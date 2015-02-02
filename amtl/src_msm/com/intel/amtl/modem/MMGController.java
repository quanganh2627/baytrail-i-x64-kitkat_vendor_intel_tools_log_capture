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

import com.intel.amtl.common.exceptions.ModemControlException;
import com.intel.amtl.common.log.AlogMarker;
import com.intel.amtl.common.models.config.ModemConf;
import com.intel.amtl.common.modem.ModemController;

import java.util.ArrayList;


public abstract class MMGController extends ModemController {

    private final String TAG = "AMTL";
    private final String MODULE = "MMGController";

    private static boolean modemAcquired = false;
    private boolean firstAcquire = true;

    public abstract boolean queryTraceState() throws ModemControlException;
    public abstract String switchOffTrace() throws ModemControlException;
    public abstract void switchTrace(ModemConf mdmconf) throws ModemControlException;
    public abstract String checkAtTraceState() throws ModemControlException;
    public abstract ModemConf getNoLoggingConf();

    /* Get a reference of MMGController: singleton design pattern */
    public static MMGController get() throws ModemControlException {
        AlogMarker.tAB("MMGController.get", "0");
        AlogMarker.tAE("MMGController.get", "0");
        throw new ModemControlException("MMGController doesn't exist on this platform");
    }

    @Override
    public void connectToModem() throws ModemControlException {
        AlogMarker.tAB("MMGController.connectToModem", "0");
        AlogMarker.tAE("MMGController.connectToModem", "0");
    }

    @Override
    public String getModemStatus() {
        AlogMarker.tAB("MMGController.connectToModem", "0");
        AlogMarker.tAE("MMGController.connectToModem", "0");
        return "DOWN";
    }

    // restart the modem by asking for a cold reset
    public void restartModem() throws ModemControlException {
        AlogMarker.tAB("MMGController.getModemStatus", "0");
        AlogMarker.tAE("MMGController.getModemStatus", "0");
    }

    public String sendCommand(String command) throws ModemControlException {
        AlogMarker.tAB("MMGController.sendCommand", "0");
        AlogMarker.tAE("MMGController.sendCommand", "0");
        return "OK";
    }

    public String flush(ModemConf mdmConf) throws ModemControlException {
        AlogMarker.tAB("MMGController.flush", "0");
        AlogMarker.tAE("MMGController.flush", "0");
        return "OK";
    }

    public String confTraceAndModemInfo(ModemConf mdmConf) throws ModemControlException {
        AlogMarker.tAB("MMGController.confTraceAndModemInfo", "0");
        AlogMarker.tAE("MMGController.confTraceAndModemInfo", "0");
        return "OK";
    }

    public String checkAtXsioState() throws ModemControlException {
        AlogMarker.tAB("MMGController.checkAtXsioState", "0");
        AlogMarker.tAE("MMGController.checkAtXsioState", "0");
        return "OK";
    }

    public String checkAtXsystraceState() throws ModemControlException {
        AlogMarker.tAB("MMGController.checkAtXsystraceState", "0");
        AlogMarker.tAE("MMGController.checkAtXsystraceState", "0");
        return "0";
    }

    public ArrayList<Master> checkAtXsystraceState(ArrayList<Master> masterList)
            throws ModemControlException {
        AlogMarker.tAB("MMGController.checkAtXsystraceState", "0");
        AlogMarker.tAE("MMGController.checkAtXsystraceState", "0");
        return null;
    }

    public String checkOct() throws ModemControlException {
        AlogMarker.tAB("MMGController.checkOct", "0");
        AlogMarker.tAE("MMGController.checkOct", "0");
        return "OK";
    }

    public String generateModemCoreDump() throws ModemControlException {
        AlogMarker.tAB("MMGController.generateModemCoreDump", "0");
        AlogMarker.tAE("MMGController.generateModemCoreDump", "0");
        return "OK";
    }

    public void releaseResource() {
        AlogMarker.tAB("MMGController.releaseResource", "0");
        AlogMarker.tAE("MMGController.releaseResource", "0");
    }

    public boolean isModemAcquired() {
        AlogMarker.tAB("MMGController.isModemAcquired", "0");
        AlogMarker.tAE("MMGController.isModemAcquired", "0");
        return this.modemAcquired;
    }

    public void acquireResource() throws ModemControlException  {
        AlogMarker.tAB("MMGController.acquireResource", "0");
        AlogMarker.tAE("MMGController.acquireResource", "0");
    }

    protected void disconnectModemStatusManager() {
        AlogMarker.tAB("MMGController.disconnectModemStatusManager", "0");
        AlogMarker.tAE("MMGController.disconnectModemStatusManager", "0");
    }

    @Override
    public void cleanBeforeExit() {
        AlogMarker.tAB("MMGController.cleanBeforeExit", "0");
        AlogMarker.tAE("MMGController.cleanBeforeExit", "0");
    }

    public boolean isModemUp() {
        AlogMarker.tAB("MMGController.isModemUp", "0");
        AlogMarker.tAE("MMGController.isModemUp", "0");
        return false;
    }
}
