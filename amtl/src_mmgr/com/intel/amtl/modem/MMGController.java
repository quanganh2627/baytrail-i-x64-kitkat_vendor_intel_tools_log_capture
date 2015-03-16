/* Android Modem Traces and Logs
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
 * Author: Morgane Butscher <morganeX.butscher@intel.com>
 */

package com.intel.amtl.modem;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.intel.amtl.common.AMTLApplication;
import com.intel.amtl.common.exceptions.ModemControlException;
import com.intel.amtl.common.log.AlogMarker;
import com.intel.amtl.common.models.config.Master;
import com.intel.amtl.common.models.config.ModemConf;
import com.intel.amtl.common.modem.ModemController;
import com.intel.amtl.mmgr.modem.AliasController;
import com.intel.amtl.mmgr.modem.OctController;
import com.intel.amtl.mmgr.modem.TraceLegacyController;
import com.intel.internal.telephony.MmgrClientException;
import com.intel.internal.telephony.ModemEventListener;
import com.intel.internal.telephony.ModemNotification;
import com.intel.internal.telephony.ModemNotificationArgs;
import com.intel.internal.telephony.ModemStatus;
import com.intel.internal.telephony.ModemStatusManager;

import java.io.IOException;
import java.lang.Object;
import java.util.ArrayList;


public abstract class MMGController extends ModemController implements ModemEventListener {

    private final String TAG = "AMTL";
    private final String MODULE = "MMGController";

    private ModemStatusManager modemStatusManager;
    private ModemStatus currentModemStatus = ModemStatus.NONE;
    private static MMGController mmgCtrl;
    private static boolean modemAcquired = false;
    private ArrayList<Master> masterArray;
    private boolean firstAcquire = true;

    public abstract boolean queryTraceState() throws ModemControlException;
    public abstract String switchOffTrace() throws ModemControlException;
    public abstract void switchTrace(ModemConf mdmconf) throws ModemControlException;
    public abstract String checkAtTraceState() throws ModemControlException;
    public abstract ModemConf getNoLoggingConf();

    public MMGController() throws ModemControlException {
        AlogMarker.tAB("MMGController.MMGController", "0");
        try {
            this.modemStatusManager = ModemStatusManager
                    .getInstance(AMTLApplication.getModemConnectionId());
        } catch (InstantiationException ex) {
            throw new ModemControlException("Cannot instantiate Modem Status Manager");
        }
        AlogMarker.tAE("MMGController.MMGController", "0");
    }

    /* Get a reference of MMGController: singleton design pattern */
    public static MMGController get() throws ModemControlException {
        AlogMarker.tAB("MMGController.get", "0");

        if (null == mmgCtrl) {
            mmgCtrl = (AMTLApplication.getTraceLegacy())
                    ? new TraceLegacyController() : (AMTLApplication.getIsAliasUsed())
                    ? new AliasController() : new OctController();
        }
        AlogMarker.tAE("MMGController.get", "0");
        return mmgCtrl;
    }

    @Override
    public void connectToModem() throws ModemControlException {
        AlogMarker.tAB("MMGController.connectToModem", "0");
        if (this.modemStatusManager != null) {
            try {
                Log.d(TAG, MODULE + ": Subscribing to Modem Status Manager");
                this.modemStatusManager.subscribeToEvent(this, ModemStatus.ALL,
                        ModemNotification.ALL);
            } catch (MmgrClientException ex) {
                throw new ModemControlException("Cannot subscribe to Modem Status Manager " + ex);
            }
            try {
                Log.d(TAG, MODULE + ": Connecting to Modem Status Manager");
                this.modemStatusManager.connect("AMTL");
            } catch (MmgrClientException ex) {
                throw new ModemControlException("Cannot connect to Modem Status Manager " + ex);
            }
            try {
                Log.d(TAG, MODULE + ": Acquiring modem resource");
                this.modemStatusManager.acquireModem();
                this.modemAcquired = true;
                this.firstAcquire = false;
            } catch (MmgrClientException ex) {
                throw new ModemControlException("Cannot acquire modem resource " + ex);
            }
        }
        AlogMarker.tAE("MMGController.connectToModem", "0");
    }

    @Override
    public String getModemStatus() {
        AlogMarker.tAB("MMGController.getModemStatus", "0");
        switch (currentModemStatus) {
            case UP:
                AlogMarker.tAE("MMGController.getModemStatus", "0");
                return "UP";
            case DOWN:
                AlogMarker.tAE("MMGController.getModemStatus", "0");
                return "DOWN";
            case DEAD:
                AlogMarker.tAE("MMGController.getModemStatus", "0");
                return "DEAD";
            case NONE:
            default:
                AlogMarker.tAE("MMGController.getModemStatus", "0");
                return "NONE";
        }
    }

