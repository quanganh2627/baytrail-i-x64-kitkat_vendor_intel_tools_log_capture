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
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.Toast;

import com.intel.amtl.AMTLApplication;
import com.intel.amtl.StoredSettings;
import com.intel.amtl.models.config.ModemConf;
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

    private String OUTPUT_STORAGE_EMMC = "/logs";
    private String outputFile = OUTPUT_STORAGE_EMMC;
    private boolean outputSDCard = false;

    private boolean running = false;

    ActionMenu(Activity activity) {
        this.activity = activity;
        switchOutput();
    }

    public boolean start() {
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
        return true;
    }

    public void stop() {
        Log.d(TAG, MODULE + ": Stopping traces");
        for (GeneralTracing gc : tracers) {
            gc.stop();
        }
        setRunning(false);
    }

    public void cleanTemp() {

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
    }

    public void saveTemp(String path) {
        for (GeneralTracing gc : tracers) {
            running = gc.isRunning();
            gc.saveTemp(path);
            if (running) {
                gc.start();
            }
        }
    }

    private String save() {
        String path = null;

        Runnable ok = new Runnable() {
            @Override
            public void run() {
                String path = PreferenceManager.getDefaultSharedPreferences(activity).getString(
                        activity.getString(R.string.settings_user_save_path_key), "/logs/");
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
                activity.getString(R.string.settings_save_path_key), "/logs");
        path = FileOperations.getTimeStampedPath(path, "logs_");

        UIHelper.savePopupDialog(activity, "Save active logs",
                "Please select the path where the current logs should be stored!", path,
                activity, ok, cancel);

        return path;
    }

    public boolean isRunning() {
        return running;
    }

    private void updateOutputPath(boolean toSDCard) {
        String relativePath = getRelativeStorePath();

        if (toSDCard) {
            outputFile = FileOperations.getSDStoragePath();
        } else {
            outputFile = OUTPUT_STORAGE_EMMC;
        }

        if (relativePath != "") {
            outputFile += "/" + relativePath;
        }
    }

    private void refreshOutputPath() {
        outputSDCard = !outputSDCard;
        switchOutput();
    }

    public boolean switchOutput() {
        outputSDCard = !outputSDCard;

        if (outputSDCard) {
            if (!FileOperations.isSdCardAvailable()) {
                outputSDCard = false;
                return false;
            }
        }

        updateOutputPath(outputSDCard);

        return true;
    }

    private void setRunning(boolean enabled) {
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
    }

    public int getViewID() {
        return R.layout.action_menu;
    }

    public void attachReferences(View view) {
        this.buttonStart = (ImageButton) view.findViewById(R.id.buttonStart);
        this.buttonSave = (ImageButton) view.findViewById(R.id.buttonSave);
        this.buttonClear = (ImageButton) view.findViewById(R.id.buttonClear);
        setRunning(false);
    }

    public void attachListeners() {
        if (this.buttonStart != null)
            this.buttonStart.setOnClickListener(this);

        if (this.buttonSave != null)
            this.buttonSave.setOnClickListener(this);

        if (this.buttonClear != null)
            this.buttonClear.setOnClickListener(this);
    }

    public void onClick(View v) {
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
                return;
        }

        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(v.getContext(), text, Toast.LENGTH_SHORT);
        toast.show();
    }

    public void updateConfiguration() { }

    /******************* Configurations *****************/
    public static void addConfig(GeneralTracing tracer) {
        if (tracer != null) {
            tracers.add(tracer);
        }
    }

    public static ModemConf getModemConfiguration() {
        return AMTLTabLayout.getModemConfiguration();
    }

    private static String getRelativeStorePath() {
        StoredSettings privatePrefs = new StoredSettings(AMTLApplication.getContext());
        return privatePrefs.getRelativeStorePath();
    }

    public static String getLogcatTraceSize() {
        StoredSettings privatePrefs = new StoredSettings(AMTLApplication.getContext());
        return privatePrefs.getLogcatTraceSize();
    }

    public static String getLogcatFileCount() {
        StoredSettings privatePrefs = new StoredSettings(AMTLApplication.getContext());
        return privatePrefs.getLogcatFileCount();
    }

    public String getLastStatus() {
        return "";
    }

    public String getTracerName() {
        return "Action Menu";
    }
}
