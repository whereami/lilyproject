/*
 * Copyright 2012 NGDATA nv
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lilyproject.mapreduce;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.HConnectionManager;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.mapreduce.TableSplit;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.hadoop.mapreduce.*;
import org.codehaus.jackson.JsonNode;
import org.lilyproject.client.LilyClient;
import org.lilyproject.repository.api.RecordScan;
import org.lilyproject.repository.api.RecordScanner;
import org.lilyproject.repository.api.Repository;
import org.lilyproject.repository.api.RepositoryException;
import org.lilyproject.tools.import_.json.RecordScanReader;
import org.lilyproject.util.exception.ExceptionUtil;
import org.lilyproject.util.hbase.LilyHBaseSchema;
import org.lilyproject.util.io.Closer;
import org.lilyproject.util.json.JsonFormat;
import org.lilyproject.util.zookeeper.ZkConnectException;
import org.lilyproject.util.zookeeper.ZkUtil;
import org.lilyproject.util.zookeeper.ZooKeeperItf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A MapReduce InputFormat for Lily based on Lily scanners.
 */
public class LilyScanInputFormat extends InputFormat<RecordIdWritable, RecordWritable> implements Configurable {
    
    public static final String SCAN = "lily.mapreduce.scan";

    final Log log = LogFactory.getLog(LilyScanInputFormat.class);
    
    private Configuration conf;
    private String zkConnectString;

    @Override
    public void setConf(Configuration conf) {
        this.conf = conf;
        zkConnectString = conf.get(LilyMapReduceUtil.ZK_CONNECT_STRING);
        if (zkConnectString == null) {
            log.warn("ZooKeeper connection string not specified, will use 'localhost'.");
            zkConnectString = "localhost";
        }
    }

    @Override
    public Configuration getConf() {
        return conf;
    }

    @Override
    public List<InputSplit> getSplits(JobContext jobContext) throws IOException, InterruptedException {
        HTable table = null;
        ZooKeeperItf zk = null;
        LilyClient lilyClient = null;
        Configuration hbaseConf = null;
        try {
            zk = ZkUtil.connect(zkConnectString, 30000);

            // Need connection to Lily to parse RecordScan (a bit lame)
            lilyClient = null;
            try {
                lilyClient = new LilyClient(zk);
            } catch (Exception e) {
                throw new IOException("Error setting up LilyClient", e);
            }
            RecordScan scan = getScan(lilyClient.getRepository());

            // Determine start and stop row
            byte[] startRow;
            if (scan.getRawStartRecordId() != null) {
                startRow = scan.getRawStartRecordId();
            } else if (scan.getStartRecordId() != null) {
                startRow = scan.getStartRecordId().toBytes();
            } else {
                startRow = new byte[0];
            }
            
            byte[] stopRow;
            if (scan.getRawStopRecordId() != null) {
                stopRow = scan.getRawStopRecordId();
            } else if (scan.getStopRecordId() != null) {
                stopRow = scan.getStopRecordId().toBytes();
            } else {
                stopRow = new byte[0];
            }

            //
            hbaseConf = LilyClient.getHBaseConfiguration(zk);
            table = new HTable(hbaseConf, LilyHBaseSchema.Table.RECORD.bytes);

            return getSplits(table, startRow, stopRow);
        } catch (ZkConnectException e) {
            throw new IOException("Error setting up splits", e);
        } finally {
            Closer.close(table);
            Closer.close(zk);
            if (hbaseConf != null) {
                HConnectionManager.deleteConnection(hbaseConf, true);
            }
            Closer.close(lilyClient);
        }
    }

    /**
     * License note: this code was copied from HBase's TableInputFormat.
     *
     * @param startRow start row of the scan
     * @param stopRow stop row of the scan
     */
    public List<InputSplit> getSplits(HTable table, final byte[] startRow, final byte[] stopRow) throws IOException {
        if (table == null) {
            throw new IOException("No table was provided.");
        }
        Pair<byte[][], byte[][]> keys = table.getStartEndKeys();
        if (keys == null || keys.getFirst() == null ||
                keys.getFirst().length == 0) {
            throw new IOException("Expecting at least one region.");
        }
        int count = 0;
        List<InputSplit> splits = new ArrayList<InputSplit>(keys.getFirst().length);
        for (int i = 0; i < keys.getFirst().length; i++) {
            if ( !includeRegionInSplit(keys.getFirst()[i], keys.getSecond()[i])) {
                continue;
            }
            String regionLocation = table.getRegionLocation(keys.getFirst()[i]).
                    getServerAddress().getHostname();
            // determine if the given start an stop key fall into the region
            if ((startRow.length == 0 || keys.getSecond()[i].length == 0 ||
                    Bytes.compareTo(startRow, keys.getSecond()[i]) < 0) &&
                    (stopRow.length == 0 ||
                            Bytes.compareTo(stopRow, keys.getFirst()[i]) > 0)) {
                byte[] splitStart = startRow.length == 0 ||
                        Bytes.compareTo(keys.getFirst()[i], startRow) >= 0 ?
                        keys.getFirst()[i] : startRow;
                byte[] splitStop = (stopRow.length == 0 ||
                        Bytes.compareTo(keys.getSecond()[i], stopRow) <= 0) &&
                        keys.getSecond()[i].length > 0 ?
                        keys.getSecond()[i] : stopRow;
                InputSplit split = new TableSplit(table.getTableName(),
                        splitStart, splitStop, regionLocation);
                splits.add(split);
                if (log.isDebugEnabled())
                    log.debug("getSplits: split -> " + (count++) + " -> " + split);
            }
        }
        return splits;
    }

    protected boolean includeRegionInSplit(final byte[] startKey, final byte [] endKey) {
        return true;
    }

    @Override
    public RecordReader<RecordIdWritable, RecordWritable> createRecordReader(InputSplit inputSplit,
            TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException {

        LilyClient lilyClient = null;
        try {
            lilyClient = new LilyClient(zkConnectString, 30000);
        } catch (Exception e) {
            throw new IOException("Error setting up LilyClient", e);
        }

        // Build RecordScan
        RecordScan scan = getScan(lilyClient.getRepository());

        // Change the start/stop record IDs on the scan to the current split
        TableSplit split = (TableSplit)inputSplit;
        scan.setRawStartRecordId(split.getStartRow());
        scan.setRawStopRecordId(split.getEndRow());

        RecordScanner scanner = null;
        try {
            scanner = lilyClient.getRepository().getScanner(scan);
        } catch (RepositoryException e) {
            throw new IOException("Error setting up RecordScanner", e);
        }

        return new LilyScanRecordReader(lilyClient, scanner);
    }
    
    private RecordScan getScan(Repository repository) {
        RecordScan scan;
        String scanData = conf.get(SCAN);
        if (scanData != null) {
            try {
                JsonNode node = JsonFormat.deserializeNonStd(scanData);
                scan = RecordScanReader.INSTANCE.fromJson(node, repository);
            } catch (Exception e) {
                ExceptionUtil.handleInterrupt(e);
                throw new RuntimeException(e);
            }
        } else {
            scan = new RecordScan();
        }
        return scan;
    }
}
