/* Android AMTL
 *
 * Copyright (C) Intel 2015
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Erwan Bracq <erwan.bracq@intel.com>
 * Author: Morgane Butscher <morganex.butscher@intel.com>
 */

package com.intel.amtl.models.config;

import android.util.Log;

import java.util.ArrayList;


public class ModemLogOutput {

    private final String TAG = "AMTL";
    private final String MODULE = "ModemLogOutput";

    private int index = -1;
    private String name = null;
    private String connectionId = null;
    private String serviceToStart = null;
    private String dftFlCmd = null;
    private String atLegacyCmd = null;
    private String useMmgr = null;
    private String fullStopCmd = null;
    private String dftCfgOnStop = null;
    private String modemInterface = null;
    private LogOutput defaultConfig = null;
    private ArrayList<LogOutput> outputList = null;

    public ModemLogOutput() {
        this.defaultConfig = new LogOutput();
        this.outputList = new ArrayList<LogOutput>();
    }

    public ModemLogOutput(int index, String name, String conId, String serviceToStart, String flCmd,
            String atLegacyCmd, String useMmgr, String fullStopCmd, String dftCfgOnStop,
            String modemInterface) {
        this.setIndex(index);
        this.setName(name);
        this.setConnectionId(conId);
        this.setServiceToStart(serviceToStart);
        this.setFlCmd(flCmd);
        this.setAtLegacyCmd(atLegacyCmd);
        this.setUseMmgr(useMmgr);
        this.setFullStopCmd(fullStopCmd);
        this.setDftCfgOnStop(dftCfgOnStop);
        this.setModemInterface(modemInterface);
        this.setDefaultConfig(defaultConfig);
        this.defaultConfig = new LogOutput();
        this.outputList = new ArrayList<LogOutput>();
    }

    public void addOutputToList(LogOutput output) {
        this.outputList.add(output);
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public void setConnectionId(String id) {
        this.connectionId = (null == id) ? "1" : id;
    }

    public String getConnectionId() {
        return this.connectionId;
    }

    public void setServiceToStart(String service) {
        this.serviceToStart = (null == service) ? "mts" : service;
    }

    public String getServiceToStart() {
        return this.serviceToStart;
    }

    public void setFlCmd(String flCmd) {
        this.dftFlCmd = (null == flCmd) ? "" : flCmd;
    }

    public String getFlcmd() {
        return this.dftFlCmd;
    }

    public void setAtLegacyCmd(String cmd) {
        this.atLegacyCmd = (null == cmd) ? "false" : cmd;
    }

    public String getAtLegacyCmd() {
        return this.atLegacyCmd;
    }

    public void setUseMmgr(String useMmgr) {
        this.useMmgr = (null == useMmgr) ? "true" : useMmgr;
    }

    public String getUseMmgr() {
        return this.useMmgr;
    }

    public void setFullStopCmd(String cmd) {
        this.fullStopCmd = cmd;
    }

    public String getFullStopCmd() {
        return this.fullStopCmd;
    }

    public void setDftCfgOnStop(String cfg) {
        this.dftCfgOnStop = cfg;
    }

    public String getDftCfgOnStop() {
        return this.dftCfgOnStop;
    }

    public void setModemInterface(String modInterface) {
        this.modemInterface = modInterface;
    }

    public String getModemInterface() {
        return this.modemInterface;
    }

    public void setDefaultConfig(LogOutput defConf) {
        this.defaultConfig = defConf;
    }

    public LogOutput getDefaultConfig() {
        return this.defaultConfig;
    }

    public ArrayList<LogOutput> getOutputList() {
        return this.outputList;
    }

    public void printToLog() {
        Log.d(TAG, MODULE + ": Configuration loaded:");
        Log.d(TAG, MODULE + ": =======================================");
        Log.d(TAG, MODULE + ": index = " + this.index + ", name = " + this.name
                + ", default flush command = " + this.dftFlCmd + ".");
        for (LogOutput o: outputList) {
            o.printToLog();
            Log.d(TAG, MODULE + ": ---------------------------------------");
        }
        Log.d(TAG, MODULE + ": =======================================");
    }
}
