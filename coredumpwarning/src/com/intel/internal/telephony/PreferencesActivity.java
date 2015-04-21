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
import java.io.RandomAccessFile;
import java.io.File;
import java.io.FileNotFoundException;


/**
 * PreferenceActivity class: it displays the preference panel
 * Extends Activity class
 * @see Activity
 */
public class PreferencesActivity extends PreferenceActivity implements OnPreferenceClickListener {

    public static final String KEY_RESTORE_SETTINGS = "prefs_shared_restore_settings_key";
    private static final int DIALOG_RESTORE_SETTINGS = 0;
	private static final int DIALOG_BPLOG_ENABLE = 1;
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
			Log.d(mLogTag, "coredump enable is "+coredumpPreference.isChecked());
			
    	}

		
		bplogPreference = (CheckBoxPreference)findPreference(this.getString(R.string.prefs_shared_bplog_settings_key));
        if (bplogPreference != null) {
			if (bplogEnabled())
				bplogPreference.setChecked(true);
			else
				bplogPreference.setChecked(false);
			bplogPreference.setOnPreferenceClickListener(this);
			Log.d(mLogTag, "bplog enable is "+bplogPreference.isChecked());
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
			Log.d(mLogTag, "enter here coredump "+coredumpPreference.isChecked());
			if (coredumpPreference.isChecked()) {
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
			Log.d(mLogTag, "enter here bplog "+bplogPreference.isChecked());
			if (bplogPreference.isChecked()) {
				showDialog(DIALOG_BPLOG_ENABLE);
			}
			else {				
				if(disableBplog())
					new AlertDialog.Builder(this)
					.setTitle("result")
					.setMessage("Bplog disabled ok")
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
				Log.d(mLogTag, "returnMsg is "+returnMsg);
				if (-1==returnMsg.indexOf("OK")) {
					returnMsg = sendAtCommand("at@cdd:paramopen(CDD_SETTINGS_ACCESS_NVM)\r\n");
					Log.d(mLogTag, "returnMsg2 is "+returnMsg);
					if(-1==returnMsg.indexOf("OK")) {
						this.closeTty();
						return false;
					}
				}
				if((handleValue=returnMsg.substring(returnMsg.lastIndexOf("(")+1,returnMsg.lastIndexOf(")")))==null) {
					this.closeTty();
					return false;
				}
				Log.d(mLogTag, "handle value is" + handleValue);

				//write port£¬ 0 means silent rest off, which will enable coredump info
				returnMsg = sendAtCommand("at@cdd:paramwrite("+handleValue+",ENABLESTATE,1)\r\n");
				if (-1==returnMsg.indexOf("OK")) {
					returnMsg = sendAtCommand("at@cdd:paramwrite("+handleValue+",ENABLESTATE,1)\r\n");
					Log.d(mLogTag, "returnMsg2 is "+returnMsg);
					if(-1==returnMsg.indexOf("OK")) {
						this.closeTty();
						return false;
					}
				}

				//close port
				returnMsg = sendAtCommand("at@cdd:paramclose("+handleValue+")\r\n");
				if (-1==returnMsg.indexOf("OK")) {
					returnMsg = sendAtCommand("at@cdd:paramclose("+handleValue+")\r\n");
					Log.d(mLogTag, "returnMsg2 is "+returnMsg);
					if(-1==returnMsg.indexOf("OK")) {
						this.closeTty();
						return false;
					}
				}

			} catch(ModemControlException ex) {
			}
		this.closeTty();
		return true;

	}

	public boolean disableCoredump() {
		
		String returnMsg = "";
		String handleValue = "";
		try {
				this.openTty();
				// open port
				returnMsg = sendAtCommand("at@cdd:paramopen(CDD_SETTINGS_ACCESS_NVM)\r\n");
				Log.d(mLogTag, "returnMsg is "+returnMsg);
				if (-1==returnMsg.indexOf("OK")) {
					returnMsg = sendAtCommand("at@cdd:paramopen(CDD_SETTINGS_ACCESS_NVM)\r\n");
					Log.d(mLogTag, "returnMsg2 is "+returnMsg);
					if(-1==returnMsg.indexOf("OK")) {
						this.closeTty();
						return false;
					}
				}
				if((handleValue=returnMsg.substring(returnMsg.lastIndexOf("(")+1,returnMsg.lastIndexOf(")")))==null) {
					this.closeTty();
					return false;
				}
				Log.d(mLogTag, "handle value is" + handleValue);

				//write port£¬ 1 means silent rest on, which will disable coredump info
				returnMsg = sendAtCommand("at@cdd:paramwrite("+handleValue+",ENABLESTATE,0)\r\n");
				if (-1==returnMsg.indexOf("OK")) {
					returnMsg = sendAtCommand("at@cdd:paramwrite("+handleValue+",ENABLESTATE,0)\r\n");
					Log.d(mLogTag, "returnMsg2 is "+returnMsg);
					if(-1==returnMsg.indexOf("OK")) {
						this.closeTty();
						return false;
					}
				}

				//close port
				returnMsg = sendAtCommand("at@cdd:paramclose("+handleValue+")\r\n");
				if (-1==returnMsg.indexOf("OK")) {
					returnMsg = sendAtCommand("at@cdd:paramclose("+handleValue+")\r\n");
					Log.d(mLogTag, "returnMsg2 is "+returnMsg);
					if(-1==returnMsg.indexOf("OK")) {
						this.closeTty();
						return false;
					}
				}

			} catch(ModemControlException ex) {
			}
		this.closeTty();
		return true;
	}
	public boolean enableBplog() {
		try { 
			   RandomAccessFile rf=new RandomAccessFile("/data/system/offlogcfg.txt","rw");
			   rf.writeBytes("on_off        = 1\n");
			   rf.writeBytes("offlog_path   = /data/logs/\n");
			   rf.writeBytes("offlog_size   = 500000000\n");
			   rf.writeBytes("offlog_number = 4");
			   rf.close(); 
		   } 
		   catch(IOException e){ 
		   	   Log.e(mLogTag,"Failed: " + e);
			   return false; 
		   }
		   return true;
	}
	
	public boolean disableBplog() {
		
		boolean result = false;
		try { 
			   // disable on_off parameter
			RandomAccessFile rf=new RandomAccessFile("/data/system/offlogcfg.txt","rw");
			rf.writeBytes("on_off        = 0\n");
			rf.writeBytes("offlog_path   = /data/logs/\n");
			rf.writeBytes("offlog_size   = 500000000\n");
			rf.writeBytes("offlog_number = 4");
			rf.close();

			//at+xsystrace=0
			this.openTty();
			for(int i=0;i<2;i++) {
				
				String returnMsg = sendAtCommand("at+xsystrace=0\r\n");
				Log.d(mLogTag, "returnMsg is "+returnMsg);
				if (-1!=returnMsg.indexOf("OK")){
					result=true;
					break;
				}
			}
				
		} catch(IOException e) { 
		   e.printStackTrace(); 
		} catch(ModemControlException ex) {
		}
		this.closeTty();
		return result;
	}
	
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        switch(id) {
        case DIALOG_RESTORE_SETTINGS:
            dialog = createRestoreSettingsDialog();
            break;
		case DIALOG_BPLOG_ENABLE:
			dialog = createBplogEnable();
			break;
        default:
            dialog = null;
        }
        return dialog;
    }

	private Dialog createBplogEnable() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Reminder");
		builder.setIcon(android.R.drawable.ic_dialog_info);
		builder.setMessage("Reboot phone?");
        //builder.setMessage(R.string.dialog_restore_settings_title);
        builder.setPositiveButton(R.string.dialog_restore_settings_yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
				if(enableBplog()) {
					Intent intent2 = new Intent(Intent.ACTION_REBOOT);
					intent2.putExtra("nowait", 1);
					intent2.putExtra("interval", 1);
					intent2.putExtra("window", 0);
					sendBroadcast(intent2);
				}
				else
					bplogPreference.setChecked(false);
					
            }
        });
        builder.setNegativeButton(R.string.dialog_restore_settings_no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
				   bplogPreference.setChecked(false);
			}
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
            }
        });
        return builder.create();
		
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
				Log.d(mLogTag, "dialogMsg is "+dialogMsg);
        	} catch(ModemControlException ex) {
    		}
		this.closeTty();
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
		Log.d(mLogTag, "on back key press");
		
		try {
			this.ttyManager.close();
		} catch (Exception ex) {
           	Log.e(mLogTag, "there is an issue with tty opening");
		}
		
    }

	
    public void closeTty() {
		
		try {
			if (this.ttyManager != null) {
				this.ttyManager.close();
				this.ttyManager = null;
			}
		} catch (Exception ex) {
           	Log.e(mLogTag, "there is an issue with tty closing");
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
		
		Log.d(mLogTag, "sending to modem " + command);
        if (command != null) {
            if (!command.equals("")) {
                ret = this.ttyManager.writeToModemControl(command);
				Log.d(mLogTag, "ret is"+ret);
            }
        }
        return ret;
    }

	public boolean bplogEnabled() {

		boolean result = false;
		File df=new File("/data/logs/bplog");
		if(df.exists()){
			long size1= df.length();
			Log.i(mLogTag, "size1= "+size1);
			try {
				Thread.sleep(1000);
			} catch(InterruptedException ie) {
			}
			if(df.length()!= size1)
				result=true;
		}
		return result;
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
				Log.d(mLogTag, "returnMsg is "+returnMsg);
				if (-1==returnMsg.indexOf("enabled")) {
					closeTty();
					return false;
				}
			} catch(ModemControlException ex) {
			} catch (InterruptedException ie) {
			}
		closeTty();	
		return true;
	}
	
}
