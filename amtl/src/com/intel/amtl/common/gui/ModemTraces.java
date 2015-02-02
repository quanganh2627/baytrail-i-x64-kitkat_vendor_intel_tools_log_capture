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

package com.intel.amtl.common.gui;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.os.FileObserver;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Switch;

import com.intel.amtl.common.exceptions.ModemControlException;
import com.intel.amtl.common.helper.LogManager;
import com.intel.amtl.common.log.AlogMarker;
import com.intel.amtl.common.models.config.ExpertConfig;
import com.intel.amtl.common.models.config.ModemConf;
import com.intel.amtl.common.modem.ModemController;
import com.intel.amtl.R;

import java.io.IOException;
import java.util.ArrayList;


public class ModemTraces implements GeneralTracing {
    Process logcatProc = null;
    private final String TAG = "AMTL";
    private final String MODULE = "ModemTraces";

    private final String CONFSETUP_TAG = "AMTL_modem_configuration_setup";
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
        AlogMarker.tAB("ModemTraces.ModemTraces", "0");
        this.elfManager = activity.getFragmentManager();
        this.parent = parent;
        this.activity = activity;
        AlogMarker.tAE("ModemTraces.ModemTraces", "0");
    }

    public boolean start() {
        AlogMarker.tAB("ModemTraces.start", "0");
        if (!isModemDumpEnabled()) {
            lastStatus = "Modem tracing not enabled";
            AlogMarker.tAE("ModemTraces.start", "0");
            return false;
        }

        if (!isRunning()) {
            setRunning(startModemTracing());
        }
        AlogMarker.tAE("ModemTraces.start", "0");

        return isRunning();
    }

    public void stop() {
        AlogMarker.tAB("ModemTraces.stop", "0");
        if (isRunning()) {
            setRunning(false);
            stopModemTracing();
        }
        AlogMarker.tAE("ModemTraces.stop", "0");
    }

    public void cleanTemp() {
        AlogMarker.tAB("ModemTraces.cleanTemp", "0");
        /* would it seem a bit dumb to delete files based on a wildcard ? */
        /*if (isRunning())
            stop();

        start();*/
        AlogMarker.tAE("ModemTraces.cleanTemp", "0");
    }

    public void saveTemp(String path) {
        AlogMarker.tAB("ModemTraces.saveTemp", "0");

        LogManager snaplog = new LogManager(path, AP_LOG_PATH, BP_LOG_PATH);
        if (snaplog == null) {
            AlogMarker.tAE("ModemTraces.saveTemp", "0");
            return;
        }

        AlogMarker.tAE("ModemTraces.saveTemp", "0");
        snaplog.makeBackup(path, false);
    }


    private boolean startModemTracing() {
        AlogMarker.tAB("ModemTraces.saveModemTracing", "0");
        modemConfToApply = ActionMenu.getModemConfiguration();
        if (modemConfToApply == null) {
            Log.e(TAG, MODULE + ": Could not start modem logging as no configuration set up");
            lastStatus = "No modem configuration selected.";
            AlogMarker.tAE("ModemTraces.saveModemTracing", "0");
            return false;
        }

        ConfigApplyFrag progressFrag = ConfigApplyFrag.newInstance(CONFSETUP_TAG,
                CONFSETUP_TARGETFRAG);
        progressFrag.launch(modemConfToApply, this.parent, elfManager);

        lastStatus = "Modem tracing started";
        AlogMarker.tAE("ModemTraces.saveModemTracing", "0");
        return true;
    }

    private void stopModemTracing() {
        AlogMarker.tAB("ModemTraces.stopModemTracing", "0");
        ModemController modemCtrl;
        try {
            rtm.exec("start pti_sigusr1");
            android.os.SystemClock.sleep(PTI_KILL_WAIT);

            Log.d(TAG, MODULE + ": Stopping trace BP");
            modemCtrl = ModemController.getInstance();

            ModemConf modConfToApply = modemCtrl.getNoLoggingConf();
            ConfigApplyFrag progressFrag = ConfigApplyFrag.newInstance(CONFSETUP_TAG,
                    CONFSETUP_TARGETFRAG);
            progressFrag.launch(modConfToApply, this.parent, elfManager);
            String modName = modemCtrl.getCurrentModemName();
            ExpertConfig.setExpertMode(modName, false);
        } catch (IOException e) {
            Log.e(TAG, MODULE + ": can't send sigusr1 signal " + e);
        } catch (ModemControlException ex) {
            Log.e(TAG, MODULE + " " + ex);
        } finally {
            modemCtrl = null;
        }
        AlogMarker.tAE("ModemTraces.stopModemTracing", "0");
    }

    public boolean isRunning() {
        AlogMarker.tAB("ModemTraces.isRunning", "0");
        AlogMarker.tAE("ModemTraces.isRunning", "0");
        return running;
    }

    private boolean isModemDumpEnabled() {
        /* as long as there is no special ui, always active */
        AlogMarker.tAB("ModemTraces.isModemDumpEnabled", "0");
        AlogMarker.tAE("ModemTraces.isModemDumpEnabled", "0");
        return true;
        /*updateConfiguration();
        return modemLog;*/
    }

    public void updateConfiguration() {
        AlogMarker.tAB("ModemTraces.updateConfiguration", "0");
        modemLog = (this.switchModem == null) ? false : switchModem.isChecked();
        AlogMarker.tAE("ModemTraces.updateConfiguration", "0");
    }

    public int getViewID() {
        AlogMarker.tAB("ModemTraces.getViewID", "0");
        AlogMarker.tAE("ModemTraces.getViewID", "0");
        return R.layout.trace_logcat;
    }

    public void attachReferences(View view) {
        AlogMarker.tAB("ModemTraces.attachReferences", "0");
        AlogMarker.tAE("ModemTraces.attachReferences", "0");
        //this.switchModem = (Switch) view.findViewById(R.id.modemDump);
    }

    public void attachListeners() {
        AlogMarker.tAB("ModemTraces.attachListeners", "0");
        AlogMarker.tAE("ModemTraces.attachListeners", "0");
    }

    private void setRunning(boolean enabled) {
        AlogMarker.tAB("ModemTraces.setRunning", "0");
        running = enabled;

        if (this.switchModem != null) {
            this.switchModem.setEnabled(!enabled);
        }
        AlogMarker.tAE("ModemTraces.setRunning", "0");
    }

    public String getLastStatus() {
        AlogMarker.tAB("ModemTraces.getLastStatus", "0");
        AlogMarker.tAE("ModemTraces.getLastStatus", "0");
        return lastStatus;
    }

    public String getTracerName() {
        AlogMarker.tAB("ModemTraces.getTracerName", "0");
        AlogMarker.tAE("ModemTraces.getTracerName", "0");
        return "Modem Traces";
    }
}
