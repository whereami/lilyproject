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
package org.lilyproject.rowlog.admin.cli;

import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.lilyproject.cli.BaseZkCliTool;
import org.lilyproject.rowlog.api.RowLogSubscription;
import org.lilyproject.rowlog.impl.RowLogConfigurationManagerImpl;
import org.lilyproject.util.Version;
import org.lilyproject.util.io.Closer;
import org.lilyproject.util.zookeeper.StateWatchingZooKeeper;
import org.lilyproject.util.zookeeper.ZooKeeperItf;

public abstract class BaseRowLogAdminCli extends BaseZkCliTool {
    
    protected Option rowLogIdOption;
    protected Option respectOrderOption;
    protected Option notifyEnabledOption;
    protected Option notifyDelayOption;
    protected Option minimalProcessDelayOption;
    protected Option wakeupTimeoutOption;
    protected Option subscriptionIdOption;
    protected Option subscriptionTypeOption;
    protected Option subscriptionOrderNrOption;
    
    protected String rowLogId = null;
    protected String subscriptionId = null;
    protected Boolean respectOrder = null;
    protected Boolean notifyEnabled = null;
    protected Long notifyDelay = null;
    protected Long minimalProcessDelay = null;
    protected Long wakeupTimeout = null;
    protected RowLogSubscription.Type type = null;
    protected Integer orderNr = null;
    
    protected RowLogConfigurationManagerImpl rowLogConfigurationManager;
    protected ZooKeeperItf zk;

    public BaseRowLogAdminCli() {
        // Here we instantiate various options, but it is up to subclasses to decide which ones
        // they acutally want to use (see getOptions() method).

        rowLogIdOption = OptionBuilder
                .withArgName("rowlog")
                .hasArg()
                .withDescription("RowLog id")
                .withLongOpt("rowlog-id")
                .create("r");
        
        subscriptionIdOption = OptionBuilder
                .withArgName("subscription")
                .hasArg()
                .withDescription("Subscription id")
                .withLongOpt("subscription-id")
                .create("s");
        
        respectOrderOption = OptionBuilder
                .withArgName("order")
                .hasArg()
                .withDescription("Respect order ('true' or 'false')")
                .withLongOpt("respect-order")
                .create("o");
        
        notifyEnabledOption = OptionBuilder
                .withArgName("notify-enabled")
                .hasArg()
                .withDescription("Notify enabled ('true' or 'false')")
                .withLongOpt("notify-enabled")
                .create("e");

        notifyDelayOption = OptionBuilder
                .withArgName("notify")
                .hasArg()
                .withDescription("Notify delay (a number >= 0)")
                .withLongOpt("notify-delay")
                .create("n");

        minimalProcessDelayOption = OptionBuilder
                .withArgName("process")
                .hasArg()
                .withDescription("Minimal process delay (a number >= 0)")
                .withLongOpt("minimal-process-delay")
                .create("p");

        wakeupTimeoutOption = OptionBuilder
                .withArgName("wakeup")
                .hasArg()
                .withDescription("Wakeup timeout (a number >= 0)")
                .withLongOpt("wakeup-timeout")
                .create("w");
        
        subscriptionTypeOption = OptionBuilder
                .withArgName("type")
                .hasArg()
                .withDescription("Subscription type")
                .withLongOpt("subscription-type")
                .create("t");
        
        subscriptionOrderNrOption = OptionBuilder
                .withArgName("order-nr")
                .hasArg()
                .withDescription("Subscription order number")
                .withLongOpt("subscription-order-nr")
                .create("nr");
    }
    
    @Override
    public List<Option> getOptions() {
        List<Option> options = super.getOptions();

        return options;
    }

    @Override
    protected String getVersion() {
        return Version.readVersion("org.lilyproject", "lily-rowlog-admin-cli");
    }

