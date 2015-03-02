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

package com.intel.amtl.modem;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.intel.amtl.AMTLApplication;
import com.intel.amtl.exceptions.ModemControlException;
import com.intel.amtl.gui.GeneralSetupFrag;
import com.intel.amtl.gui.AMTLTabLayout;
import com.intel.amtl.models.config.Master;
import com.intel.amtl.models.config.ModemConf;
import com.intel.internal.telephony.MmgrClientException;
import com.intel.internal.telephony.ModemEventListener;
import com.intel.internal.telephony.ModemNotification;
import com.intel.internal.telephony.ModemNotificationArgs;
import com.intel.internal.telephony.ModemStatus;
import com.intel.internal.telephony.ModemStatusManager;

import java.io.Closeable;
import java.io.IOException;
import java.lang.Object;
import java.util.ArrayList;


public abstract class ModemController implements ModemEventListener, Closeable {

    private final String TAG = "AMTL";
    private final String MODULE = "ModemController";

    private GsmttyManager ttyManager;
    private ModemStatusManager modemStatusManager;
    private static ModemStatus currentModemStatus = ModemStatus.NONE;
    private static ModemController mdmCtrl;
    private static boolean modemAcquired = false;
    private CommandParser cmdParser;
    private ArrayList<Master> masterArray;
    private boolean firstAcquire = true;
    private ModemConf noLoggingConf = null;

    public abstract boolean queryTraceState() throws ModemControlException;
    public abstract String switchOffTrace()throws ModemControlException;
    public abstract void switchTrace(ModemConf mdmconf)throws ModemControlException;
    public abstract String checkAtTraceState() throws ModemControlException;
    public abstract ModemConf getNoLoggingConf();

    public ModemController() throws ModemControlException {

        try {
            this.modemStatusManager = ModemStatusManager
                    .getInstance(AMTLApplication.getModemConnectionId());
            cmdParser = new CommandParser();
        } catch (InstantiationException ex) {
            throw new ModemControlException("Cannot instantiate Modem Status Manager");
        }
    }

    /* Get a reference of ModemController: singleton design pattern */
    public static ModemController getInstance() throws ModemControlException {

        if (null == mdmCtrl) {
            mdmCtrl = (AMTLApplication.getTraceLegacy())
                    ? new TraceLegacyController() : new OctController();
        }
        return mdmCtrl;
    }

    public void connectToModem() throws ModemControlException {
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
    }

    public static ModemStatus getModemStatus() {
        return currentModemStatus;
    }

    // restart the modem by asking for a cold reset
    public void restartModem() throws ModemControlException {
        if (this.modemStatusManager != null) {
            try {
                Log.d(TAG, MODULE + ": Asking for modem restart");
                this.modemStatusManager.restartModem();
            } catch (MmgrClientException ex) {
                throw new ModemControlException("Cannot restart modem");
            }
        }
    }

    public String sendAtCommand(String command) throws ModemControlException {
        String ret = "NOK";

        if (command != null) {
            if (!command.equals("")) {
                if (this.currentModemStatus != ModemStatus.UP) {
                    throw new ModemControlException("Cannot send at command, "
                            + "modem is not ready: status = " + this.currentModemStatus);
                }
                this.ttyManager.writeToModemControl(command);

              // the AT command AT+XLOG=4 doesn't return anything
                if (command.equals("AT+XLOG=4\r\n")) {
                    return "OK";
                }
                ret = "";
                do {
                    //Response may return in two lines.
                    ret += this.ttyManager.readFromModemControl();
                } while (!ret.contains("OK") && !ret.contains("ERROR"));

                if (ret.contains("ERROR")) {
                    throw new ModemControlException("Modem has answered" + ret
                            + "to the AT command sent " + command);
                }
            }
        }
        return ret;
    }

    public String flush(ModemConf mdmConf) throws ModemControlException {
        return sendAtCommand(mdmConf.getFlCmd());
    }

