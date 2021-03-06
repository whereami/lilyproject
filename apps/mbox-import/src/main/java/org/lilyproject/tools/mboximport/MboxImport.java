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
package org.lilyproject.tools.mboximport;

import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.codec.Base64InputStream;
import org.apache.james.mime4j.codec.QuotedPrintableInputStream;
import org.apache.james.mime4j.field.*;
import org.apache.james.mime4j.field.address.Address;
import org.apache.james.mime4j.field.address.AddressList;
import org.apache.james.mime4j.field.address.Mailbox;
import org.apache.james.mime4j.field.address.MailboxList;
import org.apache.james.mime4j.io.EOLConvertingInputStream;
import org.apache.james.mime4j.parser.Field;
import org.apache.james.mime4j.parser.MimeEntityConfig;
import org.apache.james.mime4j.parser.MimeTokenStream;
import org.apache.james.mime4j.util.MimeUtil;
import org.lilyproject.repository.api.*;
import org.lilyproject.testclientfw.BaseRepositoryTestTool;
import org.lilyproject.tools.import_.cli.JsonImport;
import org.lilyproject.util.Version;
import org.lilyproject.util.io.Closer;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class MboxImport extends BaseRepositoryTestTool {

    private Option fileOption;

    private Option schemaOption;

    private Map<String, Integer> partsByMediaType = new HashMap<String, Integer>();

    private static final String NS = "org.lilyproject.mail";

    private static final int MAX_LINE_LENGTH = 10000;

    @Override
    protected String getCmdName() {
        return "lily-mbox-import";
    }

    @Override
    protected String getVersion() {
        return Version.readVersion("org.lilyproject", "lily-mbox-import");
    }

    public static void main(String[] args) throws Exception {
        new MboxImport().start(args);
    }

    @Override
    public List<Option> getOptions() {
        List<Option> options = super.getOptions();

        fileOption = OptionBuilder
                .withArgName("file")
                .hasArg()
                .withDescription("File or directory name")
                .withLongOpt("file")
                .create("f");
        options.add(fileOption);

        schemaOption = OptionBuilder
                .withDescription("Create/update the schema")
                .withLongOpt("schema")
                .create("s");
        options.add(schemaOption);

        return options;
    }

    @Override
    protected int getDefaultWorkers() {
        return 1;
    }

    @Override
    public int run(CommandLine cmd) throws Exception {
        int result = super.run(cmd);
        if (result != 0)
            return result;

        if (!cmd.hasOption(schemaOption.getOpt()) && !cmd.hasOption(fileOption.getOpt())) {
            printHelp();
            return 1;
        }

        setupLily();

        if (cmd.hasOption(schemaOption.getOpt()) || cmd.hasOption(fileOption.getOpt())) {
            loadSchema();
        }

        if (cmd.hasOption(fileOption.getOpt())) {
            String fileName = cmd.getOptionValue(fileOption.getOpt());
            File file = new File(fileName);

            if (!file.exists()) {
                System.out.println("File does not exist: " + file.getAbsolutePath());
                return 1;
            }

            setupMetrics();

            startExecutor();

            if (file.isDirectory()) {
                File[] files = file.listFiles();
                Arrays.sort(files);
                for (File item : files) {
                    if (!item.isDirectory()) {
                        executor.submit(new ImportMboxFileTask(item));
                    }
                }
            } else {
                executor.submit(new ImportMboxFileTask(file));
            }

            stopExecutor();

            finishMetrics();

            System.out.println();
            System.out.println("Number of created parts per media type:");
            for (Map.Entry<String, Integer> entry : partsByMediaType.entrySet()) {
                System.out.println("  " + entry.getKey() + " : " + entry.getValue());
            }
            System.out.println();
        }

        lilyClient.close();

        return 0;
    }

    private void loadSchema() throws Exception {
        System.out.println("Creating the schema (if necessary)");
        System.out.println();
        InputStream is = getClass().getClassLoader().getResourceAsStream("org/lilyproject/tools/mboximport/mail_schema.json");
        JsonImport.load(repository, is, false);
        System.out.println();
    }

    private class ImportMboxFileTask implements Runnable {
        private File file;

        private ImportMboxFileTask(File file) {
            this.file = file;
        }

        @Override
        public void run() {
            try {
                importFile(file);
            } catch (Throwable t) {
                t.printStackTrace();
                metrics.increment("Exceptions", 1);
            }
        }
    }

    private void importFile(File file) throws Exception {
        System.out.println("Processing file " + file.getAbsolutePath());
        InputStream is = null;
        try {
            is = new FileInputStream(file);

            if (file.getName().endsWith(".gz")) {
                is = new GZIPInputStream(is);
            }

            MboxInputStream mboxStream = new MboxInputStream(is, MAX_LINE_LENGTH);

            while (mboxStream.nextMessage()) {
                MimeTokenStream stream = new MyMimeTokenStream();
                stream.parse(mboxStream);
                importMessage(stream);
            }

        } finally {
            Closer.close(is);
        }
        System.out.println();
    }

    public static class MyMimeTokenStream extends MimeTokenStream {
        protected MyMimeTokenStream() {
            super(getConfig());
        }

        private static MimeEntityConfig getConfig() {
            MimeEntityConfig config = new MimeEntityConfig();
            config.setMaxLineLen(MAX_LINE_LENGTH);
            return config;
        }
    }

    private void importMessage(MimeTokenStream stream) throws Exception {
        int multiPartNesting = 0; // note that a multipart can again contain a multipart

        Message message = new Message();

        for (int state = stream.getState();
             state != MimeTokenStream.T_END_OF_STREAM;
             state = stream.next()) {

            switch (state) {
                case MimeTokenStream.T_BODY:
                    String mediaType = stream.getBodyDescriptor().getMimeType() + "; charset=" + stream.getBodyDescriptor().getCharset();

                    // oftwewel: gebruik getDecodedInputStream
                    InputStream bodyDataStream;
                    if (MimeUtil.isQuotedPrintableEncoded(stream.getBodyDescriptor().getTransferEncoding())) {
                        bodyDataStream = new QuotedPrintableInputStream(new EOLConvertingInputStream(stream.getInputStream(), EOLConvertingInputStream.CONVERT_LF));
                    } else if (MimeUtil.isBase64Encoding(stream.getBodyDescriptor().getTransferEncoding())) {
                        bodyDataStream = new Base64InputStream(stream.getInputStream());
                    } else {
                        bodyDataStream = stream.getInputStream();
                    }

                    byte[] data = IOUtils.toByteArray(bodyDataStream);

                    // TODO could fill in filename
                    long startTime = System.nanoTime();
                    Blob blob = new Blob(mediaType, (long)data.length, null);
                    OutputStream os = repository.getOutputStream(blob);
                    try {
                        IOUtils.write(data, os);
                    } finally {
                        os.close();
                    }
                    double duration = System.nanoTime() - startTime;
                    metrics.increment("Blob creation", "Blob", duration / 1e6d);

                    Part part = message.addPart(blob);
                    part.baseMediaType = stream.getBodyDescriptor().getMimeType();

                    break;
                case MimeTokenStream.T_FIELD:
                    if (multiPartNesting == 0) {
                        Field field = stream.getField();
                        ParsedField parsedField = new DefaultFieldParser().parse(field.getName(), MimeUtil.unfold(field.getBody()), null);
                        if (parsedField.getParseException() != null) {
                            // TODO print error
                        } else if (parsedField.getName().equals(FieldName.TO)) {
                            message.to = ((AddressListField)parsedField).getAddressList();
                        } else if (parsedField.getName().equals(FieldName.CC)) {
                            message.cc = ((AddressListField)parsedField).getAddressList();
                        } else if (parsedField.getName().equals(FieldName.FROM)) {
                            message.from = ((MailboxListField)parsedField).getMailboxList();
                        } else if (parsedField.getName().equals(FieldName.SENDER)) {
                            message.sender = ((MailboxField)parsedField).getMailbox();
                        } else if (parsedField.getName().equals("List-Id")) {
                            message.listId = parsedField.getBody();
                        } else if (parsedField.getName().equals(FieldName.SUBJECT)) {
                            message.subject = parsedField.getBody();
                        }
                    }
                    break;
                case MimeTokenStream.T_START_MULTIPART:
                    multiPartNesting++;
                    break;
                case MimeTokenStream.T_END_MULTIPART:
                    multiPartNesting--;
            }
        }

        // Now create the records in Lily


        // Since we want to link the messages and parts bidirectionally, and for performance we want to avoid
        // having to update the message, we generate record IDs ourselves.
        // Since for the current usage typically parts are indexed with information dereferenced from messages,
        // we can save additional indexer work (update of dereferenced data) by first creating the messages
        // and then the parts.
        List<RecordId> partRecordIds = new ArrayList<RecordId>(message.parts.size());
        for (Part part : message.parts)
            partRecordIds.add(idGenerator.newRecordId());

        Record messageRecord = repository.newRecord(idGenerator.newRecordId());
        messageRecord.setRecordType(new QName(NS, "Message"));
        if (message.subject != null)
            messageRecord.setField(new QName(NS, "subject"), message.subject);
        if (message.to != null)
            messageRecord.setField(new QName(NS, "to"), message.getToAddressesAsStringList());
        if (message.cc != null)
            messageRecord.setField(new QName(NS, "cc"), message.getCcAddressesAsStringList());
        if (message.from != null)
            messageRecord.setField(new QName(NS, "from"), message.getFromAddressesAsStringList());
        if (message.sender != null)
            messageRecord.setField(new QName(NS, "sender"), message.getSenderAddressAsString());
        if (message.listId != null)
            messageRecord.setField(new QName(NS, "listId"), message.listId);

        if (messageRecord.getFields().size() == 0 || message.parts.size() == 0) {
            // Message has no useful headers, do not create it.
            metrics.increment("Invalid messages", 1);
            return;
        }

        List<Link> partLinks = new ArrayList<Link>(message.parts.size());
        for (RecordId recordId : partRecordIds) {
            partLinks.add(new Link(recordId));
        }
        messageRecord.setField(new QName(NS, "parts"), partLinks);

        long startTime = System.nanoTime();
        messageRecord = repository.createOrUpdate(messageRecord);
        double duration = System.nanoTime() - startTime;
        metrics.increment("Message record", "Create", duration / 1e6d);

        for (int i = 0; i < message.parts.size(); i++) {
            Part part = message.parts.get(i);
            Record partRecord = repository.newRecord(partRecordIds.get(i));
            partRecord.setRecordType(new QName(NS, "Part"));
            partRecord.setField(new QName(NS, "mediaType"), part.blob.getMediaType());
            partRecord.setField(new QName(NS, "content"), part.blob);
            partRecord.setField(new QName(NS, "message"), new Link(messageRecord.getId()));

            startTime = System.nanoTime();
            partRecord = repository.createOrUpdate(partRecord);
            duration = System.nanoTime() - startTime;
            metrics.increment("Part record", "Create", duration / 1e6d);

            part.recordId = partRecord.getId();
            increment(part.baseMediaType);

            if (verbose) {
                System.out.println("Created part record: " + partRecord.getId());
            }
        }

        if (verbose) {
            System.out.println("Created message record " + messageRecord.getId());
        }
    }

    public void increment(String mediaType) {
        Integer count = partsByMediaType.get(mediaType);
        if (count == null) {
            partsByMediaType.put(mediaType, 1);
        } else {
            partsByMediaType.put(mediaType, count + 1);
        }
    }

    private static class Message {
        public String subject;
        public AddressList to;
        public AddressList cc;
        public MailboxList from;
        public Mailbox sender;
        public String listId;

        public List<Part> parts = new ArrayList<Part>();

        public Part addPart(Blob blob) {
            Part part = new Part();
            part.blob = blob;
            parts.add(part);
            return part;
        }

        public List<String> getToAddressesAsStringList() {
            List<String> result = new ArrayList<String>(to.size());
            for (Address address : to) {
                result.add(address.getDisplayString());
            }
            return result;
        }

        public List<String> getCcAddressesAsStringList() {
            List<String> result = new ArrayList<String>(cc.size());
            for (Address address : cc) {
                result.add(address.getDisplayString());
            }
            return result;
        }

        public List<String> getFromAddressesAsStringList() {
            List<String> result = new ArrayList<String>(from.size());
            for (Mailbox mailbox : from) {
                result.add(mailbox.getDisplayString());
            }
            return result;
        }

        public String getSenderAddressAsString() {
            return sender.getDisplayString();
        }
    }

    private static class Part {
        public Blob blob;
        public RecordId recordId;
        /** Media type without parameters. */
        public String baseMediaType;
    }
}
