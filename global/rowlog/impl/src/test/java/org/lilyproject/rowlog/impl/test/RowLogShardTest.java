/*
 * Copyright 2010 Outerthought bvba
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
package org.lilyproject.rowlog.impl.test;


import static java.util.Arrays.asList;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.classextension.EasyMock.createControl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.util.Bytes;
import org.easymock.classextension.IMocksControl;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.lilyproject.rowlog.api.RowLog;
import org.lilyproject.rowlog.api.RowLogMessage;
import org.lilyproject.rowlog.api.RowLogSubscription;
import org.lilyproject.rowlog.api.RowLogSubscription.Type;
import org.lilyproject.rowlog.impl.RowLogMessageImpl;
import org.lilyproject.rowlog.impl.RowLogShardImpl;
import org.lilyproject.hadooptestfw.HBaseProxy;
import org.lilyproject.hadooptestfw.TestHelper;
import org.lilyproject.util.hbase.HBaseTableFactoryImpl;

public class RowLogShardTest {

    private static HBaseProxy HBASE_PROXY;
    private static RowLogShardImpl shard;
    private static IMocksControl control;
    private static RowLog rowLog;
    private static int batchSize = 5;
    
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        TestHelper.setupLogging();
        HBASE_PROXY = new HBaseProxy();
        HBASE_PROXY.start();
        control = createControl();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        HBASE_PROXY.stop();
    }



    @Before
    public void setUp() throws Exception {
    	rowLog = control.createMock(RowLog.class);
    	rowLog.getId();
    	expectLastCall().andReturn("rowLogId").anyTimes();
    }

    @After
    public void tearDown() throws Exception {
        control.reset();
    }

    private HTableInterface createRowLogTable() throws IOException {
        String tableName = "rowlog";
        HTableDescriptor tableDescriptor = new HTableDescriptor(tableName);
        tableDescriptor.addFamily(new HColumnDescriptor(RowLogShardImpl.MESSAGES_CF));
        HTableInterface table = new HBaseTableFactoryImpl(HBASE_PROXY.getConf()).getTable(tableDescriptor);
        return table;
    }
    
    @Test
    public void testSingleMessage() throws Exception {
        String subscriptionId = "Subscription1";
        rowLog.getSubscriptions();
        expectLastCall().andReturn(asList(new RowLogSubscription("id", subscriptionId, Type.VM, 1))).anyTimes();
        control.replay();
        shard = new RowLogShardImpl("TestShard", new byte[0], createRowLogTable(), rowLog, batchSize);
        RowLogMessageImpl message1 = new RowLogMessageImpl(System.currentTimeMillis(), Bytes.toBytes("row1"), 0L, null, rowLog);
        shard.putMessage(message1); 
        
        List<RowLogMessage> messages = shard.next(subscriptionId, batchSize);
        assertEquals(1, messages.size());
        assertEquals(message1, messages.get(0));
        
        shard.removeMessage(message1, subscriptionId);
        assertTrue(shard.next(subscriptionId, batchSize).isEmpty());
        control.verify();
    }
    
    @Test
    public void testMultipleMessages() throws Exception {
        String subscriptionId = "Subscription1";
        rowLog.getSubscriptions();
        expectLastCall().andReturn(asList(new RowLogSubscription("id", subscriptionId, Type.VM, 1))).anyTimes();
        
        control.replay();
        shard = new RowLogShardImpl("TestShard", new byte[0], createRowLogTable(), rowLog, batchSize);
        long timestamp1 = System.currentTimeMillis();
        RowLogMessageImpl message1 = new RowLogMessageImpl(timestamp1, Bytes.toBytes("row1"), 0L, null, rowLog);
        long timestamp2 = System.currentTimeMillis()+1;
        RowLogMessageImpl message2 = new RowLogMessageImpl(timestamp2, Bytes.toBytes("row2"), 0L, null, rowLog);

        shard.putMessage(message1);
        shard.putMessage(message2);

        List<RowLogMessage> messages = shard.next(subscriptionId, batchSize);
        assertEquals(2, messages.size());
        assertEquals(message1, messages.get(0));

        shard.removeMessage(message1, subscriptionId);
        assertEquals(message2, messages.get(1));
        
        shard.removeMessage(message2, subscriptionId);
        assertTrue(shard.next(subscriptionId, batchSize).isEmpty());
        control.verify();
    }
    
    @Test
    public void testBatchSize() throws Exception {
        String subscriptionId = "Subscription1";
        rowLog.getSubscriptions();
        expectLastCall().andReturn(asList(new RowLogSubscription("id", subscriptionId, Type.VM, 1))).anyTimes();
        
        RowLogMessage[] expectedMessages = new RowLogMessage[7];
        control.replay();
        shard = new RowLogShardImpl("TestShard", new byte[0], createRowLogTable(), rowLog, batchSize);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 7; i++) {
            RowLogMessageImpl message = new RowLogMessageImpl(now + i, Bytes.toBytes("row1"), 0L, null, rowLog);
            expectedMessages[i] = message;
            shard.putMessage(message);
        }

        List<RowLogMessage> messages = shard.next(subscriptionId, batchSize);
        assertEquals(batchSize , messages.size());

        int i = 0;
        for (RowLogMessage message : messages) {
            assertEquals(expectedMessages[i++], message);
            shard.removeMessage(message, subscriptionId);
        }
        messages = shard.next(subscriptionId, batchSize);
        assertEquals(2, messages.size());
        for (RowLogMessage message : messages) {
            assertEquals(expectedMessages[i++], message);
            shard.removeMessage(message, subscriptionId);
        }
        
        assertTrue(shard.next(subscriptionId, batchSize).isEmpty());
        control.verify();
    }
    
    
    @Test
    public void testMultipleConsumers() throws Exception {
        String subscriptionId1 = "Subscription1";
        String subscriptionId2 = "Subscription2";
        rowLog.getSubscriptions();
        expectLastCall().andReturn(asList(
                new RowLogSubscription("id", subscriptionId1, Type.VM, 1),
                new RowLogSubscription("id", subscriptionId2, Type.VM, 2))).anyTimes();
        
        control.replay();
        shard = new RowLogShardImpl("TestShard", new byte[0], createRowLogTable(), rowLog, batchSize);
        long timestamp1 = System.currentTimeMillis();
        RowLogMessageImpl message1 = new RowLogMessageImpl(timestamp1, Bytes.toBytes("row1"), 1L, null, rowLog);
        long timestamp2 = timestamp1 + 1;
        RowLogMessageImpl message2 = new RowLogMessageImpl(timestamp2, Bytes.toBytes("row2"), 1L, null, rowLog);
        
        shard.putMessage(message1);
        shard.putMessage(message2);
        List<RowLogMessage> messages = shard.next(subscriptionId1, batchSize);
        assertEquals(2, messages.size());
        assertEquals(message1, messages.get(0));
        shard.removeMessage(message1, subscriptionId1);
        assertEquals(message2, messages.get(1));
        shard.removeMessage(message2, subscriptionId1);
        
        messages = shard.next(subscriptionId2, batchSize);
        assertEquals(2, messages.size());
        assertEquals(message1, messages.get(0));
        shard.removeMessage(message1, subscriptionId2);
        assertEquals(message2, messages.get(1));
        shard.removeMessage(message2, subscriptionId2);
        
        assertTrue(shard.next(subscriptionId1, batchSize).isEmpty());
        assertTrue(shard.next(subscriptionId2, batchSize).isEmpty());
        control.verify();
    }
    
    @Test
    public void testMessageDoesNotExistForConsumer() throws Exception {
        String subscriptionId1 = "Subscription1";
        String subscriptionId2 = "Subscription2";
        rowLog.getSubscriptions();
        expectLastCall().andReturn(asList(new RowLogSubscription("id", subscriptionId1, Type.VM, 1))).anyTimes();
        
        control.replay();
        shard = new RowLogShardImpl("TestShard", new byte[0], createRowLogTable(), rowLog, batchSize);
        long timestamp1 = System.currentTimeMillis();
        RowLogMessageImpl message1 = new RowLogMessageImpl(timestamp1, Bytes.toBytes("row1"), 1L, null, rowLog);

        shard.putMessage(message1);
        assertTrue(shard.next(subscriptionId2, batchSize).isEmpty());

        shard.removeMessage(message1, subscriptionId2);
        List<RowLogMessage> messages = shard.next(subscriptionId1, batchSize);
        assertEquals(message1, messages.get(0));
        // Cleanup
        shard.removeMessage(message1, subscriptionId1);
        assertTrue(shard.next(subscriptionId1, batchSize).isEmpty());
        control.verify();
    }
}
