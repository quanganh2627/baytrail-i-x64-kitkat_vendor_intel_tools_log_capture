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

package com.intel.amtl.common.models;

import android.content.SharedPreferences;

import com.intel.amtl.common.exceptions.ModemControlException;
import com.intel.amtl.common.models.config.LogOutput;
import com.intel.amtl.common.models.config.ModemConf;
import com.intel.amtl.common.modem.ModemController;

import java.util.ArrayList;

public interface ConfigManager {
    public int applyConfig(ModemConf mdmConf, ModemController modemCtrl)
            throws ModemControlException;
    public int updateCurrentIndex(ModemConf curModConf, int currentIndex, String modemName,
            ModemController modemCtrl, ArrayList<LogOutput> configArray);
}
