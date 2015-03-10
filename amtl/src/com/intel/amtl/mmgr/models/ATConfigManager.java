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
import com.intel.amtl.common.StoredSettings;

import java.util.ArrayList;

public class ATConfigManager implements ConfigManager {


    private final String TAG = "AMTL";
    private final String MODULE = "ATConfigManager";

    public int applyConfig(ModemConf mdmConf, ModemController modemCtrl)
        throws ModemControlException {
        AlogMarker.tAB("ATConfigManager.applyConfig", "0");
        if (modemCtrl != null) {
            if (mdmConf != null) {
                // send the commands to set the new configuration
                modemCtrl.confTraceAndModemInfo(mdmConf);
                mdmConf = checkProfileSent(mdmConf);
                modemCtrl.switchTrace(mdmConf);
                modemCtrl.sendFlushCmd(mdmConf);

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

    private ModemConf checkProfileSent(ModemConf mdmCf) {
        AlogMarker.tAB("ATConfigManager.checkProfileSend", "0");
        if (AMTLApplication.getIsAliasUsed()) {
            String currModemProfile = mdmCf.getProfileName();
            if (!currModemProfile.equals("all_off")) {
                StoredSettings privatePrefs = new StoredSettings(AMTLApplication.getContext());
                String profileName = privatePrefs.getModemProfile();
                if (!profileName.equals(currModemProfile)) {
                    mdmCf.updateProfileName(profileName);
                }
            }
        }
        AlogMarker.tAE("ATConfigManager.checkProfileSend", "0");
        return mdmCf;
    }

    private int resetConf(ModemController mdmCtrl, ModemConf conf)
            throws ModemControlException {
        mdmCtrl.switchOffTrace();
        mdmCtrl.sendFlushCmd(conf);
        return -1;
    }

    public int updateCurrentIndex(ModemConf curModConf, int currentIndex, String modemName,
            ModemController modemCtrl, ArrayList<LogOutput> configArray) {
        AlogMarker.tAB("ATConfigManager.updateCurrentIndex", "0");
        boolean confReset = false;
        int updatedIndex = currentIndex;

        try {
            if (updatedIndex == -2) {
                LogOutput defaultConf = AMTLApplication.getDefaultConf();
                if (defaultConf != null && defaultConf.getXsio() != null
                       && defaultConf.getOct() != null) {
                    if (defaultConf.getXsio().equals(curModConf.getXsio())
                            && defaultConf.getOct().equals(curModConf.getOctMode())) {
                        updatedIndex = defaultConf.getIndex();
                    } else {
                        updatedIndex = this.resetConf(modemCtrl, curModConf);
                        confReset = true;
                    }
                } else {
                    if (configArray != null) {
                        for (LogOutput o: configArray) {
                            if (o != null && o.getXsio() != null && o.getMtsOutput() != null
                                    && o.getOct() != null) {
                                if (o.getXsio().equals(curModConf.getXsio())
                                        && o.getMtsOutput().equals(curModConf.getMtsConf()
                                        .getOutput()) && o.getOct()
                                        .equals(curModConf.getOctMode())) {
                                    updatedIndex = o.getIndex();
                                }
                            }
                        }
                    } else {
                        updatedIndex = this.resetConf(modemCtrl, curModConf);
                        confReset = true;
                    }
                }
            }
            if (updatedIndex >= 0 || ExpertConfig.isExpertModeEnabled(modemName)) {
                if (curModConf.confTraceEnabled()) {
                    if (curModConf.isMtsRequired() && !MtsManager.getMtsState().equals("running")) {
                        MtsManager.startService(curModConf.getMtsMode());
                    } else if (!curModConf.isMtsRequired() && !MtsManager.getMtsState()
                            .equals("stopped")) {
                        MtsManager.stopServices();
                    }
                } else {
                    ExpertConfig.setExpertMode(modemName, false);
                    updatedIndex = -1;
                }
            }

            if (updatedIndex == -1 && !ExpertConfig.isExpertModeEnabled(modemName)) {
                if (curModConf.confTraceEnabled() && !confReset) {
                    updatedIndex = this.resetConf(modemCtrl, curModConf);
                }
                if (!MtsManager.getMtsState().equals("stopped")) {
                    MtsManager.stopServices();
                }
            }
        } catch (ModemControlException ex) {
            Log.e(TAG, MODULE + " : an error occured while stopping logs " + ex);
        }
        AlogMarker.tAE("ATConfigManager.updateCurrentIndex", "0");
        return updatedIndex;
    }
}
