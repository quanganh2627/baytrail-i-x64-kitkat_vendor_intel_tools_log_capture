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

import com.intel.amtl.common.AMTLApplication;
import com.intel.amtl.common.exceptions.ModemControlException;
import com.intel.amtl.common.log.AlogMarker;
import com.intel.amtl.common.models.config.Master;
import com.intel.amtl.common.models.config.ModemConf;
import com.intel.amtl.common.modem.ModemController;

import com.msm.android.MSMClientLib.MSMManager;
import com.msm.android.MSMClientLib.MSMStateListener;

import java.util.ArrayList;

public class MSMController extends ModemController {

    private final String TAG = "AMTL";
    private final String MODULE = "MMGController";
    private static MSMController msmCtrl;
    // Modem status set by default to UP to enable UI but should be set to UNKNOWN
    private static String currModemStatus = "UP";

    private MSMManager msmMgr;

    public MSMController() throws ModemControlException {
        AlogMarker.tAB("MSMController.MSMController", "0");
        AlogMarker.tAE("MSMController.MSMController", "0");
    }

    public static MSMController get() throws ModemControlException {
        AlogMarker.tAB("MSMController.get", "0");
        if (null == msmCtrl) {
            msmCtrl = new MSMController();
        }
        AlogMarker.tAE("MSMController.get", "0");
        return msmCtrl;
    }

    public void connectToModem() throws ModemControlException {
        AlogMarker.tAB("MSMController.connectToModem", "0");
        msmMgr = new MSMManager(AMTLApplication.getContext());
        msmMgr.listen(listener, MSMStateListener.LISTEN_MODEM_STATE_CHANGE);
        AlogMarker.tAE("MSMController.connectToMode", "0");
    }

    public String getModemStatus() {
        AlogMarker.tAB("MSMController.getModemStatus", "0");
        AlogMarker.tAE("MSMController.getModemStatus", "0");
        return currModemStatus;
    }

    // restart the modem by asking for a cold reset
    public void restartModem() throws ModemControlException {
        AlogMarker.tAB("MSMController.restartModem", "0");
        AlogMarker.tAE("MSMController.restartModem", "0");
    }

