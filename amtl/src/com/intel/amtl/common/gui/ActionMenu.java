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
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.Toast;

import com.intel.amtl.common.AMTLApplication;
import com.intel.amtl.common.log.AlogMarker;
import com.intel.amtl.common.models.config.ModemConf;
import com.intel.amtl.common.StoredSettings;
import com.intel.amtl.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ActionMenu implements OnClickListener, GeneralTracing {

    private final String TAG = "AMTL";
    private final String MODULE = "ActionMenu";

    static List < GeneralTracing > tracers = new ArrayList < GeneralTracing > ();
    private Activity activity = null;
    Toast toast = null;

/*
1. Stop all of the logs
2. Start all of the logs (except the TCP log)
3. Save logs to output folder
4. Clear all unsaved logs
5. Switch the default log output and buffering path to removable SD card.
*/
    Process logcatProc = null;

    private ImageButton buttonStart = null;
    private ImageButton buttonSave = null;
    private ImageButton buttonClear = null;

    private final String OUTPUT_STORAGE_EMMC = AMTLApplication.getApLoggingPath();
    private String outputFile = OUTPUT_STORAGE_EMMC;
    private boolean outputSDCard = false;

    private boolean running = false;

    public ActionMenu(Activity activity) {
        AlogMarker.tAB("ActionMenu.ActionMenu", "0");
        this.activity = activity;
        switchOutput();
        AlogMarker.tAE("ActionMenu.ActionMenu", "0");
    }

    public boolean start() {
        AlogMarker.tAB("ActionMenu.start", "0");
        String status = "";
        tracers = AMTLTabLayout.getActiveTraces();
        int issues = 0;

        Log.d(TAG, MODULE + ": Starting traces");
        for (GeneralTracing gc : tracers) {
            status += gc.getTracerName() + ": ";
            if (!gc.start()) {
                status += "not ";
                issues++;
            }
            status += "started\n";
        }

        setRunning(issues != tracers.size());

        UIHelper.okDialog(activity, "Status ...", status);
        AlogMarker.tAE("ActionMenu.start", "0");
        return true;
    }

    public void stop() {
        AlogMarker.tAB("ActionMenu.stop", "0");
        Log.d(TAG, MODULE + ": Stopping traces");
        for (GeneralTracing gc : tracers) {
            gc.stop();
        }
        setRunning(false);
        AlogMarker.tAE("ActionMenu.stop", "0");
    }

    public void cleanTemp() {
        AlogMarker.tAB("ActionMenu.cleanTemp", "0");

        Runnable ok = new Runnable() {
            @Override
            public void run() {
                boolean running = false;
                for (GeneralTracing gc : tracers) {
                    running = gc.isRunning();
                    gc.cleanTemp();
                    if (running) {
                        gc.start();
                    }
                }
            }
        };

        Runnable cancel = new Runnable() {
            @Override
            public void run() {
                //nothing to do
            }
        };

        UIHelper.cleanPopupDialog(activity, "Clean temp files",
                "Are you sure you want to delete these files", ok, cancel);
        AlogMarker.tAE("ActionMenu.cleanTemp", "0");
    }

    public void saveTemp(String path) {
        AlogMarker.tAB("ActionMenu.saveTemp", "0");
        for (GeneralTracing gc : tracers) {
            running = gc.isRunning();
            gc.saveTemp(path);
            if (running) {
                gc.start();
            }
        }
        AlogMarker.tAE("ActionMenu.saveTemp", "0");
    }

    private String save() {
        AlogMarker.tAB("ActionMenu.save", "0");
        String path = null;

        Runnable ok = new Runnable() {
            @Override
            public void run() {
                String path = PreferenceManager.getDefaultSharedPreferences(activity).getString(
                        activity.getString(R.string.settings_user_save_path_key),
                        AMTLApplication.getApLoggingPath() + "/");
                boolean pathExists = false;

                try {
                    pathExists = FileOperations.createPath(path);
                } catch (IOException e) {
                    Log.e(TAG, MODULE + ": Error while create saving path: " + e);
                }

                if (pathExists) {
                    Toast toast = Toast.makeText(activity, "Saving to " + path, Toast.LENGTH_SHORT);
                    toast.show();
                    Log.d(TAG, MODULE + ": User action for saving logs to: " + path);
                    saveTemp(path);
                } else {
                    Toast toast = Toast.makeText(activity, "Not saved! \n" +
                            "Path could not be created.", Toast.LENGTH_SHORT);
                    Log.e(TAG, MODULE + ": Not saved! \nPath could not be created." + path);
                    toast.show();
                }
            }
        };

        Runnable cancel = new Runnable() {
            @Override
            public void run() {
                //nothing to do
            }
        };

        path = PreferenceManager.getDefaultSharedPreferences(activity).getString(
                activity.getString(R.string.settings_save_path_key),
                AMTLApplication.getApLoggingPath());
        path = FileOperations.getTimeStampedPath(path, "logs_");

        UIHelper.savePopupDialog(activity, "Save active logs",
                "Please select the path where the current logs should be stored!", path,
                activity, ok, cancel);

        AlogMarker.tAE("ActionMenu.save", "0");
        return path;
    }

    public boolean isRunning() {
        AlogMarker.tAB("ActionMenu.isRunning", "0");
        AlogMarker.tAE("ActionMenu.isRunning", "0");
        return running;
    }

    private void updateOutputPath(boolean toSDCard) {
        AlogMarker.tAB("ActionMenu.updateOutputPath", "0");
        String relativePath = getRelativeStorePath();

        if (toSDCard) {
            outputFile = FileOperations.getSDStoragePath();
        } else {
            outputFile = OUTPUT_STORAGE_EMMC;
        }

        if (relativePath != "") {
            outputFile += "/" + relativePath;
        }
        AlogMarker.tAE("ActionMenu.updateOutputPath", "0");
    }

    private void refreshOutputPath() {
        AlogMarker.tAB("ActionMenu.refreshOutputPath", "0");
        outputSDCard = !outputSDCard;
        switchOutput();
        AlogMarker.tAE("ActionMenu.refreshOutputPath", "0");
    }

    public boolean switchOutput() {
        AlogMarker.tAB("ActionMenu.switchOutput", "0");
        outputSDCard = !outputSDCard;

        if (outputSDCard) {
            if (!FileOperations.isSdCardAvailable()) {
                outputSDCard = false;
                AlogMarker.tAE("ActionMenu.switchOutput", "0");
                return false;
            }
        }

        updateOutputPath(outputSDCard);
        AlogMarker.tAE("ActionMenu.switchOutput", "0");

        return true;
    }

    private void setRunning(boolean enabled) {
        AlogMarker.tAB("ActionMenu.setRunning", "0");
        running = enabled;

        if (this.buttonStart != null) {
            if (enabled) {
                this.buttonStart.setImageDrawable(activity.getResources()
                        .getDrawable(R.drawable.stop_button));
            } else {
                this.buttonStart.setImageDrawable(activity.getResources()
                        .getDrawable(R.drawable.start_button));
            }
        }
        AlogMarker.tAE("ActionMenu.setRunning", "0");
    }

    public int getViewID() {
        AlogMarker.tAB("ActionMenu.getViewID", "0");
        AlogMarker.tAE("ActionMenu.getViewID", "0");
        return R.layout.action_menu;
    }

    public void attachReferences(View view) {
        AlogMarker.tAB("ActionMenu.attachReferences", "0");
        this.buttonStart = (ImageButton) view.findViewById(R.id.buttonStart);
        this.buttonSave = (ImageButton) view.findViewById(R.id.buttonSave);
        this.buttonClear = (ImageButton) view.findViewById(R.id.buttonClear);
        setRunning(false);
        AlogMarker.tAE("ActionMenu.attachReferences", "0");
    }

    public void attachListeners() {
        AlogMarker.tAB("ActionMenu.attachListeners", "0");
        if (this.buttonStart != null)
            this.buttonStart.setOnClickListener(this);

        if (this.buttonSave != null)
            this.buttonSave.setOnClickListener(this);

        if (this.buttonClear != null)
            this.buttonClear.setOnClickListener(this);
        AlogMarker.tAE("ActionMenu.attachListeners", "0");
    }

    public void onClick(View v) {
        AlogMarker.tAB("ActionMenu.onClick", "0");
        CharSequence text;

        switch (v.getId()) {
            case R.id.buttonStart:
                if (isRunning()) {
                    text = "Stop tracing";
                    stop();
                } else {
                    text = "Start tracing";
                    start();
                }
                break;
            case R.id.buttonSave:
                if (toast != null) {
                    toast.cancel();
                }
                toast = Toast.makeText(v.getContext(), "Saving ...", Toast.LENGTH_SHORT);
                String path = save();
                return;
            case R.id.buttonClear:
                text = "Clear temporary files";
                cleanTemp();
                break;
            default:
                AlogMarker.tAE("ActionMenu.onClick", "0");
                return;
        }

        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(v.getContext(), text, Toast.LENGTH_SHORT);
        toast.show();
        AlogMarker.tAE("ActionMenu.onClick", "0");
    }

    public void updateConfiguration() { }

    /******************* Configurations *****************/
    public static void addConfig(GeneralTracing tracer) {
        AlogMarker.tAB("ActionMenu.addConfig", "0");
        if (tracer != null) {
            tracers.add(tracer);
        }
        AlogMarker.tAE("ActionMenu.addConfig", "0");
    }

    public static ModemConf getModemConfiguration() {
        AlogMarker.tAB("ActionMenu.getModemConfiguration", "0");
        AlogMarker.tAE("ActionMenu.getModemConfiguration", "0");
        return AMTLTabLayout.getModemConfiguration();
    }

    private static String getRelativeStorePath() {
        AlogMarker.tAB("ActionMenu.getRelativeStorePath", "0");
        StoredSettings privatePrefs = new StoredSettings(AMTLApplication.getContext());
        AlogMarker.tAE("ActionMenu.getRelativeStorePath", "0");
        return privatePrefs.getRelativeStorePath();
    }

    public static String getLogcatTraceSize() {
        AlogMarker.tAB("ActionMenu.getLogcatTraceSize", "0");
        StoredSettings privatePrefs = new StoredSettings(AMTLApplication.getContext());
        AlogMarker.tAE("ActionMenu.getLogcatTraceSize", "0");
        return privatePrefs.getLogcatTraceSize();
    }

    public static String getLogcatFileCount() {
        AlogMarker.tAB("ActionMenu.getLogcatFileCount", "0");
        StoredSettings privatePrefs = new StoredSettings(AMTLApplication.getContext());
        AlogMarker.tAE("ActionMenu.getLogcatFileCount", "0");
        return privatePrefs.getLogcatFileCount();
    }

    public String getLastStatus() {
        AlogMarker.tAB("ActionMenu.getLastStatus", "0");
        AlogMarker.tAE("ActionMenu.getLastStatus", "0");
        return "";
    }

    public String getTracerName() {
        AlogMarker.tAB("ActionMenu.getTracerName", "0");
        AlogMarker.tAE("ActionMenu.getTracerName", "0");
        return "Action Menu";
    }
}