    // restart the modem by asking for a cold reset
    public void restartModem() throws ModemControlException {
        AlogMarker.tAB("MMGController.restartModem", "0");
        if (this.modemStatusManager != null) {
            try {
                Log.d(TAG, MODULE + ": Asking for modem restart");
                this.modemStatusManager.restartModem();
            } catch (MmgrClientException ex) {
                throw new ModemControlException("Cannot restart modem");
            }
        }
        AlogMarker.tAE("MMGController.restartModem", "0");
    }

    public String sendCommand(String command) throws ModemControlException {
        AlogMarker.tAB("MMGController.sendCommand", "0");
        String ret = "NOK";

        if (command != null) {
            if (!command.equals("")) {
                if (this.currentModemStatus != ModemStatus.UP) {
                    throw new ModemControlException("Cannot send at command, "
                            + "modem is not ready: status = " + this.currentModemStatus);
                }
                modIfMgr.writeToModemControl(command);

                // the AT command AT+XLOG=4 doesn't return anything
                if (command.equals("AT+XLOG=4\r\n")) {
                    AlogMarker.tAE("MMGController.sendCommand", "0");
                    return "OK";
                }
                ret = "";
                do {
                    //Response may return in two lines.
                    ret += modIfMgr.readFromModemControl();
                } while (!ret.contains("OK") && !ret.contains("ERROR")
                        && !command.equals("at+xsystrace=pn#\r\n"));

                if (ret.contains("ERROR")) {
                    throw new ModemControlException("Modem has answered" + ret
                            + "to the AT command sent " + command);
                }
            }
        }
        AlogMarker.tAE("MMGController.sendCommand", "0");
        return ret;
    }

    public String flush(ModemConf mdmConf) throws ModemControlException {
        AlogMarker.tAB("MMGController.flush", "0");
        AlogMarker.tAE("MMGController.flush", "0");
        return sendCommand(mdmConf.getFlCmd());
    }

    public String confTraceAndModemInfo(ModemConf mdmConf) throws ModemControlException {
        AlogMarker.tAB("MMGController.confTraceAndModemInfo", "0");
        AlogMarker.tAE("MMGController.confTraceAndModemInfo", "0");
        return sendCommand(mdmConf.getXsio());
    }

    public String checkAtXsioState() throws ModemControlException {
        AlogMarker.tAB("MMGController.checkAtXsioState", "0");
        AlogMarker.tAE("MMGController.checkAtXsioState", "0");
        return getCmdParser().parseXsioResponse(sendCommand("at+xsio?\r\n"));
    }

    public String checkAtXsystraceState() throws ModemControlException {
        AlogMarker.tAB("MMGController.checkAtXsystraceState", "0");
        AlogMarker.tAE("MMGController.checkAtXsystraceState", "0");
        return sendCommand("at+xsystrace=10\r\n");
    }

    public ArrayList<Master> checkAtXsystraceState(ArrayList<Master> masterList)
            throws ModemControlException {
        AlogMarker.tAB("MMGController.checkAtXsystraceState", "0");
        AlogMarker.tAE("MMGController.checkAtXsystraceState", "0");
        return getCmdParser().parseXsystraceResponse(sendCommand("at+xsystrace=10\r\n"),
                masterList);
    }

    public String checkOct() throws ModemControlException {
        AlogMarker.tAB("MMGController.checkOct", "0");
        AlogMarker.tAE("MMGController.checkOct", "0");
        return getCmdParser().parseOct(sendCommand("at+xsystrace=11\r\n"));
    }

    public String checkProfileName() throws ModemControlException {
        AlogMarker.tAB("MMGController.checkProfileName", "0");
        AlogMarker.tAE("MMGController.checkProfileName", "0");
        return getCmdParser().parseProfileName(sendCommand("at+xsystrace=pn#\r\n"));
    }

    public String generateModemCoreDump() throws ModemControlException {
        AlogMarker.tAB("MMGController.generateModemCoreDump", "0");
        AlogMarker.tAE("MMGController.generateModemCoreDump", "0");
        return sendCommand("AT+XLOG=4\r\n");
    }