    @Override
    protected int processOptions(CommandLine cmd) throws Exception {
        int result = super.processOptions(cmd);
        if (result != 0)
            return result;

        if (cmd.hasOption(rowLogIdOption.getOpt())) {
            rowLogId = cmd.getOptionValue(rowLogIdOption.getOpt());
        }
        
        if (cmd.hasOption(subscriptionIdOption.getOpt())) {
            subscriptionId = cmd.getOptionValue(subscriptionIdOption.getOpt());
        }
        
        if (cmd.hasOption(respectOrderOption.getOpt())) {
            String optionValue = cmd.getOptionValue(respectOrderOption.getOpt());
            if ("true".equals(optionValue.toLowerCase()))
                respectOrder = Boolean.TRUE;
            else if ("false".equals(optionValue.toLowerCase()))
                respectOrder = Boolean.FALSE;
            else {
                System.out.println("Specified value for 'respect order' should be either 'true' or 'false'");
                return 1;
            }
        }
        
        if (cmd.hasOption(notifyEnabledOption.getOpt())) {
            String optionValue = cmd.getOptionValue(notifyEnabledOption.getOpt());
            if ("true".equals(optionValue.toLowerCase()))
                notifyEnabled = Boolean.TRUE;
            else if ("false".equals(optionValue.toLowerCase()))
                notifyEnabled = Boolean.FALSE;
            else {
                System.out.println("Specified value for 'notify enabled' should be either 'true' or 'false'");
                return 1;
            }
        }

        if (cmd.hasOption(notifyDelayOption.getOpt())) {
            try {
                notifyDelay = Long.valueOf(cmd.getOptionValue(notifyDelayOption.getOpt()));
                if (notifyDelay < 0) {
                    System.out.println("Notify delay must be a number >= 0");
                    return 1;
                }
            } catch (NumberFormatException e) {
                System.out.println("Notify delay must be a number >= 0");
                return 1;
            }
        }

        if (cmd.hasOption(minimalProcessDelayOption.getOpt())) {
            try {
                minimalProcessDelay = Long.valueOf(cmd.getOptionValue(minimalProcessDelayOption.getOpt()));
                if (minimalProcessDelay < 0) {
                    System.out.println("Minimal process delay must be a number >= 0");
                    return 1;
                }
            } catch (NumberFormatException e) {
                System.out.println("Minimal process delay must be a number >= 0");
                return 1;
            }
        }
        
        if (cmd.hasOption(wakeupTimeoutOption.getOpt())) {
            try {
                wakeupTimeout = Long.valueOf(cmd.getOptionValue(wakeupTimeoutOption.getOpt()));
                if (wakeupTimeout < 0) {
                    System.out.println("Wakeup timeout must be a number >= 0");
                    return 1;
                }
            } catch (NumberFormatException e) {
                System.out.println("Wakeup timeout must be a number >= 0");
                return 1;
            }
        }

        if (cmd.hasOption(subscriptionOrderNrOption.getOpt())) {
            try {
                orderNr= Integer.valueOf(cmd.getOptionValue(subscriptionOrderNrOption.getOpt()));
                if (orderNr < 0) {
                    System.out.println("Subscription order number must be a number >= 0");
                    return 1;
                }
            } catch (NumberFormatException e) {
                System.out.println("Subscription order number must be a number >= 0");
                return 1;
            }
        }
        
        if (cmd.hasOption(subscriptionTypeOption.getOpt())) {
            try {
                type = RowLogSubscription.Type.valueOf(cmd.getOptionValue(subscriptionTypeOption.getOpt()));
            } catch (IllegalArgumentException e) {
                System.out.println("Subscription type '" + cmd.getOptionValue(subscriptionTypeOption.getOpt()) + "' is unknown.");
                return 1;
            }
        }
        

        return 0;
    }
    
    @Override
    public int run(CommandLine cmd) throws Exception {
        int result = super.run(cmd);
        if (result != 0)
            return result;
        
        zk = new StateWatchingZooKeeper(zkConnectionString, zkSessionTimeout);
        
        rowLogConfigurationManager = new RowLogConfigurationManagerImpl(zk);
        
        return 0;
    }

    @Override
    protected void cleanup() {
        Closer.close(rowLogConfigurationManager);
        Closer.close(zk);
        super.cleanup();
    }
}
