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

package com.intel.amtl.msm.models;

import android.util.Log;

import com.intel.amtl.common.exceptions.ModemControlException;
import com.intel.amtl.common.log.AlogMarker;
import com.intel.amtl.common.models.ConfigManager;
import com.intel.amtl.common.models.config.ExpertConfig;
import com.intel.amtl.common.models.config.LogOutput;
import com.intel.amtl.common.models.config.ModemConf;
import com.intel.amtl.common.modem.ModemController;

import java.util.ArrayList;

public class SofiaConfigManager implements ConfigManager {


    private final String TAG = "AMTL";
    private final String MODULE = "SofiaConfigManager";


    public int applyConfig(ModemConf mdmConf, ModemController modemCtrl)
            throws ModemControlException {
        AlogMarker.tAB("SofiaConfigManager.applyConfig", "0");
        if (modemCtrl != null) {
            if (mdmConf != null) {
                // send the commands to set the new configuration
                modemCtrl.switchTrace(mdmConf);
            } else {
                throw new ModemControlException("no configuration to apply");
            }
        } else {
            throw new ModemControlException("cannot apply configuration: modemCtrl is null");
        }
        AlogMarker.tAE("SofiaConfigManager.applyConfig", "0");
        return mdmConf.getIndex();
    }

    public int updateCurrentIndex(ModemConf curModConf, int currentIndex, String modemName,
            ModemController modemCtrl, ArrayList<LogOutput> configArray) {
        AlogMarker.tAB("SofiaConfigManager.updateCurrentIndex", "0");
        boolean confReset = false;
        int updatedIndex = currentIndex;
        if (configArray != null) {
            for (LogOutput o: configArray) {
                if (o != null && o.getOct() != null) {
                    if (o.getOct().equals(curModConf.getOctMode())) {
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
        if (updatedIndex >= 0 || ExpertConfig.isExpertModeEnabled(modemName)) {
            if (!curModConf.confTraceEnabled()) {
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
        }
        AlogMarker.tAE("SofiaConfigManager.updateCurrentIndex", "0");
        return updatedIndex;
    }
}
