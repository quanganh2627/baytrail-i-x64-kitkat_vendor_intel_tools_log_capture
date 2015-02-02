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
 * Author: Morgane Butscher <morganeX.butscher@intel.com>
 */

package com.intel.amtl.mmgr.models;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import com.intel.amtl.common.AMTLApplication;
import com.intel.amtl.common.exceptions.ModemControlException;
import com.intel.amtl.common.log.AlogMarker;
import com.intel.amtl.common.models.ConfigManager;
import com.intel.amtl.common.models.config.ExpertConfig;
import com.intel.amtl.common.models.config.LogOutput;
import com.intel.amtl.common.models.config.ModemConf;
import com.intel.amtl.common.modem.ModemController;
import com.intel.amtl.common.mts.MtsManager;

import java.util.ArrayList;

public class ATConfigManager implements ConfigManager {


    private final String TAG = "AMTL";
    private final String MODULE = "ATConfigManager";

    public int applyConfig(SharedPreferences prefs, ModemConf mdmConf, ModemController modemCtrl)
            throws ModemControlException {
        AlogMarker.tAB("ATConfigManager.applyConfig", "0");
        if (modemCtrl != null) {
            if (mdmConf != null) {
                // send the commands to set the new configuration
                modemCtrl.confTraceAndModemInfo(mdmConf);
                modemCtrl.switchTrace(mdmConf);
                // if flush command available for this configuration, let s use it.
                if (!mdmConf.getFlCmd().equalsIgnoreCase("")) {
                    Log.d(TAG, MODULE + ": Config has flush_cmd defined.");
                    modemCtrl.flush(mdmConf);
                    // give time to the modem to sync - 1 second
                    SystemClock.sleep(1000);
                } else {
                    // fall back - check if a default flush cmd is set
                    Log.d(TAG, MODULE + ": Fall back - check default_flush_cmd");
                    String flCmd = prefs.getString("default_flush_cmd", "");
                    if (!flCmd.equalsIgnoreCase("")) {
                        modemCtrl.sendCommand(flCmd + "\r\n");
                        // give time to the modem to sync - 1 second
                        SystemClock.sleep(1000);
                    }
                }

                MtsManager.stopServices();
                // set mts parameters through mts properties
                mdmConf.applyMtsParameters();
                // check if the configuration requires mts
                if (mdmConf.isMtsRequired()) {
                    // start mts in the chosen mode: persistent or oneshot
                    MtsManager.startService(mdmConf.getMtsMode());
                }
                // restart modem by a cold reset
                modemCtrl.restartModem();
                // give time to the modem to be up again
                SystemClock.sleep(2000);
            } else {
                throw new ModemControlException("no configuration to apply");
            }
        } else {
            throw new ModemControlException("cannot apply configuration: modemCtrl is null");
        }
        AlogMarker.tAE("ATConfigManager.applyConfig", "0");
        return mdmConf.getIndex();
    }

    public int updateCurrentIndex(ModemConf curModConf, int currentIndex, String modemName,
            ModemController modemCtrl, ArrayList<LogOutput> configArray) {
        AlogMarker.tAB("ATConfigManager.updateCurrentIndex", "0");
        boolean confReset = false;
        int updatedIndex = currentIndex;
        if (updatedIndex == -2) {
            LogOutput defaultConf = AMTLApplication.getDefaultConf();
            if (defaultConf != null && defaultConf.getXsio() != null
                    && defaultConf.getOct() != null) {
                if (defaultConf.getXsio().equals(curModConf.getXsio())
                        && defaultConf.getOct().equals(curModConf.getOctMode())) {
                    updatedIndex = defaultConf.getIndex();
                } else {
                    try {
                        modemCtrl.switchOffTrace();
                        confReset = true;
                        updatedIndex = -1;
                    } catch (ModemControlException ex) {
                        Log.e(TAG, MODULE + " : an error occured while stopping logs " + ex);
                    }
                }
            } else {
                if (configArray != null) {
                    for (LogOutput o: configArray) {
                        if (o != null && o.getXsio() != null && o.getMtsOutput() != null
                                && o.getOct() != null) {
                            if (o.getXsio().equals(curModConf.getXsio())
                                    && o.getMtsOutput().equals(curModConf.getMtsConf().getOutput())
                                    && o.getOct().equals(curModConf.getOctMode())) {
                                updatedIndex = o.getIndex();
                            }
                        }
                    }
                } else {
                    try {
                        modemCtrl.switchOffTrace();
                        confReset = true;
                        updatedIndex = -1;
                    } catch (ModemControlException ex) {
                        Log.e(TAG, MODULE + " : an error occured while stopping logs " + ex);
                    }
                }
            }
        }
        if (updatedIndex >= 0 || ExpertConfig.isExpertModeEnabled(modemName)) {
            if (curModConf.confTraceEnabled()) {
                if (curModConf.isMtsRequired() && !MtsManager.getMtsState().equals("running")) {
                    MtsManager.startService(curModConf.getMtsMode());
                } else if (!curModConf.isMtsRequired() && MtsManager.getMtsState()
                        .equals("running")) {
                    MtsManager.stopServices();
                }
            } else {
                ExpertConfig.setExpertMode(modemName, false);
                updatedIndex = -1;
            }
        }

        if (updatedIndex == -1 && !ExpertConfig.isExpertModeEnabled(modemName)) {
            if (curModConf.confTraceEnabled() && !confReset) {
                try {
                    modemCtrl.switchOffTrace();
                    updatedIndex = -1;
                } catch (ModemControlException ex) {
                    Log.e(TAG, MODULE + " : an error occured while stopping logs " + ex);
                }
            }
            if (MtsManager.getMtsState().equals("running")) {
                MtsManager.stopServices();
            }
        }
        AlogMarker.tAE("ATConfigManager.updateCurrentIndex", "0");
        return updatedIndex;
    }
}
