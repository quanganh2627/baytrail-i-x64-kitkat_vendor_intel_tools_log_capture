/* Android AMTL
 *
 * Copyright (C) Intel 2013
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
 * Author: Erwan Bracq <erwan.bracq@intel.com>
 * Author: Morgane Butscher <morganeX.butscher@intel.com>
 */

package com.intel.amtl.models.config;

import android.util.Log;

import com.intel.amtl.AMTLApplication;
import com.intel.amtl.mts.MtsConf;

public abstract class ModemConf {

    private static final String TAG = "AMTL";
    private static final String MODULE = "ModemConf";
    private String atXsio = "";
    private String atXsystrace = "";
    private String mtsMode = "";
    private String flCmd = "";
    private String octMode = "";
    private boolean mtsRequired = false;
    private int confIndex = -1;

    private LogOutput config = null;
    private MtsConf mtsConf = null;

    protected String atTrace = "";

    public abstract boolean confTraceEnabled();
    public abstract void activateConf(boolean activate);

    public synchronized static ModemConf getInstance(LogOutput config) {
        return (AMTLApplication.getTraceLegacy())
                ? new TraceLegacyModemConf(config) : new OctModemConf(config);
    }

    public synchronized static ModemConf getInstance(String xsio, String trace, String xsystrace,
            String flcmd, String octMode) {
        return (AMTLApplication.getTraceLegacy())
                ? new TraceLegacyModemConf(xsio, trace, xsystrace, flcmd, octMode)
                : new OctModemConf(xsio, trace, xsystrace, flcmd, octMode);
    }

    public ModemConf(LogOutput config) {
        this.config = config;
        this.confIndex = this.config.getIndex();
        this.atXsystrace = "AT+XSYSTRACE=1," + this.config.concatMasterPort();

        if (this.config.getFlCmd() != null) {
            this.flCmd = this.config.getFlCmd() + "\r\n";
        }
        if (this.config.getXsio() != null) {

            this.atXsystrace += "," + this.config.concatMasterOption();
            this.atXsio = "AT+XSIO=" + this.config.getXsio() + "\r\n";

            if (this.config.getOct() != null || this.config.getOctFcs() != null) {
                this.atXsystrace += ",\"";
                if (this.config.getOct() != null) {
                    this.atXsystrace += "oct=";
                    this.atXsystrace += this.config.getOct();
                }
                if (this.config.getOctFcs() != null) {
                    if (this.config.getOct() != null) {
                        this.atXsystrace += ";";
                    }
                    this.atXsystrace += "oct_fcs=";
                    this.atXsystrace += this.config.getOctFcs();
                }
                this.atXsystrace += "\"";
            }
            if (this.config.getPti1() != null) {
                this.atXsystrace += ",\"pti1=";
                this.atXsystrace += this.config.getPti1();
                this.atXsystrace += "\"";
            }
            if (this.config.getPti2() != null) {
                this.atXsystrace += ",\"pti2=";
                this.atXsystrace += this.config.getPti2();
                this.atXsystrace += "\"";
            }
        }
        this.atXsystrace += "\r\n";
        if (!this.atXsio.equals("")) {
            this.mtsMode = this.config.getMtsMode();
            this.mtsConf = new MtsConf(this.config.getMtsInput(), this.config.getMtsOutput(),
                    this.config.getMtsOutputType(), this.config.getMtsRotateNum(),
                    this.config.getMtsRotateSize(), this.config.getMtsInterface(),
                    this.config.getMtsBufferSize());
        } else {
            this.mtsConf = new MtsConf();
        }
    }

    public ModemConf(String xsio, String trace, String xsystrace, String flcmd, String octMode) {
        this.atTrace = trace;
        this.atXsio = xsio;
        this.atXsystrace = xsystrace;
        this.flCmd = flcmd;
        this.confIndex = -1;
        this.mtsConf = new MtsConf();
        if (!octMode.equals("")) {
            this.octMode = octMode;
        }
    }

    public void setMtsConf (MtsConf conf) {
        if (conf != null) {
            this.mtsConf = conf;
        }
    }

    public MtsConf getMtsConf () {
        return this.mtsConf;
    }

    public String getXsio() {
        return this.atXsio;
    }

    public String getTrace() {
        return this.atTrace;
    }

    public String getXsystrace() {
        return this.atXsystrace;
    }

    public String getFlCmd() {
        return this.flCmd;
    }

    public int getIndex() {
        return this.confIndex;
    }

    public void setIndex(int index) {
        this.confIndex = index;
    }

    public String getMtsMode() {
        return this.mtsMode;
    }

    public void setMtsMode(String mode) {
        this.mtsMode = mode;
    }

    public void applyMtsParameters() {
        if (this.mtsConf != null) {
            this.mtsConf.applyParameters();
            printMtsToLog();
        }
    }

    public boolean isMtsRequired() {
        if (this.mtsConf != null) {
           return (!this.mtsConf.getInput().equals("") && !this.mtsConf.getOutput().equals("")
               && !this.mtsConf.getOutputType().equals("") && !getMtsMode().equals(""));
        }
        return false;
    }

    public void setOctMode(String octMode) {
        this.octMode = octMode;
    }

    public String getOctMode() {
        return this.octMode;
    }

    public void printMtsToLog() {
        if (this.mtsConf != null) {
            Log.d(TAG, MODULE + ": ========= MTS CONFIGURATION =========");
            Log.d(TAG, MODULE + ": INPUT = " + this.mtsConf.getInput());
            Log.d(TAG, MODULE + ": OUTPUT = " + this.mtsConf.getOutput());
            Log.d(TAG, MODULE + ": OUTPUT TYPE = " + this.mtsConf.getOutputType());
            Log.d(TAG, MODULE + ": ROTATE NUM = " + this.mtsConf.getRotateNum());
            Log.d(TAG, MODULE + ": ROTATE SIZE = " + this.mtsConf.getRotateSize());
            Log.d(TAG, MODULE + ": INTERFACE = " + this.mtsConf.getInterface());
            Log.d(TAG, MODULE + ": BUFFER SIZE = " + this.mtsConf.getBufferSize());
            Log.d(TAG, MODULE + ": MODE = " + this.mtsMode);
            Log.d(TAG, MODULE + ": =======================================");
        }
    }

    public void printToLog() {
        Log.d(TAG, MODULE + ": ========= MODEM CONFIGURATION =========");
        Log.d(TAG, MODULE + ": XSIO = " + this.atXsio);
        Log.d(TAG, MODULE + ": TRACE = " + this.atTrace);
        Log.d(TAG, MODULE + ": XSYSTRACE = " + this.atXsystrace);
        Log.d(TAG, MODULE + ": OCT = " + this.octMode);
        Log.d(TAG, MODULE + ": =======================================");
    }

}
