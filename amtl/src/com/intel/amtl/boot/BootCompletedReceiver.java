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
 */

package com.android.amtl.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.intel.amtl.R;
import com.intel.amtl.gui.FileOperations;

import java.io.IOException;


public class BootCompletedReceiver extends BroadcastReceiver {

    private final String TAG = "AMTL";
    private final String MODULE = "BootCompletedReceiver";
    private final String TEMP = "logcat_amtl";
    private String tempFile = FileOperations.TEMP_OUTPUT_FOLDER + TEMP;
    private String logsCount = "5";
    private String logsSize = "16";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.d(TAG, MODULE + ": BOOT COMPLETED");

            String command = "/system/bin/logcat";

            SharedPreferences.Editor editor = context.getSharedPreferences("AMTLPrefsData",
                    Context.MODE_PRIVATE).edit();

            SharedPreferences prefs = context.getSharedPreferences("AMTLPrefsData",
                    Context.MODE_PRIVATE);

            if (null == editor || null == prefs) {
                return;
            }

            if (!FileOperations.pathExists("/dev/log/kernel")) {
                editor.putBoolean("kernel", false);
            } else {
                if (prefs.getBoolean("kernel", false)) {
                    command += " -b kernel";
                }
            }
            if (!FileOperations.pathExists("/dev/log/main")) {
                editor.putBoolean("main", false);
            } else {
                if (prefs.getBoolean("main", false)) {
                    command += " -b main";
                }
            }
            if (!FileOperations.pathExists("/dev/log/radio")) {
                editor.putBoolean("radio", false);
            } else {
                if (prefs.getBoolean("radio", false)) {
                    command += " -b radio";
                }
            }
            if (!FileOperations.pathExists("/dev/log/events")) {
                editor.putBoolean("events", false);
            } else {
                if (prefs.getBoolean("events", false)) {
                    command += " -b events";
                }
            }
            if (!FileOperations.pathExists("/dev/log/system")) {
                editor.putBoolean("system", false);
            } else {
                if (prefs.getBoolean("system", false)) {
                    command += " -b system";
                }
            }

            editor.commit();

            if (command.equals("/system/bin/logcat")) {
                return;
            }

            SharedPreferences appSharedPrefs = PreferenceManager
                    .getDefaultSharedPreferences(context);
            if (tempFile != null) {
                command += " -f " + tempFile;

                if (null == appSharedPrefs) {
                    command += " -r " + logsSize;
                } else {
                    command += " -r " + appSharedPrefs.getString(
                            context.getString(R.string.settings_logcat_size_key),
                            context.getString(R.string.settings_logcat_size_default));
                }
            }

            if (null == appSharedPrefs) {
                command += " -n " + logsCount;
            } else {
                command += " -n " + appSharedPrefs.getString(
                        context.getString(R.string.settings_logcat_file_count_key),
                        context.getString(R.string.settings_logcat_file_count_default));
            }
            command += " -v threadtime";

            try {
                Log.d(TAG, MODULE + ": executing following command: " + command);
                Runtime.getRuntime().exec(command);
            } catch (IOException e) {
                Log.e(TAG, MODULE + ": Could not start logcat log");
            }
        }
    }
}