    public void releaseResource() {
        AlogMarker.tAB("MMGController.releaseResource", "0");
        if (this.modemStatusManager != null) {
            try {
                if (this.modemAcquired) {
                    this.modemStatusManager.releaseModem();
                    this.modemAcquired = false;
                }
            } catch (MmgrClientException ex) {
                Log.e(TAG, MODULE + ": Cannot release modem resource");
            }
        }
        AlogMarker.tAE("MMGController.releaseResource", "0");
    }

    public boolean isModemAcquired() {
        AlogMarker.tAB("MMGController.isModemAcquired", "0");
        AlogMarker.tAE("MMGController.isModemAcquired", "0");
        return this.modemAcquired;
    }

    public void acquireResource() throws ModemControlException  {
        AlogMarker.tAB("MMGController.acquireResource", "0");
        try {
            if (this.modemStatusManager != null) {
                if (!this.modemAcquired && !this.firstAcquire) {
                    this.modemStatusManager.acquireModem();
                    this.modemAcquired = true;
                }
            }
        } catch (MmgrClientException ex) {
            throw new ModemControlException("Could not acquire modem" + ex);
        }
        AlogMarker.tAE("MMGController.acquireResource", "0");
    }

    protected void disconnectModemStatusManager() {
        AlogMarker.tAB("MMGController.disconnectModemStatusManager", "0");
        if (modemStatusManager != null) {
            releaseResource();
            modemStatusManager.disconnect();
            modemStatusManager = null;
        }
        AlogMarker.tAE("MMGController.disconnectModemStatusManager", "0");
    }

    @Override
    public void cleanBeforeExit() {
        AlogMarker.tAB("MMGController.cleanBeforeExit", "0");
        super.closeModemInterface();
        disconnectModemStatusManager();
        this.mmgCtrl = null;
        AlogMarker.tAE("MMGController.cleanBeforeExit", "0");
    }

    public boolean isModemUp() {
        AlogMarker.tAB("MMGController.isModemUp", "0");
        AlogMarker.tAE("MMGController.isModemUp", "0");
        return (currentModemStatus == ModemStatus.UP) ? true : false;
    }

    @Override
    public void onModemColdReset(ModemNotificationArgs args) {
        AlogMarker.tAB("MMGController.onModemColdReset", "0");
        Log.d(TAG, MODULE + ": Modem is performing a COLD RESET");
        AlogMarker.tAE("MMGController.onModemColdReset", "0");
    }

    @Override
    public void onModemShutdown(ModemNotificationArgs args) {
        AlogMarker.tAB("MMGController.onModemShutdown", "0");
        Log.d(TAG, MODULE + ": Modem is performing a SHUTDOWN");
        AlogMarker.tAE("MMGController.onModemShutdown", "0");
    }

    @Override
    public void onPlatformReboot(ModemNotificationArgs args) {
        AlogMarker.tAB("MMGController.onPlatformReboot", "0");
        Log.d(TAG, MODULE + ": Modem is performing a PLATFORM REBOOT");
        AlogMarker.tAE("MMGController.onPlatformReboot", "0");
    }

    @Override
    public void onModemCoreDump(ModemNotificationArgs args) {
        AlogMarker.tAB("MMGController.onModemCoreDump", "0");
        Log.d(TAG, MODULE + ": Modem is performing a COREDUMP");
        AlogMarker.tAE("MMGController.onModemCoreDump", "0");
    }

    @Override
    public void onModemUp() {
        AlogMarker.tAB("MMGController.onModemUp", "0");
        this.currentModemStatus = ModemStatus.UP;
        Log.d(TAG, MODULE + ": Modem is UP");
        try {
            AMTLApplication.setCloseTtyEnable(false);
            openModemInterface();
            sendMessage("UP");
        } catch (ModemControlException ex) {
            Log.e(TAG, MODULE + ex);
        }
        AlogMarker.tAE("MMGController.onModemUp", "0");
    }

    @Override
    public void onModemDown() {
        AlogMarker.tAB("MMGController.onModemDown", "0");
        this.currentModemStatus = ModemStatus.DOWN;
        Log.d(TAG, MODULE + ": Modem is DOWN");
        sendMessage("DOWN");
        closeModemInterface();
        AlogMarker.tAE("MMGController.onModemDown", "0");
    }

    @Override
    public void onModemDead() {
        AlogMarker.tAB("MMGController.onModemDead", "0");
        this.onModemDown();
        this.currentModemStatus = ModemStatus.DEAD;
        Log.d(TAG, MODULE + ": Modem is DEAD");
        AlogMarker.tAE("MMGController.onModemDead", "0");
    }
}
