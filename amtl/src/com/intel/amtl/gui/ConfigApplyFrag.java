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
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.intel.amtl.R;
import com.intel.amtl.exceptions.ModemControlException;
import com.intel.amtl.models.config.ModemConf;
import com.intel.amtl.modem.ModemController;
import com.intel.amtl.mts.MtsManager;


// Embedded class to handle delayed configuration setup (Dialog part).
public class ConfigApplyFrag extends DialogFragment {

    private final String TAG = "AMTL";
    private final String MODULE = "ConfigApplyFrag";

    int targetFrag;
    String tag;

    ProgressBar confProgNot;
    // thread executed while Dialog Box is displayed.
    ApplyConfTask exeSetup;

    public ConfigApplyFrag(String tag, int targetFrag) {
        this.tag = tag;
        this.targetFrag = targetFrag;
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
            getTargetFragment()
                    .onActivityResult(targetFrag, Activity.RESULT_OK, null);
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
        // Spawn the thread to execute modem configuration.
        if (this.exeSetup != null) {
            this.exeSetup.execute();
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
        /* This will allow dialog box to stay even if parent layout configuration
        is changed (rotation).*/
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        /* This allows to close dialog box if the thread ends while we are not focused
        on the activity. */
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

        public ApplyConfTask (ModemConf confToApply) {
            this.modConfToApply = confToApply;
        }

        void setFragment(ConfigApplyFrag confAppFrag) {
            confSetupFrag = confAppFrag;
        }

        // Function overrides for Apply configuration thread.
        @Override
        protected Void doInBackground(Void... params) {
            try {
                modemCtrl = ModemController.get();
                if (modemCtrl != null) {
                    // send the three at commands
                    modemCtrl.sendAtCommand(modConfToApply.getXsio());
                    modemCtrl.sendAtCommand(modConfToApply.getTrace());
                    modemCtrl.sendAtCommand(modConfToApply.getXsystrace());
                    // check if the configuration requires mts
                    mtsManager = new MtsManager();
                    if (modConfToApply != null) {
                        if (modConfToApply.isMtsRequired()) {
                            // set mts parameters through mts properties
                            mtsManager.stopServices();
                            modConfToApply.applyMtsParameters();
                            // start mts in the chosen mode: persistent or oneshot
                            mtsManager.startService(modConfToApply.getMtsMode());
                        } else {
                            // do not stop mts when applying conf from masterfrag
                            if (!modConfToApply.getXsio().equals("")
                                    || !modConfToApply.getTrace().equals("")) {
                                mtsManager.stopServices();
                                modConfToApply.applyMtsParameters();
                            }
                        }
                        modConfToApply = null;
                        // restart modem by a cold reset
                        modemCtrl.restartModem();
                        // give time to the modem to be up again
                        SystemClock.sleep(2000);
                    } else {
                        exceptReason = "no configuration to apply";
                    }
                } else {
                    exceptReason = "cannot apply configuration: modemCtrl is null";
                }
            } catch (ModemControlException ex) {
                exceptReason = ex.getMessage();
                Log.e(TAG, MODULE + ": cannot change modem configuration " + ex);
                // if config change failed, logging is stopped
                try {
                    mtsManager = new MtsManager();
                    mtsManager.stopServices();
                    modemCtrl.sendAtCommand("AT+TRACE=0\r\n");
                    modemCtrl.sendAtCommand("AT+XSYSTRACE=0\r\n");
                    modemCtrl.restartModem();
                    Log.e(TAG, MODULE + ": logging has been stopped");
                } catch (ModemControlException mcex) {
                    Log.e(TAG, MODULE + ": failed to stop logging " + mcex);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void exception) {
            modemCtrl = null;
            if (confSetupFrag == null) {
                return;
            }
            confSetupFrag.confSetupTerminated(exceptReason);
        }
    }
}
