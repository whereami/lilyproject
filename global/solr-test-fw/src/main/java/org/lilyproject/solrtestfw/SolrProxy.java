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
package org.lilyproject.solrtestfw;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.lilyproject.util.io.Closer;
import org.lilyproject.util.test.TestHomeUtil;
import org.lilyproject.util.xml.DocumentHelper;
import org.lilyproject.util.xml.XPathUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class SolrProxy {
    private Mode mode;

    public enum Mode { EMBED, CONNECT }
    public static String SOLR_MODE_PROP_NAME = "lily.solrproxy.mode";

    private SolrTestingUtility solrTestingUtility;
    private SolrServer solrServer;

    private MultiThreadedHttpConnectionManager connectionManager;
    private HttpClient httpClient;

    private String uri;

    private File testHome;

    private boolean clearData;

    public SolrProxy() throws IOException {
        this(null);
    }

    public SolrProxy(Mode mode) throws IOException {
        this(mode, true);
    }
    
    /**
     * Creates a new SolrProxy
     * @param mode either EMBED or CONNECT
     * @param clearData it true, clears the data directories upon shutdown
     * @throws IOException
     */
    public SolrProxy(Mode mode, boolean clearData) throws IOException {
        this.clearData = clearData;

        if (mode == null) {
          String solrModeProp = System.getProperty(SOLR_MODE_PROP_NAME);
            if (solrModeProp == null || solrModeProp.equals("") || solrModeProp.equals("embed")) {
                this.mode = Mode.EMBED;
            } else if (solrModeProp.equals("connect")) {
                this.mode = Mode.CONNECT;
            } else {
                throw new RuntimeException("Unexpected value for " + SOLR_MODE_PROP_NAME + ": " + solrModeProp);
            }
        } else {
            this.mode = mode;
        }
    }

    public void setTestHome(File testHome) throws IOException {
        if (mode != Mode.EMBED) {
            throw new RuntimeException("testHome should only be set when mode is EMBED");
        }
        this.testHome = testHome;
    }

    private void initTestHome() throws IOException {
        if (testHome == null) {
            testHome = TestHomeUtil.createTestHome("lily-solrproxy-");
        }

        FileUtils.forceMkdir(testHome);
    }

    public void start() throws Exception {
        start(null, null);
    }

    public void start(byte[] solrSchemaData, byte[] solrConfigData) throws Exception {
        System.out.println("SolrProxy mode: " + mode);

        switch (mode) {
            case EMBED:
                initTestHome();
                System.out.println("SolrProxy embedded mode temp dir: " + testHome.getAbsolutePath());
                solrTestingUtility = new SolrTestingUtility(testHome, clearData);
                if (solrSchemaData != null) {
                    solrTestingUtility.setSchemaData(solrSchemaData);
                }
                if (solrConfigData != null) {
                    solrTestingUtility.setSolrConfigData(solrConfigData);
                }
                solrTestingUtility.start();
                this.uri = solrTestingUtility.getUri();
                solrServer = new CommonsHttpSolrServer(uri, httpClient);
                break;
            case CONNECT:
                if (solrSchemaData != null || solrConfigData != null) {
                    changeSolrSchemaAndConfig(solrSchemaData, solrConfigData);
                }
                this.uri = "http://localhost:8983/solr";
                solrServer = new CommonsHttpSolrServer(uri, httpClient);
                break;
            default:
                throw new RuntimeException("Unexpected mode: " + mode);
        }

        connectionManager = new MultiThreadedHttpConnectionManager();
        connectionManager.getParams().setDefaultMaxConnectionsPerHost(5);
        connectionManager.getParams().setMaxTotalConnections(50);
        httpClient = new HttpClient(connectionManager);
    }
    
    public void stop() throws Exception {
        Closer.close(solrTestingUtility);

        Closer.close(connectionManager);
        connectionManager = null;
        httpClient = null;

        solrServer = null;
    }

    public SolrServer getSolrServer() {
        return solrServer;
    }
    
    /**
     * Commits the solr index. 
     */
    public void commit() throws Exception {
        solrServer.commit();
    }
    
    public String getUri() {
        return uri;
    }

    public void changeSolrConfig(byte[] newConfigData) throws Exception {
        changeSolrSchemaAndConfig(null, newConfigData);
    }
    
    public void changeSolrSchema(byte[] newSchemaData) throws Exception {
        changeSolrSchemaAndConfig(newSchemaData, null);
    }

    public void changeSolrSchemaAndConfig(byte[] newSchemaData, byte[] newConfigData) throws Exception {
        boolean schemaChanged = false;
        boolean solrConfigChanged = false;
        //
        // Find out location of Solr home dir
        //
        Document doc = readCoreStatus();
        File solrHomeDir = new File(XPathUtils.evalString("/response/lst[@name='status']/lst[@name='core0']/str[@name='instanceDir']", doc));

        if (newSchemaData != null) {
            schemaChanged = updateFile(newFile(solrHomeDir, "conf", "schema.xml"), newSchemaData);
            if (!schemaChanged) {
                System.out.println("Solr schema was unchanged, not overwriting it");
            }
        }

        if (newConfigData != null) {
            solrConfigChanged = updateFile(newFile(solrHomeDir, "conf", "solrconfig.xml"), newConfigData);
            if (!solrConfigChanged) {
                System.out.println("Solr config was unchanged, not overwriting it");
            }
        }

        if (schemaChanged || solrConfigChanged) {
            reloadCore();
        } else {
            System.out.println("No changes to schema or config file. Not restarting Solr");
        }
    }

    /**
     * Ensure the target file has the specified content.
     * @param target
     * @param content
     * @return true if the content of the file was changed
     */
    private boolean updateFile(File target, byte[] content) throws IOException {
        byte[] oldContent = FileUtils.readFileToByteArray(target);
        
        boolean equal = Arrays.equals(oldContent, content);
        if (!equal) {
            FileUtils.writeByteArrayToFile(target, content);
        }
        return !equal;
    }

    private File newFile(File parentDir, String... pathElements) {
        File result = parentDir;
        for (String child: pathElements) {
            result = new File(result, child);
        }
        return result;
    }

    private Document readCoreStatus() throws IOException, SAXException, ParserConfigurationException {
        URL coreStatusURL = new URL("http://localhost:8983/solr/admin/cores?action=STATUS&core=core0");
        HttpURLConnection coreStatusConn = (HttpURLConnection)coreStatusURL.openConnection();
        coreStatusConn.connect();
        if (coreStatusConn.getResponseCode() != 200) {
            throw new RuntimeException("Fetch Solr core status: expected status 200 but got: " +
                    coreStatusConn.getResponseCode());
        }
        InputStream is = coreStatusConn.getInputStream();
        Document doc = DocumentHelper.parse(is);
        is.close();
        coreStatusConn.disconnect();
        return doc;
    }

    private void reloadCore() throws IOException {
        URL coreReloadURL = new URL("http://localhost:8983/solr/admin/cores?action=RELOAD&core=core0");
        HttpURLConnection coreReloadConn = (HttpURLConnection)coreReloadURL.openConnection();
        coreReloadConn.connect();
        int response = coreReloadConn.getResponseCode();
        coreReloadConn.disconnect();
        if (response != 200) {
            throw new RuntimeException("Core reload: expected status 200 but got: " + response);
        }
    }
}
