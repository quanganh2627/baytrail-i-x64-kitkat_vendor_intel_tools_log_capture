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
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.intel.amtl.common.AMTLApplication;
import com.intel.amtl.R;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

public class LogcatTraces implements GeneralTracing, OnCheckedChangeListener {
    Process logcatProc = null;
    String TEMP = "logcat_amtl";
    String tempFile = FileOperations.TEMP_OUTPUT_FOLDER + TEMP;

    private final String TAG = "AMTL";
    private final String MODULE = "LogcatTraces";

    private Switch switchKernel = null;
    private Switch switchMain = null;
    private Switch switchRadio = null;
    private Switch switchEvent = null;
    private Switch switchSystem = null;

    private boolean kernelLog = true;
    private boolean mainLog = true;
    private boolean radioLog = true;
    private boolean eventLog = true;
    private boolean systemLog = true;

    private String logsCount = "5";
    private String logsSize = "16";

    private String lastStatus = "";

    private OnLogcatTraceModeApplied logcatSettingsCallBack = nullCb;

    public LogcatTraces(Activity activity) {
        logcatSettingsCallBack = (OnLogcatTraceModeApplied) activity;

        if (!FileOperations.pathExists("/dev/log/kernel")) {
            kernelLog = false;
        }
        if (!FileOperations.pathExists("/dev/log/main")) {
            mainLog = false;
        }
        if (!FileOperations.pathExists("/dev/log/radio")) {
            radioLog = false;
        }
        if (!FileOperations.pathExists("/dev/log/events")) {
            eventLog = false;
        }
        if (!FileOperations.pathExists("/dev/log/system")) {
            systemLog = false;
        }
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public boolean start() {

        String command;

        if (logcatProc != null) {
            lastStatus = "Logcat traces already running";
            return false;
        }

        prepare();

        if (!kernelLog && !mainLog && !radioLog && !eventLog && !systemLog) {
            lastStatus = "No logcat traces activated.";
            return false;
        }

        setRunning(true);
        command = "/system/bin/logcat";
        Editor editor = AMTLApplication.getContext().getSharedPreferences("AMTLPrefsData",
                Context.MODE_PRIVATE).edit();
        resetPreferences(editor);

        if (kernelLog) {
            command += " -b kernel";
            editor.putBoolean("kernel", true);
        }
        if (mainLog) {
            command += " -b main";
            editor.putBoolean("main", true);
        }
        if (radioLog) {
            command += " -b radio";
            editor.putBoolean("radio", true);
        }
        if (eventLog) {
            command += " -b events";
            editor.putBoolean("events", true);
        }
        if (systemLog) {
            command += " -b system";
            editor.putBoolean("system", true);
        }

        if (tempFile != null) {
            command += " -f " + tempFile;
            command += " -r " + logsSize;
        }

        editor.commit();
        command += " -n " + logsCount;
        command += " -v threadtime";

        try {
            Log.d(TAG, MODULE + ": executing following command: " + command);
            logcatProc = Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            Log.e(TAG, MODULE + ": Could not start logcat log");
        }

        lastStatus = "Logcat traces initialized ok";
        return true;
    }

    public void stop() {
        if (logcatProc != null) {
            logcatProc.destroy();
            logcatProc = null;
            setRunning(false);
            Editor editor = AMTLApplication.getContext().getSharedPreferences("AMTLPrefsData",
                    Context.MODE_PRIVATE).edit();
            resetPreferences(editor);
            editor.commit();
        }
    }

    private void resetPreferences(Editor edit) {
        edit.putBoolean("kernel", false);
        edit.putBoolean("main", false);
        edit.putBoolean("radio", false);
        edit.putBoolean("events", false);
        edit.putBoolean("system", false);
    }

    public void cleanTemp() {
        if (isRunning()) {
            stop();
        }

        FileOperations.removeFiles(FileOperations.TEMP_OUTPUT_FOLDER, TEMP);
    }

    public void saveTemp(String path) {
        if (isRunning()) {
            stop();
        }

        try {
            FileOperations.copyFiles(FileOperations.TEMP_OUTPUT_FOLDER, path, TEMP);
        } catch (IOException e) {
            Log.e(TAG, MODULE + ": Could not save logcat log");
        }
    }

    public boolean isRunning() {
        return (logcatProc != null);
    }

    public void updateConfiguration() {
        kernelLog = (this.switchKernel == null) ? false
                : ((switchKernel.getVisibility() == View.VISIBLE)
                ? switchKernel.isChecked() : false);
        mainLog = (this.switchMain == null) ? false : ((switchMain.getVisibility() == View.VISIBLE)
                ? switchMain.isChecked() : false);
        radioLog = (this.switchRadio == null) ? false
                : ((switchRadio.getVisibility() == View.VISIBLE) ? switchRadio.isChecked() : false);
        eventLog = (this.switchEvent == null) ? false
                : ((switchEvent.getVisibility() == View.VISIBLE) ? switchEvent.isChecked() : false);
        systemLog = (this.switchSystem == null) ? false
                : ((switchSystem.getVisibility() == View.VISIBLE)
                ? switchSystem.isChecked() : false);
    }

    public int getViewID() {
        return R.layout.trace_logcat;
    }

    public void attachReferences(View view) {
        this.switchKernel = (Switch) view.findViewById(R.id.logcatKernel);
        this.switchMain = (Switch) view.findViewById(R.id.logcatMain);
        this.switchRadio = (Switch) view.findViewById(R.id.logcatRadio);
        this.switchEvent = (Switch) view.findViewById(R.id.logcatEvent);
        this.switchSystem = (Switch) view.findViewById(R.id.logcatSystem);
    }

    public void attachListeners() {
        SharedPreferences prefs = AMTLApplication.getContext().getSharedPreferences("AMTLPrefsData",
                Context.MODE_PRIVATE);
        if (this.switchKernel != null) {
            this.switchKernel.setOnCheckedChangeListener(this);
            if (!FileOperations.pathExists("/dev/log/kernel")) {
                this.switchKernel.setVisibility(View.GONE);
            }
            this.switchKernel.setChecked(prefs.getBoolean("kernel", false));
        }
        if (this.switchMain != null) {
            this.switchMain.setOnCheckedChangeListener(this);
            if (!FileOperations.pathExists("/dev/log/main")) {
                this.switchMain.setVisibility(View.GONE);
            }
            this.switchMain.setChecked(prefs.getBoolean("main", false));
        }
        if (this.switchRadio != null) {
            this.switchRadio.setOnCheckedChangeListener(this);
            if (!FileOperations.pathExists("/dev/log/radio")) {
                this.switchRadio.setVisibility(View.GONE);
            }
            this.switchRadio.setChecked(prefs.getBoolean("radio", false));
        }
        if (this.switchEvent != null) {
            this.switchEvent.setOnCheckedChangeListener(this);
            if (!FileOperations.pathExists("/dev/log/events")) {
                this.switchEvent.setVisibility(View.GONE);
            }
            this.switchEvent.setChecked(prefs.getBoolean("events", false));
        }
        if (this.switchSystem != null) {
            this.switchSystem.setOnCheckedChangeListener(this);
            if (!FileOperations.pathExists("/dev/log/system")) {
                this.switchSystem.setVisibility(View.GONE);
            }
            this.switchSystem.setChecked(prefs.getBoolean("system", false));
        }
    }

    private void setRunning(boolean enabled) {
        enabled = false; //always true

        if (this.switchKernel != null) {
            this.switchKernel.setEnabled(!enabled);
        }
        if (this.switchMain != null) {
            this.switchMain.setEnabled(!enabled);
        }
        if (this.switchRadio != null) {
            this.switchRadio.setEnabled(!enabled);
        }
        if (this.switchEvent != null) {
            this.switchEvent.setEnabled(!enabled);
        }
        if (this.switchSystem != null) {
            this.switchSystem.setEnabled(!enabled);
        }
    }

    public void onCheckedChanged (CompoundButton v, boolean isChecked) {
        updateConfiguration();
        logcatSettingsCallBack.onLogcatTraceConfApplied(this);
    }

    public interface OnLogcatTraceModeApplied {
        public void onLogcatTraceConfApplied(GeneralTracing lt);
    }

    private static OnLogcatTraceModeApplied nullCb = new OnLogcatTraceModeApplied() {
        public void onLogcatTraceConfApplied(GeneralTracing lt) { }
    };

    //TO DO
    private void prepare() {
        logsCount = ActionMenu.getLogcatFileCount();
        logsSize = ActionMenu.getLogcatTraceSize();
    }

    public String getTracerName() {
        return "Logcat Traces";
    }
}
