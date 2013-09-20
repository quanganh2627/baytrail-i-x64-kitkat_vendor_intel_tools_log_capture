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

import com.intel.amtl.mts.MtsConf;

public class ModemConf {

    private static final String TAG = "AMTL";
    private static final String MODULE = "ModemConf";
    private String atXsio = "";
    private String atTrace = "";
    private String atXsystrace = "";
    private String mtsMode = "";
    private boolean mtsRequired = false;

    private LogOutput config = null;
    private MtsConf mtsConf = null;

    public ModemConf(LogOutput config) {
        this.config = config;
        this.atXsystrace = "AT+XSYSTRACE=1," + this.config.concatMasterPort();

        if (this.config.getXsio() != null) {

            this.atXsystrace += "," + this.config.concatMasterOption();
            this.atXsio = "AT+XSIO=" + this.config.getXsio() + "\r\n";

            if (this.config.getOct() != null) {
                this.atXsystrace += ",\"oct=";
                this.atXsystrace += this.config.getOct();
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
                    this.config.getMtsRotateSize());
        } else {
            this.mtsConf = new MtsConf();
        }
    }

    public ModemConf(String xsio, String trace, String xsystrace) {
        this.atTrace = trace;
        this.atXsio = xsio;
        this.atXsystrace = xsystrace;
        if (!this.atTrace.equals("AT+TRACE=1\r\n")) {
            this.mtsConf = new MtsConf();
        }
    }

    public void setMtsConf (MtsConf conf) {
        if (conf != null) {
            this.mtsConf = conf;
        }
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

    public void activateConf(boolean activate) {
        if (activate) {
            this.atTrace = "AT+TRACE=1\r\n";
        } else {
            this.atTrace = "AT+TRACE=0\r\n";
        }
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

    public void printMtsToLog() {
        if (this.mtsConf != null) {
            Log.d(TAG, MODULE + ": ========= MTS CONFIGURATION =========");
            Log.d(TAG, MODULE + ": INPUT = " + this.mtsConf.getInput());
            Log.d(TAG, MODULE + ": OUTPUT = " + this.mtsConf.getOutput());
            Log.d(TAG, MODULE + ": OUTPUT TYPE = " + this.mtsConf.getOutputType());
            Log.d(TAG, MODULE + ": ROTATE NUM = " + this.mtsConf.getRotateNum());
            Log.d(TAG, MODULE + ": ROTATE SIZE = " + this.mtsConf.getRotateSize());
            Log.d(TAG, MODULE + ": MODE = " + this.mtsMode);
            Log.d(TAG, MODULE + ": =======================================");
        }
    }

    public void printToLog() {
        Log.d(TAG, MODULE + ": ========= MODEM CONFIGURATION =========");
        Log.d(TAG, MODULE + ": XSIO = " + this.atXsio);
        Log.d(TAG, MODULE + ": TRACE = " + this.atTrace);
        Log.d(TAG, MODULE + ": XSYSTRACE = " + this.atXsystrace);
        Log.d(TAG, MODULE + ": =======================================");
    }

}
