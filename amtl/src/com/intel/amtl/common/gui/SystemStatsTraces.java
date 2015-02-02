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
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.intel.amtl.common.log.AlogMarker;
import com.intel.amtl.R;

import java.io.IOException;
import java.util.ArrayList;

public class SystemStatsTraces implements GeneralTracing, OnCheckedChangeListener {
    PeriodicTask task = null;
    SysLogger[] loggers;
    String memInfoFile = FileOperations.TEMP_OUTPUT_FOLDER + "MemInfo_amtl";
    String procrankFile = FileOperations.TEMP_OUTPUT_FOLDER + "Procrank_amtl";
    String topLogFile = FileOperations.TEMP_OUTPUT_FOLDER + "TopLog_amtl";
    String cpuLoadFile = FileOperations.TEMP_OUTPUT_FOLDER + "CpuLoad_amtl";

    private final String TAG = "AMTL";
    private final String MODULE = "SystemStatsTraces";

    private Switch switchMemInfo = null;
    private Switch switchProcrank = null;
    private Switch switchTopLog = null;
    private Switch switchCpuLoad = null;

    private boolean memInfoLog = true;
    private boolean procrankLog = true;
    private boolean topLogLog = true;
    private boolean cpuLoadLog = true;

    /* frequency in seconds */
    private int memInfoRunFreq = 5;
    private int procrankRunFreq = 5;
    private int topLogRunFreq = 5;
    private int cpuLoadRunFreq = 1;

    private String lastStatus = "";
    private boolean running = false;

    private OnSystemStatsTraceModeApplied systemStatsCallBack = nullCb;

    public SystemStatsTraces(Activity activity) {
        AlogMarker.tAB("SystemStatsTraces.SystemStatsTraces", "0");
        systemStatsCallBack = (OnSystemStatsTraceModeApplied) activity;

        loggers = new SysLogger[] {
            new SysLogger() {
                public void log(long time) {
                    if (time%memInfoRunFreq != 0) {
                        return;
                    }
                    if (memInfoLog) {
                        outputMemoryInfo();
                    }
                }
            },
            new SysLogger() {
                public void log(long time) {
                    if (time%procrankRunFreq != 0) {
                        return;
                    }
                    if (procrankLog) {
                        outputProcrank();
                    }
                 }
            },
            new SysLogger() {
                public void log(long time) {
                    if (time%topLogRunFreq != 0) {
                        return;
                    }
                    if (topLogLog) {
                        outputTopLog();
                    }
                }
            },
            new SysLogger() {
                public void log(long time) {
                    if (time%cpuLoadRunFreq != 0) {
                        return;
                    }
                    if (cpuLoadLog) {
                        outputCPULoading();
                    }
                }
            }
        };
        AlogMarker.tAE("SystemStatsTraces.SystemStatsTraces", "0");
    }

    public String getLastStatus() {
        AlogMarker.tAB("SystemStatsTraces.getLastStatus", "0");
        AlogMarker.tAE("SystemStatsTraces.getLastStatus", "0");
        return lastStatus;
    }

    public interface SysLogger {
        public void log(long time);
    }

    private class PeriodicTask extends AsyncTask<Void, Void, Void> {
        private AlogMarker m = new AlogMarker();
        protected Void doInBackground(Void... params) {
            AlogMarker.tAB("SystemStatsTraces.PeriodicTask.doInBackground", "0");
            long iteration = 0;
            while (true) {
                for (SysLogger logger : loggers) {
                    logger.log(iteration);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                iteration++;

                if (isCancelled()) {
                    break;
                }
            }
            Log.d(TAG, MODULE + ": PeriodicTask stopped");
            AlogMarker.tAE("SystemStatsTraces.PeriodicTask.doInBackground", "0");
            return null;
        }

        protected void onProgressUpdate(Integer... progress) {}
        protected void onPostExecute(Long result) {}
    }

    private void outputMemoryInfo() {
        AlogMarker.tAB("SystemStatsTraces.outputMemoryInfo", "0");

        String command = "/system/bin/cat /proc/meminfo >> " + memInfoFile;
        try {
            Runtime.getRuntime().exec(command);
        }
        catch (IOException e) {
            Log.e(TAG, MODULE + ": Command failed: " + command + "\n" + e);
        }
        AlogMarker.tAE("SystemStatsTraces.outputMemoryInfo", "0");
    }

    private void outputProcrank() {
        AlogMarker.tAB("SystemStatsTraces.outputProcrank", "0");

        String command = "/system/xbin/procrank >> " + procrankFile;
        try {
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            Log.e(TAG, MODULE + ": Command failed: " + command + "\n" + e);
        }
        AlogMarker.tAE("SystemStatsTraces.outputProcrank", "0");
    }

    private void outputTopLog() {
        AlogMarker.tAB("SystemStatsTraces.outputTopLog", "0");

        String command = "/system/bin/top -n 1 >> " + topLogFile;
        try {
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            Log.e(TAG, MODULE + ": Command failed: " + command + "\n" + e);
        }
        AlogMarker.tAE("SystemStatsTraces.outputTopLog", "0");
    }

    private void outputCPULoading() {
        AlogMarker.tAB("SystemStatsTraces.outputCPULoading", "0");

        String command = "/system/bin/top -n 1 -s cpu >> " + cpuLoadFile;
        try {
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            Log.e(TAG, MODULE + ": Command failed: " + command + "\n" + e);
        }
        AlogMarker.tAE("SystemStatsTraces.outputCPULoading", "0");
    }

