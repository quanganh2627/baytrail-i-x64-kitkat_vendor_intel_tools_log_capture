/*
 * Copyright (C) 2012 Intel Corporation, All rights Reserved.
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

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Preferences class: it configure this app
 * Extends PreferenceFragment class
 * @see PreferenceFragment
 */
public class PreferencesFragment extends PreferenceFragment {
    /**
     * Override of onResume
     *
     * @return void
     */
    @Override
    public void onResume() {
        super.onResume();
        addPreferencesFromResource(R.xml.preferences);
    }
}