    public String sendCommand(String command) throws ModemControlException {
        AlogMarker.tAB("MSMController.sendCommand", "0");
        String ret = "NOK";

        if (command != null) {
            if (!command.equals("")) {
                if (this.isModemUp()) {
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
        AlogMarker.tAE("MSMController.sendCommand", "0");
        return ret;
    }

    public String flush(ModemConf mdmConf) throws ModemControlException {
        AlogMarker.tAB("MSMController.flush", "0");
        AlogMarker.tAE("MSMController.flush", "0");
        return "OK";
    }

    public String confTraceAndModemInfo(ModemConf mdmConf) throws ModemControlException {
        AlogMarker.tAB("MSMController.confTraceAndModemInfo", "0");
        AlogMarker.tAE("MSMController.confTraceAndModemInfo", "0");
        return "OK";
    }

    public String checkAtXsioState() throws ModemControlException {
        AlogMarker.tAB("MSMController.checkAtXsioState", "0");
        AlogMarker.tAE("MSMController.checkAtXsioState", "0");
        return "";
    }

    public String checkAtXsystraceState() throws ModemControlException {
        AlogMarker.tAB("MSMController.checkAtXsystraceState", "0");
        AlogMarker.tAE("MSMController.checkAtXsystraceState", "0");
        return sendCommand("at+xsystrace=10\r\n");
    }

    public ArrayList<Master> checkAtXsystraceState(ArrayList<Master> masterList)
            throws ModemControlException {
        AlogMarker.tAB("MSMController.checkAtXsystraceState", "0");
        AlogMarker.tAE("MSMController.checkAtXsystraceState", "0");
        return getCmdParser().parseXsystraceResponse(sendCommand("at+xsystrace=10\r\n"),
                masterList);
    }

    public String checkOct() throws ModemControlException {
        AlogMarker.tAB("MSMController.checkOct", "0");
        AlogMarker.tAE("MSMController.checkOct", "0");
        return getCmdParser().parseOct(sendCommand("at+xsystrace=11\r\n"));
    }

    public String generateModemCoreDump() throws ModemControlException {
        AlogMarker.tAB("MSMController.generateModemCoreDump", "0");
        AlogMarker.tAE("MSMController.generateModemCoreDump", "0");
        return "OK";
    }

    public void releaseResource() {
        AlogMarker.tAB("MSMController.releaseResource", "0");
        AlogMarker.tAE("MSMController.releaseResource", "0");
    }

    public boolean isModemAcquired() {
        AlogMarker.tAB("MSMController.isModemAcquired", "0");
        AlogMarker.tAE("MSMController.isModemAcquired", "0");
        return true;
    }

    public void acquireResource() throws ModemControlException {
        AlogMarker.tAB("MSMController.acquireResource", "0");
        AlogMarker.tAE("MSMController.acquireResource", "0");
    }

    public void cleanBeforeExit() {
        AlogMarker.tAB("MSMController.cleanBeforeExit", "0");
        super.closeModemInterface();
        this.msmCtrl = null;
        AlogMarker.tAE("MSMController.cleanBeforeExit", "0");
    }

    public boolean isModemUp() {
        AlogMarker.tAB("MSMController.isModemUp", "0");
        AlogMarker.tAE("MSMController.isModemUp", "0");
        return (currModemStatus.equals("UP") ? true : false);
    }

    private MSMStateListener listener = new MSMStateListener() {
        public void onModemStateChange(int state) {
            AlogMarker.tAB("MSMController.onModemStateChange", "0");
            switch (state) {
                case MSMStateListener.E_MODEM_DOWN:
                    Log.d(TAG, MODULE + ": Modem is DOWN");
                    currModemStatus = "DOWN";
                    sendMessage("DOWN");
                    break;
                case MSMStateListener.E_MODEM_SHUTDOWN:
                    Log.d(TAG, MODULE + ": Modem is SHUTTING DOWN");
                    currModemStatus = "DOWN";
                    sendMessage("DOWN");
                    break;
                case MSMStateListener.E_MODEM_UP:
                    Log.d(TAG, MODULE + ": Modem is UP");
                    currModemStatus = "UP";
                    sendMessage("UP");
                    break;
            }
            Log.d(TAG, MODULE);
            AlogMarker.tAE("MSMController.onModemStateChange", "0");
        }
    };

    @Override
    public ModemConf getNoLoggingConf() {
        AlogMarker.tAB("MSMController.getNoLoggingConf", "0");
        AlogMarker.tAE("MSMController.getNoLoggingConf", "0");
        return ModemConf.getInstance("", "", "AT+XSYSTRACE=0\r\n", "", "");
    }

    @Override
    public boolean queryTraceState() throws ModemControlException {
        AlogMarker.tAB("MSMController.queryTraceState", "0");
        AlogMarker.tAE("MSMController.queryTraceState", "0");
        return !checkOct().equals("0");
    }

    @Override
    public String switchOffTrace() throws ModemControlException {
        AlogMarker.tAB("MSMController.switchOffTrace", "0");
        AlogMarker.tAE("MSMController.switchOffTrace", "0");
        return sendCommand("AT+XSYSTRACE=0\r\n");
    }

    @Override
    public void switchTrace(ModemConf mdmConf) throws ModemControlException {
        AlogMarker.tAB("MSMController.switchTrace", "0");
        AlogMarker.tAE("MSMController.switchTrace", "0");
        sendCommand(mdmConf.getXsystrace());
    }

    @Override
    public String checkAtTraceState() throws ModemControlException {
        AlogMarker.tAB("MSMController.checkAtTraceState", "0");
        AlogMarker.tAE("MSMController.checkAtTraceState", "0");
        return "";
    }
}
