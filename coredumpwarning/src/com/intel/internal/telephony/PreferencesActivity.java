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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
//import android.app.Activity;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.EditText;
import com.intel.internal.telephony.TelephonyEventsNotifier.exceptions.ModemControlException;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Html;


/**
 * PreferenceActivity class: it displays the preference panel
 * Extends Activity class
 * @see Activity
 */
public class PreferencesActivity extends PreferenceActivity implements OnPreferenceClickListener {

    public static final String KEY_RESTORE_SETTINGS = "prefs_shared_restore_settings_key";
    private static final int DIALOG_RESTORE_SETTINGS = 0;
    final private String mLogTag = "TelephonyEventsNotifier";
    private GsmttyManager ttyManager;

    public void onCreate(Bundle savedInstanceState) {
		Log.e(mLogTag, "3");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        Preference restoreSettings = findPreference(this.getString(R.string.prefs_shared_restore_settings_key));
        if (restoreSettings != null) {
			Log.e(mLogTag, "8");
				
	        restoreSettings.setOnPreferenceClickListener(this);
    	}
        	
    }
	
    @Override
    public boolean onPreferenceClick(Preference preference) {
		Log.e(mLogTag, "5");
        String thisKey = preference.getKey();
        String restoreSettingsKey = this.getString(R.string.prefs_shared_restore_settings_key);
		Log.e(mLogTag, "1");
        if (thisKey.contentEquals(restoreSettingsKey)) {
			Log.e(mLogTag, "2");
            showDialog(DIALOG_RESTORE_SETTINGS);
            return true;
        } else
            return false;
    }

    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        switch(id) {
        case DIALOG_RESTORE_SETTINGS:
            dialog = createRestoreSettingsDialog();
            break;
        default:
            dialog = null;
        }
        return dialog;
    }

    private Dialog createRestoreSettingsDialog() {
		final EditText input = new EditText(this);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("pls input at command");
		builder.setIcon(android.R.drawable.ic_dialog_info);
		builder.setView(input);
        //builder.setMessage(R.string.dialog_restore_settings_title);
        builder.setPositiveButton(R.string.dialog_restore_settings_yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                restoreDefaultSettings(input.getText().toString());
            }
        });
        builder.setNegativeButton(R.string.dialog_restore_settings_no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
            }
        });
        return builder.create();
    }

    private void restoreDefaultSettings(String input) {
        String notifyMsg = "at command return:";
        String dialogMsg = "";
		try {
            	this.openTty();
				String inputMsg = input+ "\r\n";
				dialogMsg = sendAtCommand(inputMsg);
				
				Log.i(mLogTag, "dialogMsg is "+dialogMsg);
        	} catch(ModemControlException ex) {
    		}
		
		new AlertDialog.Builder(this)
		.setTitle("result")
		.setMessage(dialogMsg)
		.setPositiveButton("yes",null)
		.show();
    }
    /**
     * Override of onResume
     *
     * @return void
     */
    @Override
    protected void onResume() {
        super.onResume();
		Log.e(mLogTag, "4");

        PreferenceReader reader = new PreferenceReader(this);
        reader.updateDefaultValues();

        //getFragmentManager().beginTransaction().replace(android.R.id.content,
       //     new PreferencesFragment()).commit();
    }
	public void openTty() {
        try {
	        if (this.ttyManager != null) {
				Log.i(mLogTag, "OPEN tty is not null");
				ttyManager.close();
			    Thread.sleep(500);
           	}
            this.ttyManager = new GsmttyManager();
        } catch (ModemControlException ex) {
           	Log.e(mLogTag, "there is an issue with tty opening");
    	} catch (Exception e) {
		}
    }    

	public String sendAtCommand(String command) throws ModemControlException {
        String ret = "NOK";
		
		Log.i(mLogTag, "sending to modem " + command);
        if (command != null) {
            if (!command.equals("")) {
                ret = this.ttyManager.writeToModemControl(command);
				Log.i(mLogTag, "ret is"+ret);
            }
        }
        return ret;
    }
}
