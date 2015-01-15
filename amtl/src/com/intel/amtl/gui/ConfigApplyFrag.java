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
package com.intel.amtl.gui;


import android.app.Activity;
import android.app.Application;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.intel.amtl.AMTLApplication;
import com.intel.amtl.R;
import com.intel.amtl.exceptions.ModemControlException;
import com.intel.amtl.models.config.LogOutput;
import com.intel.amtl.models.config.ModemConf;
import com.intel.amtl.modem.ModemController;
import com.intel.amtl.mts.MtsManager;

import java.util.ArrayList;


// Embedded class to handle delayed configuration setup (Dialog part).
public class ConfigApplyFrag extends DialogFragment {

    private final String TAG = "AMTL";
    private final String MODULE = "ConfigApplyFrag";

    private static final String EXTRA_TAG = "extra_tag";
    private static final String EXTRA_FRAG = "extra_frag";

    int targetFrag;
    String tag;

    ProgressBar confProgNot;
    // thread executed while Dialog Box is displayed.
    ApplyConfTask exeSetup;

    public static final ConfigApplyFrag newInstance(String tag, int targetFrag) {
        ConfigApplyFrag confApplyFrag = new ConfigApplyFrag();
        Bundle bdl = new Bundle(2);
        bdl.putString(EXTRA_TAG, tag);
        bdl.putInt(EXTRA_FRAG, targetFrag);
        confApplyFrag.setArguments(bdl);
        return confApplyFrag;
    }

    public void handlerConf(ApplyConfTask confTask) {
        // This allows to get ConfSetupTerminated on the specified Fragment.
        this.exeSetup = confTask;
        this.exeSetup.setFragment(this);
    }

    public void confSetupTerminated(String exceptReason) {
        // dismiss() is possible only if we are on the current Activity.
        // And will crash if we have switched to another one.
        if (isResumed()) {
            dismiss();
        }

        this.exeSetup = null;
        if (!exceptReason.equals("")) {
            Log.e(TAG, MODULE + ": modem conf application failed: " + exceptReason);
            UIHelper.okDialog(getActivity(),
                    "Error ", "Configuration not applied:\n" + exceptReason);
        }

        if (getTargetFragment() != null) {
            getTargetFragment().onActivityResult(targetFrag, Activity.RESULT_OK, null);
        }
    }

    public void launch(ModemConf modemConfToApply, Fragment frag, FragmentManager gsfManager) {
        handlerConf(new ApplyConfTask(modemConfToApply));
        setTargetFragment(this, targetFrag);
        show(gsfManager, tag);
    }

    // Function overrides for the DialogFragment instance.
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        this.tag = getArguments().getString(EXTRA_TAG);
        this.targetFrag = getArguments().getInt(EXTRA_FRAG);
        // Spawn the thread to execute modem configuration.
        if (this.exeSetup != null) {
            this.exeSetup.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        // Create dialog box.
        View view = inflater.inflate(R.layout.fragment_task, container);
        confProgNot = (ProgressBar)view.findViewById(R.id.progressBar);
        getDialog().setTitle("Executing configuration");
        setCancelable(false);

        return view;
    }

    @Override
    public void onDestroyView() {
        // This will allow dialog box to stay even if parent layout
        // configuration is changed (rotation)
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        // This allows to close dialog box if the thread ends while
        // we are not focused on the activity.
        super.onResume();
        if (this.exeSetup == null) {
            dismiss();
        }
    }

    // embedded class to handle delayed configuration setup (thread part).
    public static class ApplyConfTask extends AsyncTask<Void, Void, Void> {
        private final String TAG = "AMTL";
        private final String MODULE = "ApplyConfTask";
        private ConfigApplyFrag confSetupFrag;
        private ModemController modemCtrl;
        private MtsManager mtsManager;
        private ModemConf modConfToApply;
        private String exceptReason = "";
        private ArrayList<String> modemNames;
        private String modName;

        public ApplyConfTask (ModemConf confToApply) {
            this.modConfToApply = confToApply;
            modemNames = new ArrayList<String>();
        }

        void setFragment(ConfigApplyFrag confAppFrag) {
            confSetupFrag = confAppFrag;
        }

