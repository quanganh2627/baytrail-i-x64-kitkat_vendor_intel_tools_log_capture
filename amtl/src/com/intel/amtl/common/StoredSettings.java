/* AMTL
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

package com.intel.amtl.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

import com.intel.amtl.R;

public class StoredSettings {
    protected SharedPreferences appPrivatePrefs;
    private final SharedPreferences appSharedPrefs;
    protected Editor privatePrefsEditor;
    private final Editor sharedPrefsEditor;
    private final Context mCtx;

    public StoredSettings(Context context) {
        this.mCtx = context;
        this.appPrivatePrefs = context.getSharedPreferences("amtlPrivatePreferences",
                Context.MODE_PRIVATE);
        this.privatePrefsEditor = appPrivatePrefs.edit();
        this.appSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.sharedPrefsEditor = appSharedPrefs.edit();
    }

    public String getLogcatFileCount() {
        return appSharedPrefs.getString(
                mCtx.getString(R.string.settings_logcat_file_count_key),
                mCtx.getString(R.string.settings_logcat_file_count_default));
    }

    public String getLogcatTraceSize() {
        return appSharedPrefs.getString(
                mCtx.getString(R.string.settings_logcat_size_key),
                mCtx.getString(R.string.settings_logcat_size_default));
    }

    public String getRelativeStorePath() {
        return appSharedPrefs.getString(
                mCtx.getString(R.string.settings_save_path_key),
                mCtx.getString(R.string.settings_save_path_default));
    }
}
