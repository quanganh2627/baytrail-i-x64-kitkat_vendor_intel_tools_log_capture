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
 * Author: Morgane Butscher <morganeX.butscher@intel.com>
 * Author: Erwan Bracq <erwan.bracq@intel.com>
 */
package com.intel.amtl.platform;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileInputStream;

public class Platform {

    private final String TAG = "AMTL";
    private final String MODULE = "Platform";
    private final String CPU_FILE = "/sys/kernel/telephony/cpu_name";
    private final String MODEM_FILE = "/sys/kernel/telephony/modem_name";
    private String cpuName = "";
    private String modemName = "";
    private String catalogPath = "/system/etc/amtl/catalog/";

    public Platform(Context context) {
        getCpuName();
        getModemName();
        // Store modem and cpu names
        SharedPreferences.Editor editor =
                 context.getSharedPreferences("AMTLPrefsData",
                 Context.MODE_PRIVATE).edit();
        editor.putString("cpuName", this.cpuName);
        editor.putString("modemName", this.modemName);
        editor.commit();
    }

    public String getPlatformConf() {
        return catalogPath + cpuName + "_" + modemName + ".cfg";
    }

    private void getModemName() {
        InputStream inStream = null;
        try {
            inStream = new FileInputStream(MODEM_FILE);
            if (inStream != null) {
                byte[] b = new byte[inStream.available()];
                inStream.read(b);
                String name = new String(b);
                this.modemName = name.substring(0, name.indexOf("\n"));
                Log.d(TAG, MODULE + ": Identified cpu name: " + this.modemName);
            }
        } catch (FileNotFoundException ex) {
            Log.e(TAG, MODULE + ": " + MODEM_FILE + " has not been found" + ex);
        } catch (IOException ex) {
            Log.e(TAG, MODULE + ": Unable to read file " + MODEM_FILE + " " + ex);
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException ex) {
                    Log.e(TAG, MODULE + ": Error during close " + ex);
                }
            }
        }
    }

    private void getCpuName() {
        InputStream inStream = null;
        try {
            inStream = new FileInputStream(CPU_FILE);
            if (inStream != null) {
                byte[] b = new byte[inStream.available()];
                inStream.read(b);
                String name = new String(b);
                this.cpuName = name.substring(0, name.indexOf("\n"));
                Log.d(TAG, MODULE + ": Identified cpu name: " + this.cpuName);
            }
        } catch (FileNotFoundException ex) {
            Log.e(TAG, MODULE + ": " + CPU_FILE + " has not been found" + ex);
        } catch (IOException ex) {
            Log.e(TAG, MODULE + ": Unable to read file " + CPU_FILE + " " + ex);
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException ex) {
                    Log.e(TAG, MODULE + ": Error during close " + ex);
                }
            }
        }
    }
}