        private void applyConf(SharedPreferences prefs, SharedPreferences.Editor editor,
                ModemConf mdmConf) throws ModemControlException {
            if (modemCtrl != null) {
                // send the three at commands
                modemCtrl.confTraceAndModemInfo(mdmConf);
                modemCtrl.switchTrace(mdmConf);
                // if flush command available for this configuration, let s use it.
                if (!mdmConf.getFlCmd().equalsIgnoreCase("")) {
                    Log.d(TAG, MODULE + ": Config has flush_cmd defined.");
                    modemCtrl.flush(mdmConf);
                    // give time to the modem to sync - 1 second
                    SystemClock.sleep(1000);
                } else {
                    // fall back - check if a default flush cmd is set
                    Log.d(TAG, MODULE + ": Fall back - check default_flush_cmd");
                    String flCmd = prefs.getString("default_flush_cmd", "");
                    if (!flCmd.equalsIgnoreCase("")) {
                        modemCtrl.sendAtCommand(flCmd + "\r\n");
                        // give time to the modem to sync - 1 second
                        SystemClock.sleep(1000);
                    }
                }

                // Success to apply conf, it s time to record it.
                Log.d(TAG, MODULE + ": Configuration index to save: " + mdmConf.getIndex());

                editor.putInt("index" + modName, mdmConf.getIndex());

                // check if the configuration requires mts
                mtsManager = new MtsManager();
                if (mdmConf != null) {
                    // set mts parameters through mts properties
                    mtsManager.stopServices();
                    mdmConf.applyMtsParameters();
                    if (mdmConf.isMtsRequired()) {
                        // start mts in the chosen mode: persistent or oneshot
                        mtsManager.startService(mdmConf.getMtsMode());
                    }
                    // restart modem by a cold reset
                    modemCtrl.restartModem();
                    // give time to the modem to be up again
                    SystemClock.sleep(10000);
                } else {
                    exceptReason = "no configuration to apply";
                }
            } else {
                exceptReason = "cannot apply configuration: modemCtrl is null";
            }
        }

        // Function overrides for Apply configuration thread.
        @Override
        protected Void doInBackground(Void... params) {
            SharedPreferences prefs =
                    AMTLApplication.getContext().getSharedPreferences("AMTLPrefsData",
                    Context.MODE_PRIVATE);
            SharedPreferences.Editor editor =
                    AMTLApplication.getContext().getSharedPreferences("AMTLPrefsData",
                    Context.MODE_PRIVATE).edit();
            try {
                modemNames = AMTLApplication.getModemNameList();
                String curModemIndex = PreferenceManager
                        .getDefaultSharedPreferences(AMTLApplication.getContext())
                        .getString(AMTLApplication.getContext()
                        .getString(R.string.settings_modem_name_key), "0");
                int readModemIndex = Integer.parseInt(curModemIndex);
                modName = modemNames.get(readModemIndex);
                modemCtrl = ModemController.getInstance();
                applyConf(prefs, editor, modConfToApply);
                // Everything went right, so let s commit trace configuration.
                editor.commit();
            } catch (ModemControlException ex) {
                exceptReason = ex.getMessage();
                Log.e(TAG, MODULE + ": cannot change modem configuration " + ex);
                // if config change failed, apply modem default conf if defined or else deactivate
                // logging
                editor.remove("index" + modName);
                editor.commit();
                try {
                    LogOutput defaultOutput = AMTLApplication.getDefaultConf();
                    if (defaultOutput != null) {
                        modConfToApply = ModemConf.getInstance(AMTLApplication.getDefaultConf());
                        Log.d(TAG, MODULE + ": applying default conf");
                    } else {
                        modConfToApply = modemCtrl.getNoLoggingConf();
                        Log.d(TAG, MODULE + ": stopping logging");
                    }
                    applyConf(prefs, editor, modConfToApply);
                    // Everything went right, so let s commit trace configuration.
                    editor.commit();
                } catch (ModemControlException mcex) {
                    Log.e(TAG, MODULE + ": failed to apply config " + mcex);
                }
            } finally {
               modemCtrl = null;
               modConfToApply = null;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void exception) {
            if (confSetupFrag == null) {
                return;
            }
            confSetupFrag.confSetupTerminated(exceptReason);
        }
    }
}
