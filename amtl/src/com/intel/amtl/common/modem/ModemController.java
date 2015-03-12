/* Android Modem Traces and Logs
 *
 * Copyright (C) Intel 2013
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
 * Author: Morgane Butscher <morganeX.butscher@intel.com>
 */

package com.intel.amtl.common.modem;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import com.intel.amtl.common.AMTLApplication;
import com.intel.amtl.common.exceptions.ModemControlException;
import com.intel.amtl.common.log.AlogMarker;
import com.intel.amtl.common.models.ConfigManager;
import com.intel.amtl.common.models.config.Master;
import com.intel.amtl.common.models.config.ModemConf;
import com.intel.amtl.mmgr.models.ATConfigManager;
import com.intel.amtl.mmgr.modem.ATParser;
import com.intel.amtl.mmgr.modem.MMGRModemInterfaceMgr;
import com.intel.amtl.modem.MMGController;
import com.intel.amtl.modem.MSMController;
import com.intel.amtl.modem.StubController;
import com.intel.amtl.msm.models.SofiaConfigManager;
import com.intel.amtl.msm.modem.SofiaModemInterfaceMgr;
import com.intel.amtl.msm.modem.SofiaParser;
import com.intel.amtl.R;

import java.util.ArrayList;

public abstract class ModemController {

    private final String TAG = "AMTL";
    private final String MODULE = "ModemController";

    private static String currentModemStatus = "NONE";
    protected static ModemInterfaceMgr modIfMgr;
    private CommandParser cmdParser;
    private ConfigManager confManager;
    private static ModemController mdmCtrl;

    public abstract boolean isModemUp();
    public abstract String getModemStatus();
    public abstract void restartModem() throws ModemControlException;
    public abstract String sendCommand(String command) throws ModemControlException;
    public abstract void connectToModem() throws ModemControlException;
    public abstract boolean queryTraceState() throws ModemControlException;
    public abstract String switchOffTrace()throws ModemControlException;
    public abstract void switchTrace(ModemConf mdmconf)throws ModemControlException;
    public abstract String checkAtTraceState() throws ModemControlException;
    public abstract ModemConf getNoLoggingConf();
    public abstract void cleanBeforeExit();
    public abstract void acquireResource() throws ModemControlException;
    public abstract void releaseResource();
    public abstract boolean isModemAcquired();
    public abstract String flush(ModemConf mdmConf) throws ModemControlException;

    public abstract String confTraceAndModemInfo(ModemConf mdmConf) throws ModemControlException;
    public abstract String checkAtXsioState() throws ModemControlException;
    public abstract String checkAtXsystraceState() throws ModemControlException;
    public abstract ArrayList<Master> checkAtXsystraceState(ArrayList<Master> masterList)
            throws ModemControlException;
    public abstract String checkOct() throws ModemControlException;
    public abstract String checkProfileName() throws ModemControlException;
    public abstract String generateModemCoreDump() throws ModemControlException;

    public ModemController() {
        AlogMarker.tAB("ModemController.ModemController", "0");
        cmdParser = (AMTLApplication.getModemInfo().equals("mmgr")) ? new ATParser()
                : new SofiaParser();
        confManager = (AMTLApplication.getModemInfo().equals("mmgr")) ? new ATConfigManager()
                : new SofiaConfigManager();
        AlogMarker.tAE("ModemController.ModemController", "0");
    }

    public static synchronized ModemController getInstance() throws ModemControlException {
        AlogMarker.tAB("ModemController.getInstance", "0");
        if (AMTLApplication.getModemInfo().equals("mmgr")) {
            mdmCtrl = MMGController.get();
        } else if (AMTLApplication.getModemInfo().equals("msm")) {
            mdmCtrl = MSMController.get();
        } else {
            mdmCtrl = StubController.get();
        }
        AlogMarker.tAE("ModemController.getInstance", "0");
        return mdmCtrl;
    }

    public CommandParser getCmdParser() {
        AlogMarker.tAB("ModemController.getCmdParser", "0");
        AlogMarker.tAE("ModemController.getCmdParser", "0");
        return cmdParser;
    }

    public ConfigManager getConfigManager() {
        AlogMarker.tAB("ModemController.getConfigManager", "0");
        AlogMarker.tAE("ModemController.getConfigManager", "0");
        return confManager;
    }

    public String getCurrentModemName() {
        AlogMarker.tAB("ModemController.getCurrentModemName", "0");
        ArrayList<String> modemNames = new ArrayList<String>();
        modemNames = AMTLApplication.getModemNameList();
        String curModemIndex = PreferenceManager
                .getDefaultSharedPreferences(AMTLApplication.getContext())
                .getString(AMTLApplication.getContext()
                .getString(R.string.settings_modem_name_key), "0");
        int readModemIndex = Integer.parseInt(curModemIndex);
        String modName = modemNames.get(readModemIndex);
        AlogMarker.tAE("ModemController.getCurrentModemName", "0");
        return modName;
    }

    public void sendFlushCmd(ModemConf curModConf) throws ModemControlException {
        // if flush command available for this configuration, let s use it.
        if (!curModConf.getFlCmd().equalsIgnoreCase("")) {
            Log.d(TAG, MODULE + ": Config has flush_cmd defined.");
            flush(curModConf);
            // give time to the modem to sync - 1 second
            SystemClock.sleep(1000);
        } else {
            // fall back - check if a default flush cmd is set
            Log.d(TAG, MODULE + ": Fall back - check default_flush_cmd");
            SharedPreferences prefs = AMTLApplication.getContext()
                    .getSharedPreferences("AMTLPrefsData", Context.MODE_PRIVATE);
            if (prefs != null) {
                String flCmd = prefs.getString("default_flush_cmd", "");
                if (!flCmd.equalsIgnoreCase("")) {
                    sendCommand(flCmd + "\r\n");
                    // give time to the modem to sync - 1 second
                    SystemClock.sleep(1000);
                }
            }
        }
    }

    public void closeModemInterface() {
        AlogMarker.tAB("ModemController.closeModemInterface", "0");
        if (this.modIfMgr != null) {
            this.modIfMgr.closeModemInterface();
            this.modIfMgr = null;
        }
        AlogMarker.tAE("ModemController.closeModemInterface", "0");
    }

    public void openModemInterface() throws ModemControlException {
        AlogMarker.tAB("ModemController.openModemInterface", "0");
        if (this.modIfMgr == null) {
            this.modIfMgr = (AMTLApplication.getModemInfo().equals("mmgr"))
                ? new MMGRModemInterfaceMgr() : new SofiaModemInterfaceMgr();
        }
        AlogMarker.tAE("ModemController.closeModemInterface", "0");
    }

    protected void sendMessage(String msg) {
        AlogMarker.tAB("ModemController.sendMessage", "0");
        Intent intent = new Intent("modem-event");
        intent.putExtra("message", msg);
        AMTLApplication.getContext().sendBroadcast(intent);
        AlogMarker.tAE("ModemController.sendMessage", "0");
    }
}
