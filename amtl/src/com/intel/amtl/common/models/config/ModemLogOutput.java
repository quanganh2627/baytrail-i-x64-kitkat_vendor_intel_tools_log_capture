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

package com.intel.amtl.common.models.config;

import android.util.Log;

import com.intel.amtl.common.log.AlogMarker;
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
        AlogMarker.tAB("ModemLogOutput.ModemLogOutput", "0");
        this.defaultConfig = new LogOutput();
        this.outputList = new ArrayList<LogOutput>();
        AlogMarker.tAE("ModemLogOutput.ModemLogOutput", "0");
    }

    public ModemLogOutput(int index, String name, String conId, String serviceToStart, String flCmd,
            String atLegacyCmd, String useMmgr, String fullStopCmd, String dftCfgOnStop,
            String modemInterface) {
        AlogMarker.tAB("ModemLogOutput.ModemLogOutput", "0");
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
        AlogMarker.tAE("ModemLogOutput.ModemLogOutput", "0");
    }

    public void addOutputToList(LogOutput output) {
        AlogMarker.tAB("ModemLogOutput.addOutputToList", "0");
        this.outputList.add(output);
        AlogMarker.tAE("ModemLogOutput.addOutputToList", "0");
    }

    public void setIndex(int index) {
        AlogMarker.tAB("ModemLogOutput.setIndex", "0");
        this.index = index;
        AlogMarker.tAE("ModemLogOutput.setIndex", "0");
    }

    public void setName(String name) {
        AlogMarker.tAB("ModemLogOutput.setName", "0");
        this.name = name;
        AlogMarker.tAE("ModemLogOutput.setName", "0");
    }

    public String getName() {
        AlogMarker.tAB("ModemLogOutput.getName", "0");
        AlogMarker.tAE("ModemLogOutput.getName", "0");
        return this.name;
    }

    public void setConnectionId(String id) {
        AlogMarker.tAB("ModemLogOutput.setConnectionId", "0");
        this.connectionId = (null == id) ? "1" : id;
        AlogMarker.tAE("ModemLogOutput.setConnectionId", "0");
    }

    public String getConnectionId() {
        AlogMarker.tAB("ModemLogOutput.getConnectionId", "0");
        AlogMarker.tAE("ModemLogOutput.getConnectionId", "0");
        return this.connectionId;
    }

    public void setServiceToStart(String service) {
        AlogMarker.tAB("ModemLogOutput.setServiceToStart", "0");
        this.serviceToStart = (null == service) ? "mts" : service;
        AlogMarker.tAE("ModemLogOutput.setServiceToStart", "0");
    }

    public String getServiceToStart() {
        AlogMarker.tAB("ModemLogOutput.getServiceToStart", "0");
        AlogMarker.tAE("ModemLogOutput.getServiceToStart", "0");
        return this.serviceToStart;
    }

    public void setFlCmd(String flCmd) {
        AlogMarker.tAB("ModemLogOutput.setFlCmd", "0");
        AlogMarker.tAE("ModemLogOutput.setFlCmd", "0");
        this.dftFlCmd = (null == flCmd) ? "" : flCmd;
    }

    public String getFlcmd() {
        AlogMarker.tAB("ModemLogOutput.getFlCmd", "0");
        AlogMarker.tAE("ModemLogOutput.getFlCmd", "0");
        return this.dftFlCmd;
    }

    public void setAtLegacyCmd(String cmd) {
        AlogMarker.tAB("ModemLogOutput.setAtLegacyCmd", "0");
        this.atLegacyCmd = (null == cmd) ? "false" : cmd;
        AlogMarker.tAE("ModemLogOutput.setAtLegacyCmd", "0");
    }

    public String getAtLegacyCmd() {
        AlogMarker.tAB("ModemLogOutput.getAtLegacyCmd", "0");
        AlogMarker.tAE("ModemLogOutput.getAtLegacyCmd", "0");
        return this.atLegacyCmd;
    }

    public void setUseMmgr(String useMmgr) {
        AlogMarker.tAB("ModemLogOutput.setUseMmgr", "0");
        this.useMmgr = (null == useMmgr) ? "false" : useMmgr;
        AlogMarker.tAE("ModemLogOutput.setUseMmgr", "0");
    }

    public String getUseMmgr() {
        AlogMarker.tAB("ModemLogOutput.getUseMmgr", "0");
        AlogMarker.tAE("ModemLogOutput.getUseMmgr", "0");
        return this.useMmgr;
    }

    public void setFullStopCmd(String cmd) {
        AlogMarker.tAB("ModemLogOutput.setFullstopCmd", "0");
        this.fullStopCmd = cmd;
        AlogMarker.tAE("ModemLogOutput.setFullstopCmd", "0");
    }

    public String getFullStopCmd() {
        AlogMarker.tAB("ModemLogOutput.getFullstopCmd", "0");
        AlogMarker.tAE("ModemLogOutput.getFullstopCmd", "0");
        return this.fullStopCmd;
    }

    public void setDftCfgOnStop(String cfg) {
        AlogMarker.tAB("ModemLogOutput.setDftCfgOnStop", "0");
        this.dftCfgOnStop = cfg;
        AlogMarker.tAE("ModemLogOutput.setDftCfgOnStop", "0");
    }

    public String getDftCfgOnStop() {
        AlogMarker.tAB("ModemLogOutput.getDftCfgOnStop", "0");
        AlogMarker.tAE("ModemLogOutput.getDftCfgOnStop", "0");
        return this.dftCfgOnStop;
    }

    public void setModemInterface(String modInterface) {
        AlogMarker.tAB("ModemLogOutput.setModemInterface", "0");
        this.modemInterface = modInterface;
        AlogMarker.tAE("ModemLogOutput.setModemInterface", "0");
    }

    public String getModemInterface() {
        AlogMarker.tAB("ModemLogOutput.getModemInterface", "0");
        AlogMarker.tAE("ModemLogOutput.getModemInterface", "0");
        return this.modemInterface;
    }

    public void setDefaultConfig(LogOutput defConf) {
        AlogMarker.tAB("ModemLogOutput.setDefaultConfig", "0");
        this.defaultConfig = defConf;
        AlogMarker.tAE("ModemLogOutput.setDefaultConfig", "0");
    }

    public LogOutput getDefaultConfig() {
        AlogMarker.tAB("ModemLogOutput.getDefaultConfig", "0");
        AlogMarker.tAE("ModemLogOutput.getDefaultConfig", "0");
        return this.defaultConfig;
    }

    public ArrayList<LogOutput> getOutputList() {
        AlogMarker.tAB("ModemLogOutput.getOutputList", "0");
        AlogMarker.tAE("ModemLogOutput.getOutputList", "0");
        return this.outputList;
    }

    public void printToLog() {
        AlogMarker.tAB("ModemLogOutput.printToLog", "0");
        Log.d(TAG, MODULE + ": Configuration loaded:");
        Log.d(TAG, MODULE + ": =======================================");
        Log.d(TAG, MODULE + ": index = " + this.index + ", name = " + this.name
                + ", connection id = " + this.connectionId
                + ", service to start = " + this.serviceToStart
                + ", default flush command = " + this.dftFlCmd
                + ", use mmgr = " + this.useMmgr
                + ", modem interface = " + this.modemInterface+ ".");
        for (LogOutput o: outputList) {
            o.printToLog();
            Log.d(TAG, MODULE + ": ---------------------------------------");
        }
        Log.d(TAG, MODULE + ": =======================================");
        AlogMarker.tAE("ModemLogOutput.printToLog", "0");
    }
}
