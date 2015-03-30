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
import android.preference.CheckBoxPreference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
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
import java.io.IOException;


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
	private CheckBoxPreference coredumpPreference;
	private CheckBoxPreference bplogPreference;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        Preference restoreSettings = findPreference(this.getString(R.string.prefs_shared_restore_settings_key));
        if (restoreSettings != null) {

	        restoreSettings.setOnPreferenceClickListener(this);
    	}
		
		coredumpPreference = (CheckBoxPreference)findPreference(this.getString(R.string.prefs_shared_coredump_settings_key));
        if (coredumpPreference != null) {
			if (coredumpEnabled())
				coredumpPreference.setChecked(true);
			else
				coredumpPreference.setChecked(false);			
			coredumpPreference.setOnPreferenceClickListener(this);
			Log.i(mLogTag, "coredump enable is "+coredumpPreference.isChecked());
			
    	}

		
		bplogPreference = (CheckBoxPreference)findPreference(this.getString(R.string.prefs_shared_bplog_settings_key));
        if (bplogPreference != null) {
			if (bplogEnabled())
				bplogPreference.setChecked(true);
			else
				bplogPreference.setChecked(false);
			bplogPreference.setOnPreferenceClickListener(this);
			Log.i(mLogTag, "bplog enable is "+bplogPreference.isChecked());
    	}
    }

	@Override
    public boolean onPreferenceClick(Preference preference) {
        String thisKey = preference.getKey();
        String restoreSettingsKey = this.getString(R.string.prefs_shared_restore_settings_key);
		String restoreCoredumpKey = this.getString(R.string.prefs_shared_coredump_settings_key);
		String restoreBplogKey = this.getString(R.string.prefs_shared_bplog_settings_key);
		
		//at command box
		if (thisKey.contentEquals(restoreSettingsKey)) {
            showDialog(DIALOG_RESTORE_SETTINGS);
        }

		//coredump box
		if (thisKey.contentEquals(restoreCoredumpKey)) {
			Log.i(mLogTag, "enter here coredump "+coredumpPreference.isChecked());
			if (coredumpPreference.isChecked()) {
				Log.i(mLogTag, "enter here coredump 2\n");
				if(enableCoredump())
					new AlertDialog.Builder(this)
						.setTitle("result")
						.setMessage("enable coredump ok")
						.setPositiveButton("yes",null)
						.show();
				else
					coredumpPreference.setChecked(false);		
			}
			else {				
				Log.i(mLogTag, "enter here coredump 3\n");				
				if(disableCoredump())
					new AlertDialog.Builder(this)
						.setTitle("result")
						.setMessage("disable coredump ok")
						.setPositiveButton("yes",null)
						.show();
				else
					coredumpPreference.setChecked(true);
			}
        }

		//bplog box
		if (thisKey.contentEquals(restoreBplogKey)) {
			Log.i(mLogTag, "enter here bplog "+bplogPreference.isChecked());
			if (bplogPreference.isChecked()) {
				Log.i(mLogTag, "enter here 2\n");
				if(enableBplog()) 
					new AlertDialog.Builder(this)
					.setTitle("result")
					.setMessage("please reboot phone")
					.setPositiveButton("yes",null)
					.show();
				else 
					bplogPreference.setChecked(false);
									
			}
			else {				
				Log.i(mLogTag, "enter here bplog 3\n");				
				if(disableBplog())
					new AlertDialog.Builder(this)
					.setTitle("result")
					.setMessage("disable bplog ok")
					.setPositiveButton("yes",null)
					.show();
				else 
					bplogPreference.setChecked(true);
			}
        }
		
		return true;
    }

	public boolean enableCoredump() {
		String returnMsg = "";
		String handleValue = "";
		try {
				this.openTty();
				// open port
				returnMsg = sendAtCommand("at@cdd:paramopen(CDD_SETTINGS_ACCESS_NVM)\r\n");
				Log.i(mLogTag, "returnMsg is "+returnMsg);
				if (-1==returnMsg.indexOf("OK")) {
					returnMsg = sendAtCommand("at@cdd:paramopen(CDD_SETTINGS_ACCESS_NVM)\r\n");
					Log.i(mLogTag, "returnMsg2 is "+returnMsg);
					if(-1==returnMsg.indexOf("OK"))
						return false;
				}
				if((handleValue=returnMsg.substring(returnMsg.lastIndexOf("(")+1,returnMsg.lastIndexOf(")")))==null)
					return false;
				Log.i(mLogTag, "handle value is" + handleValue);

				//write port£¬ 0 means silent rest off, which will enable coredump info
				returnMsg = sendAtCommand("at@cdd:paramwrite("+handleValue+",ENABLESTATE,1)\r\n");
				if (-1==returnMsg.indexOf("OK")) {
					returnMsg = sendAtCommand("at@cdd:paramwrite("+handleValue+",ENABLESTATE,1)\r\n");
					Log.i(mLogTag, "returnMsg2 is "+returnMsg);
					if(-1==returnMsg.indexOf("OK"))
						return false;
				}

				//close port
				returnMsg = sendAtCommand("at@cdd:paramclose("+handleValue+")\r\n");
				if (-1==returnMsg.indexOf("OK")) {
					returnMsg = sendAtCommand("at@cdd:paramclose("+handleValue+")\r\n");
					Log.i(mLogTag, "returnMsg2 is "+returnMsg);
					if(-1==returnMsg.indexOf("OK"))
						return false;
				}

			} catch(ModemControlException ex) {
			}
		return true;

	}

	public boolean disableCoredump() {
		
		String returnMsg = "";
		String handleValue = "";
		try {
				this.openTty();
				// open port
				returnMsg = sendAtCommand("at@cdd:paramopen(CDD_SETTINGS_ACCESS_NVM)\r\n");
				Log.i(mLogTag, "returnMsg is "+returnMsg);
				if (-1==returnMsg.indexOf("OK")) {
					returnMsg = sendAtCommand("at@cdd:paramopen(CDD_SETTINGS_ACCESS_NVM)\r\n");
					Log.i(mLogTag, "returnMsg2 is "+returnMsg);
					if(-1==returnMsg.indexOf("OK"))
						return false;
				}
				if((handleValue=returnMsg.substring(returnMsg.lastIndexOf("(")+1,returnMsg.lastIndexOf(")")))==null)
					return false;
				Log.i(mLogTag, "handle value is" + handleValue);

				//write port£¬ 1 means silent rest on, which will disable coredump info
				returnMsg = sendAtCommand("at@cdd:paramwrite("+handleValue+",ENABLESTATE,0)\r\n");
				if (-1==returnMsg.indexOf("OK")) {
					returnMsg = sendAtCommand("at@cdd:paramwrite("+handleValue+",ENABLESTATE,0)\r\n");
					Log.i(mLogTag, "returnMsg2 is "+returnMsg);
					if(-1==returnMsg.indexOf("OK"))
						return false;
				}

				//close port
				returnMsg = sendAtCommand("at@cdd:paramclose("+handleValue+")\r\n");
				if (-1==returnMsg.indexOf("OK")) {
					returnMsg = sendAtCommand("at@cdd:paramclose("+handleValue+")\r\n");
					Log.i(mLogTag, "returnMsg2 is "+returnMsg);
					if(-1==returnMsg.indexOf("OK"))
						return false;
				}

			} catch(ModemControlException ex) {
			}
		return true;
	}
	public boolean enableBplog() {
		String returnMsg = "";
		try {
				this.openTty();
				returnMsg = sendAtCommand("at+xsystrace=1,\"bb_sw=1;3g_sw=1;digrf=1\",\"digrf=0x84;bb_sw=sdl:th,tr,st,db,pr,lt,li,gt,ae,mo\",\"oct=4\"\r\n");
				Log.i(mLogTag, "returnMsg enableBplog is "+returnMsg);
				if (-1==returnMsg.indexOf("OK")) {
					returnMsg = sendAtCommand("at+xsystrace=1,\"bb_sw=1;3g_sw=1;digrf=1\",\"digrf=0x84;bb_sw=sdl:th,tr,st,db,pr,lt,li,gt,ae,mo\",\"oct=4\"\r\n");
					Log.i(mLogTag, "returnMsg2 is "+returnMsg);
					if(-1==returnMsg.indexOf("OK"))
						return false;
				}
			} catch(ModemControlException ex) {
		}
		return true;
	}
	
	public boolean disableBplog() {
		
		String returnMsg = "";
		try {
				this.openTty();
				sendAtCommand("at+xsystrace=1,\"bb_sw=0;3g_sw=0;digrf=0\",\"digrf=0x84;bb_sw=sdl:th,tr,st,db,pr,lt,li,gt,ae,mo\",\"oct=4\"\r\n");
				returnMsg = sendAtCommand("at+xsystrace=1,\"bb_sw=0;3g_sw=0;digrf=0\",\"digrf=0x84;bb_sw=sdl:th,tr,st,db,pr,lt,li,gt,ae,mo\",\"oct=4\"\r\n");
				Log.i(mLogTag, "returnMsg disableBplog1 is "+returnMsg);
				if (-1==returnMsg.indexOf("OK"))
					return false;
			} catch(ModemControlException ex) {
		}
		return true;
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

        PreferenceReader reader = new PreferenceReader(this);
        reader.updateDefaultValues();

        //getFragmentManager().beginTransaction().replace(android.R.id.content,
       //     new PreferencesFragment()).commit();
    }

	@Override
    public void onBackPressed() {
        super.onBackPressed();
		Log.i(mLogTag, "on back key press");
		
		try {
			this.ttyManager.close();
		} catch (Exception ex) {
           	Log.e(mLogTag, "there is an issue with tty opening");
		}
		
    }
	public boolean openTty() {
        try {
			if (this.ttyManager == null) {
            	this.ttyManager = new GsmttyManager();
           	}
        } catch (ModemControlException ex) {
           	Log.e(mLogTag, "there is an issue with tty opening");
			return false;
    	} catch (Exception e) {
		}
		return true;
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

	public boolean bplogEnabled() {

		String returnMsg = "";
		try {
				if(false==this.openTty())
					this.openTty();
				returnMsg = sendAtCommand("at+xsystrace=10\r\n");
				Log.i(mLogTag, "returnMsg is "+returnMsg);
				if ((-1==returnMsg.indexOf("bb_sw: Oct"))||(-1==returnMsg.indexOf("3g_sw: Oct"))||(-1==returnMsg.indexOf("digrf: Oct")))
					return false;
			} catch(ModemControlException ex) {
		}
		return true;
	}

	
	public boolean coredumpEnabled() {

		String returnMsg = "";
		try {
				if(false==this.openTty()) {
					Thread.currentThread();
					Thread.sleep(1000);
					this.openTty();
				}
				returnMsg = sendAtCommand("at@cdd:getEnabled()\r\n");
				Log.i(mLogTag, "returnMsg is "+returnMsg);
				if (-1==returnMsg.indexOf("enabled"))
					return false;
			} catch(ModemControlException ex) {
			} catch (InterruptedException ie) {
			}
		return true;
	}
	
}
