/*
 * Copyright (C) 2013 Intel Corporation, All rights Reserved.
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
 */

package com.intel.internal.telephony.TelephonyEventsNotifier;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class PreferenceReader {

    private SharedPreferences mPrefs;
    private Context mContext;
    final private String mLogTag = "TelephonyEventsNotifier";

    PreferenceReader(Context context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    void updateDefaultValues() {
        PackageInfo pInfo;

        if (mContext != null) {
            try {
                /* This code will set default values and overwrite user configuration when the
                 * versionCode is updated. */
                pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
                if (pInfo != null) {
                    if (mPrefs.getLong("lastRunVersionCode", 0) < pInfo.versionCode) {
                        Log.d(mLogTag, "set default values");
                        SharedPreferences.Editor editor = mPrefs.edit();
                        if (editor != null) {
                            editor.clear();
                            editor.putLong("lastRunVersionCode", pInfo.versionCode);
                            editor.commit();
                            PreferenceManager.setDefaultValues(mContext, R.xml.preferences, true);
                        } else {
                            Log.e(mLogTag, "editor is null");
                        }

                    }
                } else {
                    Log.e(mLogTag, "pInfo is null");
                }
            } catch (NameNotFoundException e) {
                Log.e(mLogTag, "failed to load package information");
            }
        } else {
            Log.e(mLogTag, "mContext is null");
        }
    }

    Boolean isPopupEnabled() {
        return mPrefs.getBoolean("checkboxPopup", false);
    }

    Boolean isNotificationEnabled() {
        return mPrefs.getBoolean("checkboxNotification", false);
    }

    Boolean isNotificationSoundEnabled() {
        return mPrefs.getBoolean("checkboxNotificationSound", false);
    }

    Boolean isNotificationVibrateEnabled() {
        return mPrefs.getBoolean("checkboxNotificationVibrator", false);
    }

    Boolean isCoreDumpStartEnabled() {
        return mPrefs.getBoolean("checkboxCoreDumpStart", false);
    }

    Boolean isCoreDumpCompleteEnabled() {
        return mPrefs.getBoolean("checkboxCoreDumpStop", false);
    }

    Boolean isModemRebootEnabled() {
        return mPrefs.getBoolean("checkboxModemReboot", false);
    }

    Boolean isModemOutOfServiceEnabled() {
        return mPrefs.getBoolean("checkboxModemOut", false);
    }

    Boolean isModemWarmResetEnabled() {
        return mPrefs.getBoolean("checkboxWarmReset", false);
    }

    Boolean isModemColdResetEnabled() {
        return mPrefs.getBoolean("checkboxColdReset", false);
    }

    Boolean isModemUnsolicitedResetEnabled() {
        return mPrefs.getBoolean("checkboxUnsolicitedReset", false);
    }

    Boolean isModemNotResponsiveEnabled() {
        return mPrefs.getBoolean("checkboxNotResponsive", false);
    }

    Boolean isModemFwBadFamilyEnabled() {
        return mPrefs.getBoolean("checkboxFwFamily", false);
    }

    Boolean isModemFwSecurityCorruptedEnabled() {
        return mPrefs.getBoolean("checkboxFwSecurity", false);
    }

    Boolean isModemFwOutdatedEnabled() {
        return mPrefs.getBoolean("checkboxFwOutdated", false);
    }

    Boolean isModemFwRestrictedEnabled() {
        return mPrefs.getBoolean("checkboxFwRestricted", false);
    }

    Boolean isModemFwUpdateFailed() {
        return mPrefs.getBoolean("checkboxFwUpdateFailure", false);
    }
}