    public String confTraceAndModemInfo(ModemConf mdmConf) throws ModemControlException {
        return sendAtCommand(mdmConf.getXsio());
    }

    public String checkAtXsioState() throws ModemControlException {
        return cmdParser.parseXsioResponse(sendAtCommand("at+xsio?\r\n"));
    }

    public String checkAtXsystraceState() throws ModemControlException {
        return sendAtCommand("at+xsystrace=10\r\n");
    }

    public ArrayList<Master> checkAtXsystraceState(ArrayList<Master> masterList)
            throws ModemControlException {
        return cmdParser.parseXsystraceResponse(sendAtCommand("at+xsystrace=10\r\n"), masterList);
    }

    public String checkOct() throws ModemControlException {
        return cmdParser.parseOct(sendAtCommand("at+xsystrace=11\r\n"));
    }

    public String generateModemCoreDump() throws ModemControlException {
        return sendAtCommand("AT+XLOG=4\r\n");
    }

    public CommandParser getCmdParser() {
        return cmdParser;
    }

    public void close() {
        if (this.ttyManager != null) {
            this.ttyManager.close();
            this.ttyManager = null;
        }
    }

    public void openTty() throws ModemControlException {
        if (this.ttyManager == null) {
            this.ttyManager = new GsmttyManager();
        }
    }

    public void releaseResource() {
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
    }

    public boolean isModemAcquired() {
        return this.modemAcquired;
    }

    public void acquireResource() throws MmgrClientException {
        if (this.modemStatusManager != null) {
            if (!this.modemAcquired && !this.firstAcquire) {
                this.modemStatusManager.acquireModem();
                this.modemAcquired = true;
            }
        }
    }

    private void disconnectModemStatusManager() {
        if (this.modemStatusManager != null) {
            this.releaseResource();
            this.modemStatusManager.disconnect();
            this.modemStatusManager = null;
        }
    }

    public void cleanBeforeExit() {
        this.close();
        this.disconnectModemStatusManager();
        this.mdmCtrl = null;
    }

    private void sendMessage(String msg) {
        Intent intent = new Intent("modem-event");
        intent.putExtra("message", msg);
        AMTLApplication.getContext().sendBroadcast(intent);
    }

    public static boolean isModemUp() {
        boolean bStatus = false;

        if (currentModemStatus == ModemStatus.UP) {
            bStatus = true;
        }

        return bStatus;
    }

    @Override
    public void onModemColdReset(ModemNotificationArgs args) {
        Log.d(TAG, MODULE + ": Modem is performing a COLD RESET");
    }

    @Override
    public void onModemShutdown(ModemNotificationArgs args) {
        Log.d(TAG, MODULE + ": Modem is performing a SHUTDOWN");
    }

    @Override
    public void onPlatformReboot(ModemNotificationArgs args) {
        Log.d(TAG, MODULE + ": Modem is performing a PLATFORM REBOOT");
    }

    @Override
    public void onModemCoreDump(ModemNotificationArgs args) {
        Log.d(TAG, MODULE + ": Modem is performing a COREDUMP");
    }

    @Override
    public void onModemUp() {
        this.currentModemStatus = ModemStatus.UP;
        Log.d(TAG, MODULE + ": Modem is UP");
        try {
            AMTLApplication.setCloseTtyEnable(false);
            this.openTty();
            sendMessage("UP");
        } catch (ModemControlException ex) {
            Log.e(TAG, MODULE + ex);
        }
    }

    @Override
    public void onModemDown() {
        this.currentModemStatus = ModemStatus.DOWN;
        Log.d(TAG, MODULE + ": Modem is DOWN");
        sendMessage("DOWN");
        this.close();
    }

    @Override
    public void onModemDead() {
        this.onModemDown();
        this.currentModemStatus = ModemStatus.DEAD;
        Log.d(TAG, MODULE + ": Modem is DEAD");
    }
}