    public boolean start() {
        AlogMarker.tAB("SystemStatsTraces.start", "0");

        String command;
        if (memInfoLog || procrankLog || topLogLog || cpuLoadLog) {}
        else {
            setRunning(false);
            lastStatus = "SystemStats traces not started";
            AlogMarker.tAE("SystemStatsTraces.start", "0");
            return false;
        }

        task = new PeriodicTask();
        task.execute();
        setRunning(true);

        lastStatus = "SystemStats traces initialized ok";
        AlogMarker.tAE("SystemStatsTraces.start", "0");
        return true;
    }

    public void stop() {
        AlogMarker.tAB("SystemStatsTraces.stop", "0");
        if (task != null) {
            task.cancel(true);
            task = null;
            setRunning(false);
        }
        AlogMarker.tAE("SystemStatsTraces.stop", "0");
    }

    public void cleanTemp() {
        AlogMarker.tAB("SystemStatsTraces.cleanTemp", "0");
        if (isRunning()) {
            stop();
        }

        FileOperations.removeFile(memInfoFile);
        FileOperations.removeFile(procrankFile);
        FileOperations.removeFile(topLogFile);
        FileOperations.removeFile(cpuLoadFile);
        AlogMarker.tAE("SystemStatsTraces.cleanTemp", "0");
    }

    public void saveTemp(String path) {
        AlogMarker.tAB("SystemStatsTraces.saveTemp", "0");
        if (isRunning()) {
            stop();
        }

        try {
            FileOperations.copy(memInfoFile, path + "MemInfo");
        } catch (IOException e) {
            Log.e(TAG, MODULE + ": Could not save MemInfo");
        }
        try {
            FileOperations.copy(procrankFile, path + "Procrank");
        } catch (IOException e) {
            Log.e(TAG, MODULE + ": Could not save Procrank");
        }
        try {
            FileOperations.copy(topLogFile, path + "MemInfo");
        } catch (IOException e) {
            Log.e(TAG, MODULE + ": Could not save TopLog");
        }
        try {
            FileOperations.copy(cpuLoadFile, path + "MemInfo");
        } catch (IOException e) {
            Log.e(TAG, MODULE + ": Could not save CpuLoad");
        }
        AlogMarker.tAE("SystemStatsTraces.saveTemp", "0");
    }

    public boolean isRunning() {
        AlogMarker.tAB("SystemStatsTraces.isRunning", "0");
        AlogMarker.tAE("SystemStatsTraces.isRunning", "0");
        return running;
    }

    public void updateConfiguration() {
        AlogMarker.tAB("SystemStatsTraces.updateConfiguration", "0");
        memInfoLog = (this.switchMemInfo == null) ? false : switchMemInfo.isChecked();
        procrankLog = (this.switchProcrank == null) ? false : switchProcrank.isChecked();
        topLogLog = (this.switchTopLog == null) ? false : switchTopLog.isChecked();
        cpuLoadLog = (this.switchCpuLoad == null) ? false : switchCpuLoad.isChecked();
        AlogMarker.tAE("SystemStatsTraces.updateConfiguration", "0");
    }

    public int getViewID() {
        AlogMarker.tAB("SystemStatsTraces.getViewID", "0");
        AlogMarker.tAE("SystemStatsTraces.getViewID", "0");
        return R.layout.trace_sysstats;
    }

    public void attachReferences(View view) {
        AlogMarker.tAB("SystemStatsTraces.attachReferences", "0");
        this.switchMemInfo = (Switch) view.findViewById(R.id.sysStatsMemInfo);
        this.switchProcrank = (Switch) view.findViewById(R.id.sysStatsProcrank);
        this.switchTopLog = (Switch) view.findViewById(R.id.sysStatsTopLog);
        this.switchCpuLoad = (Switch) view.findViewById(R.id.sysStatsCpuLoad);
        AlogMarker.tAE("SystemStatsTraces.attachReferences", "0");
    }

    public void attachListeners() {
        AlogMarker.tAB("SystemStatsTraces.attachListeners", "0");
        if (this.switchMemInfo != null) {
            this.switchMemInfo.setOnCheckedChangeListener (this);
        }
        if (this.switchProcrank != null) {
            this.switchProcrank.setOnCheckedChangeListener (this);
        }
        if (this.switchTopLog != null) {
            this.switchTopLog.setOnCheckedChangeListener (this);
        }
        if (this.switchCpuLoad != null) {
            this.switchCpuLoad.setOnCheckedChangeListener (this);
        }
        AlogMarker.tAE("SystemStatsTraces.attachListeners", "0");
    }

    private void setRunning(boolean enabled) {
        AlogMarker.tAB("SystemStatsTraces.setRunning", "0");
        running = enabled;
        AlogMarker.tAE("SystemStatsTraces.setRunning", "0");
    }

    public void onCheckedChanged (CompoundButton v, boolean isChecked) {
        AlogMarker.tAB("SystemStatsTraces.onCheckedChanged", "0");
        updateConfiguration();
        systemStatsCallBack.onSystemStatsTraceConfApplied(this);
        AlogMarker.tAE("SystemStatsTraces.onCheckedChanged", "0");
    }

    public interface OnSystemStatsTraceModeApplied {
        public void onSystemStatsTraceConfApplied(GeneralTracing lt);
    }

    private static OnSystemStatsTraceModeApplied nullCb = new OnSystemStatsTraceModeApplied() {
        public void onSystemStatsTraceConfApplied(GeneralTracing lt) { }
    };

    public String getTracerName() {
        return "SystemStats Traces";
    }
}
