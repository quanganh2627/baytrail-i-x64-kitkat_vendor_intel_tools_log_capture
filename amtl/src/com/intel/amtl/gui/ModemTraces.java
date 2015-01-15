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
 * Author: Nicolae Natea <nicolaex.natea@intel.com>
 */

package com.intel.amtl.gui;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Switch;

import com.intel.amtl.AMTLApplication;
import com.intel.amtl.exceptions.ModemControlException;
import com.intel.amtl.helper.LogManager;
import com.intel.amtl.models.config.ModemConf;
import com.intel.amtl.modem.ModemController;
import com.intel.amtl.R;

import java.io.IOException;
import java.util.ArrayList;


public class ModemTraces implements GeneralTracing {
    Process logcatProc = null;
    private final String TAG = "AMTL";
    private final String MODULE = "ModemTraces";

    private final String CONFSETUP_TAG = "AMTL_modem_configuration_setup";
    private final String EXPERT_PROPERTY = "persist.service.amtl.expert";
    private final int CONFSETUP_TARGETFRAG = 0;
    private final String AP_LOG_PATH = "/mnt/sdcard/logs/";
    private final String BP_LOG_PATH = "/logs/";
    private final int PTI_KILL_WAIT = 1000;

    private Runtime rtm = java.lang.Runtime.getRuntime();

    private Switch switchModem = null;
    private boolean modemLog = false;
    private boolean running = false;

    private String lastStatus = "";

    private ModemConf modemConfToApply;
    private FragmentManager elfManager = null;
    private Fragment parent = null;
    private Activity activity = null;

    ModemTraces(Activity activity, Fragment parent) {
        this.elfManager = activity.getFragmentManager();
        this.parent = parent;
        this.activity = activity;
    }

    public boolean start() {
        if (!isModemDumpEnabled()) {
            lastStatus = "Modem tracing not enabled";
            return false;
        }

        if (!isRunning()) {
            setRunning(startModemTracing());
        }

        return isRunning();
    }

    public void stop() {
        if (isRunning()) {
            setRunning(false);
            stopModemTracing();
        }
    }

    public void cleanTemp() {
        /* would it seem a bit dumb to delete files based on a wildcard ? */
        /*if (isRunning())
            stop();

        start();*/
    }

    public void saveTemp(String path) {

        LogManager snaplog = new LogManager(path, AP_LOG_PATH, BP_LOG_PATH);
        if (snaplog == null) {
            return;
        }

        snaplog.makeBackup(path, false);
    }


    private boolean startModemTracing() {
        modemConfToApply = ActionMenu.getModemConfiguration();
        if (modemConfToApply == null) {
            Log.e(TAG, MODULE + ": Could not start modem logging as no configuration set up");
            lastStatus = "No modem configuration selected.";
            return false;
        }

        ConfigApplyFrag progressFrag = ConfigApplyFrag.newInstance(CONFSETUP_TAG,
                CONFSETUP_TARGETFRAG);
        progressFrag.launch(modemConfToApply, this.parent, elfManager);

        lastStatus = "Modem tracing started";
        return true;
    }

    private void stopModemTracing() {

        try {
            rtm.exec("start pti_sigusr1");
            android.os.SystemClock.sleep(PTI_KILL_WAIT);
            Log.d(TAG, MODULE + ": Stopping trace BP");
            ModemController modemCtrl = ModemController.getInstance();
            ModemConf modConfToApply = modemCtrl.getNoLoggingConf();
            ConfigApplyFrag progressFrag = ConfigApplyFrag.newInstance(CONFSETUP_TAG,
                    CONFSETUP_TARGETFRAG);
            progressFrag.launch(modConfToApply, this.parent, elfManager);
        } catch (IOException e) {
            Log.e(TAG, MODULE + ": can't send sigusr1 signal " + e);
        } catch (ModemControlException ex) {
            Log.e(TAG, MODULE + " " + ex);
        }

        ArrayList<String> modemNames = new ArrayList<String>();
        modemNames = AMTLApplication.getModemNameList();
        String curModemIndex = PreferenceManager
                   .getDefaultSharedPreferences(AMTLApplication.getContext())
                   .getString(AMTLApplication.getContext()
                   .getString(R.string.settings_modem_name_key), "0");
        int readModemIndex = Integer.parseInt(curModemIndex);
        String modName = modemNames.get(readModemIndex);
        SystemProperties.set(EXPERT_PROPERTY + modName, "0");
    }

    public boolean isRunning() {
        return running;
    }

    private boolean isModemDumpEnabled() {
        /* as long as there is no special ui, always active */
        return true;
        /*updateConfiguration();
        return modemLog;*/
    }

    public void updateConfiguration() {
        modemLog = (this.switchModem == null) ? false : switchModem.isChecked();
    }

    public int getViewID() {
        return R.layout.trace_logcat;
    }

    public void attachReferences(View view) {
        //this.switchModem = (Switch) view.findViewById(R.id.modemDump);
    }

    public void attachListeners() { }

    private void setRunning(boolean enabled) {
        running = enabled;

        if (this.switchModem != null) {
            this.switchModem.setEnabled(!enabled);
        }
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public String getTracerName() {
        return "Modem Traces";
    }
}
