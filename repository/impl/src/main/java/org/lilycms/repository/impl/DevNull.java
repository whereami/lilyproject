package org.lilycms.repository.impl;

import org.apache.hadoop.hbase.client.RowLock;
import org.lilycms.rowlog.api.RowLogMessage;
import org.lilycms.rowlog.api.RowLogMessageConsumer;

public class DevNull implements RowLogMessageConsumer {

	public static final int ID = 2;
	
	public DevNull() {
    }
	
	public int getId() {
		return ID;
	}
	
	public boolean processMessage(RowLogMessage message, RowLock rowLock) {
		return true;
    }
}
