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

package com.intel.amtl.gui;

import com.intel.amtl.R;

import android.content.Intent;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;

import java.util.ArrayList;

public class AMTLSettings extends PreferenceActivity {
    private static Boolean gcmEnabled = null;
    private ArrayList <String> modemNames;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.menu);

        setTitle("AMTL Settings");

        Intent get = getIntent();
        modemNames = get.getStringArrayListExtra("modem");

        final ListPreference listPreference
                = (ListPreference) findPreference("settings_modem_name_key");

        if (modemNames != null && !modemNames.isEmpty()) {
            setListPreferenceModem(listPreference, modemNames);

            listPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    setListPreferenceModem(listPreference, modemNames);
                    return false;
                }
            });

            listPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int i = ((ListPreference) preference).findIndexOfValue(newValue.toString());
                    CharSequence[] entries = ((ListPreference) preference).getEntries();
                    preference.setSummary(entries[i]);
                    return true;
                }
            });
        }

        EditTextPreference savePath
                = (EditTextPreference) findPreference(getString(R.string.settings_save_path_key));
        if (null != savePath) {
            savePath.setOnPreferenceChangeListener(generalPathUpdated);
        }
    }

    private final OnPreferenceChangeListener generalPathUpdated = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            return true;
        }
    };

    protected static void setListPreferenceModem(ListPreference listPreference,
            ArrayList <String> names ) {

        ArrayList<String> listItem = new ArrayList<String>();

        for (String s : names) {
            listItem.add(names.indexOf(s), Integer.toString(names.indexOf(s)));
        }

        CharSequence[] entries = names.toArray(new CharSequence[names.size()]);
        listPreference.setEntries(entries);
        CharSequence[] entryValues = listItem.toArray(new CharSequence[listItem.size()]);
        listPreference.setEntryValues(entryValues);
        if (null == listPreference.getValue()) {
            listPreference.setValueIndex(0);
            listPreference.setSummary(entries[0]);
        } else {
            listPreference.setSummary(listPreference.getEntry());
        }
    }
}
