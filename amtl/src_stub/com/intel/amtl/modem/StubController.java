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
import com.intel.amtl.common.models.config.Master;
import com.intel.amtl.common.models.config.ModemConf;
import com.intel.amtl.common.modem.ModemController;

import java.util.ArrayList;

public class StubController extends ModemController {

    private final String TAG = "AMTL";
    private final String MODULE = "StubController";
    private static StubController stubCtrl;
    // Modem status set by default to UP to enable UI but should be set to UNKNOWN
    private static String currModemStatus = "UP";

    public StubController() throws ModemControlException {
        AlogMarker.tAB("StubController.MSMController", "0");
        AlogMarker.tAE("StubController.MSMController", "0");
    }

    public static StubController get() throws ModemControlException {
        AlogMarker.tAB("StubController.get", "0");
        if (null == stubCtrl) {
            stubCtrl = new StubController();
        }
        AlogMarker.tAE("StubController.get", "0");
        return stubCtrl;
    }

    public void connectToModem() throws ModemControlException {
        AlogMarker.tAB("StubController.connectToModem", "0");
        AlogMarker.tAE("StubController.connectToMode", "0");
    }

    public String getModemStatus() {
        AlogMarker.tAB("StubController.getModemStatus", "0");
        AlogMarker.tAE("StubController.getModemStatus", "0");
        return currModemStatus;
    }

    // restart the modem by asking for a cold reset
    public void restartModem() throws ModemControlException {
        AlogMarker.tAB("StubController.restartModem", "0");
        AlogMarker.tAE("StubController.restartModem", "0");
    }

    public String sendCommand(String command) throws ModemControlException {
        AlogMarker.tAB("StubController.sendCommand", "0");
        String ret = "NOK";

        if (command != null) {
            if (!command.equals("")) {
                if (!this.isModemUp()) {
                    throw new ModemControlException("Cannot send at command, "
                            + "modem is not ready: status = " + this.currModemStatus);
                }
                modIfMgr.writeToModemControl(command);

                ret = "";
                do {
                    //Response may return in two lines.
                    ret += modIfMgr.readFromModemControl();
                } while (!ret.contains("OK") && !ret.contains("ERROR"));

                if (ret.contains("ERROR")) {
                    throw new ModemControlException("Modem has answered" + ret
                            + "to the AT command sent " + command);
                }
            }
        }
        AlogMarker.tAE("StubController.sendCommand", "0");
        return ret;
    }

    public String flush(ModemConf mdmConf) throws ModemControlException {
        AlogMarker.tAB("StubController.flush", "0");
        AlogMarker.tAE("StubController.flush", "0");
        return "OK";
    }

    public String confTraceAndModemInfo(ModemConf mdmConf) throws ModemControlException {
        AlogMarker.tAB("StubController.confTraceAndModemInfo", "0");
        AlogMarker.tAE("StubController.confTraceAndModemInfo", "0");
        return "OK";
    }

    public String checkAtXsioState() throws ModemControlException {
        AlogMarker.tAB("StubController.checkAtXsioState", "0");
        AlogMarker.tAE("StubController.checkAtXsioState", "0");
        return "";
    }

    public String checkAtXsystraceState() throws ModemControlException {
        AlogMarker.tAB("StubController.checkAtXsystraceState", "0");
        AlogMarker.tAE("StubController.checkAtXsystraceState", "0");
        return sendCommand("at+xsystrace=10\r\n");
    }

    public ArrayList<Master> checkAtXsystraceState(ArrayList<Master> masterList)
            throws ModemControlException {
        AlogMarker.tAB("StubController.checkAtXsystraceState", "0");
        AlogMarker.tAE("StubController.checkAtXsystraceState", "0");
        return getCmdParser().parseXsystraceResponse(sendCommand("at+xsystrace=10\r\n"),
                masterList);
    }

    public String checkOct() throws ModemControlException {
        AlogMarker.tAB("StubController.checkOct", "0");
        AlogMarker.tAE("StubController.checkOct", "0");
        return getCmdParser().parseOct(sendCommand("at+xsystrace=11\r\n"));
    }

    public String checkProfileName() throws ModemControlException {
        AlogMarker.tAB("StubController.checkProfileName", "0");
        AlogMarker.tAE("StubController.checkProfileName", "0");
        return getCmdParser().parseProfileName(sendCommand("at+xsystrace=pn#\r\n"));
    }

    public String generateModemCoreDump() throws ModemControlException {
        AlogMarker.tAB("StubController.generateModemCoreDump", "0");
        AlogMarker.tAE("StubController.generateModemCoreDump", "0");
        return "OK";
    }

    public void releaseResource() {
        AlogMarker.tAB("StubController.releaseResource", "0");
        AlogMarker.tAE("StubController.releaseResource", "0");
    }

    public boolean isModemAcquired() {
        AlogMarker.tAB("StubController.isModemAcquired", "0");
        AlogMarker.tAE("StubController.isModemAcquired", "0");
        return true;
    }

    public void acquireResource() throws ModemControlException {
        AlogMarker.tAB("StubController.acquireResource", "0");
        AlogMarker.tAE("StubController.acquireResource", "0");
    }

    public void cleanBeforeExit() {
        AlogMarker.tAB("StubController.cleanBeforeExit", "0");
        super.closeModemInterface();
        this.stubCtrl = null;
        AlogMarker.tAE("StubController.cleanBeforeExit", "0");
    }

    public boolean isModemUp() {
        AlogMarker.tAB("StubController.isModemUp", "0");
        AlogMarker.tAE("StubController.isModemUp", "0");
        return (currModemStatus.equals("UP") ? true : false);
    }

    @Override
    public ModemConf getNoLoggingConf() {
        AlogMarker.tAB("StubController.getNoLoggingConf", "0");
        AlogMarker.tAE("StubController.getNoLoggingConf", "0");
        return ModemConf.getInstance("", "", "AT+XSYSTRACE=0\r\n", "", "");
    }

    @Override
    public boolean queryTraceState() throws ModemControlException {
        AlogMarker.tAB("StubController.queryTraceState", "0");
        AlogMarker.tAE("StubController.queryTraceState", "0");
        return !checkOct().equals("0");
    }

    @Override
    public String switchOffTrace() throws ModemControlException {
        AlogMarker.tAB("StubController.switchOffTrace", "0");
        AlogMarker.tAE("StubController.switchOffTrace", "0");
        return sendCommand("AT+XSYSTRACE=0\r\n");
    }

    @Override
    public void switchTrace(ModemConf mdmConf) throws ModemControlException {
        AlogMarker.tAB("StubController.switchTrace", "0");
        AlogMarker.tAE("StubController.switchTrace", "0");
        sendCommand(mdmConf.getXsystrace());
    }

    @Override
    public String checkAtTraceState() throws ModemControlException {
        AlogMarker.tAB("StubController.checkAtTraceState", "0");
        AlogMarker.tAE("StubController.checkAtTraceState", "0");
        return "";
    }
}
