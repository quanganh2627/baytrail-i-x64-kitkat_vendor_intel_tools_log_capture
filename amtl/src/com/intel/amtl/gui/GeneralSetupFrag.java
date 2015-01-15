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
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.EditText;

import com.intel.amtl.AMTLApplication;
import com.intel.amtl.R;
import com.intel.amtl.exceptions.ModemControlException;
import com.intel.amtl.helper.LogManager;
import com.intel.amtl.models.config.ExpertConfig;
import com.intel.amtl.models.config.LogOutput;
import com.intel.amtl.models.config.ModemConf;
import com.intel.amtl.modem.CommandParser;
import com.intel.amtl.modem.ModemController;
import com.intel.amtl.mts.MtsManager;
import com.intel.internal.telephony.ModemStatus;

import java.io.File;
import java.io.IOException;
import java.lang.RuntimeException;
import java.util.ArrayList;


public class GeneralSetupFrag extends Fragment implements OnClickListener, OnCheckedChangeListener {

    private final String TAG = "AMTL";
    private final String MODULE = "GeneralSetupFrag";
    private final String EXPERT_PROPERTY = "persist.service.amtl.expert";
    private final String BACKUP_LOG_PATH = "/data/amtl_logs_backup/";
    private final String AP_LOG_PATH = "/mnt/sdcard/logs/";
    private final String BP_LOG_PATH = "/logs/";
    private final int logTagId = 100;
    private final int genCDId = 200;

    // Graphical objects for onClick handling.
    private Switch sExpertMode;
    private Button bLogTag;
    private Button bGenCD;
    private TextView tvModemStatus;
    private TextView tvMtsStatus;
    private TextView tvOctStatus;

    // Callback object pointer. Active only when associated to Main layout.
    private GSFCallBack gsfCb = nullCb;

    private LinearLayout ll;
    private ArrayList<LogOutput> configArray;
    private ModemConf modemConfToApply;
    private ModemConf currentModemConf;
    private ExpertConfig expConf;
    private MtsManager mtsMgr;

    private String modemName;
    private Boolean firstCreated = true;

    // Used to not execute OnCheckedChanged()
    private Boolean isIgnore = false;

    // Callback interface for Toast messages in Main layout.
    public interface GSFCallBack {
        public void onGeneralConfApplied(ModemConf conf);
    }

    public GeneralSetupFrag(ArrayList<LogOutput> outputArray, ExpertConfig expConf, String modem) {
        this.configArray = outputArray;
        this.expConf = expConf;
        this.modemName = modem;
    }

    public GeneralSetupFrag() {
    }

    private static GSFCallBack nullCb = new GSFCallBack() {
        public void onGeneralConfApplied(ModemConf conf) { }
    };

    private void updateText (String textToDisplay, TextView tv ) {
        if (tv != null) {
            if (textToDisplay.equals("")) {
                tv.setText("stopped");
            } else {
                tv.setText(textToDisplay);
            }
        }
    }

    private void setUIEnabled() {
        this.updateText("UP", tvModemStatus);
        // only enable conf switches if expert property is set to 1
        this.setSwitchesEnabled(!this.isExpertModeEnabled());
        if (this.sExpertMode != null) {
            this.sExpertMode.setEnabled(true);
        }
        if (this.bGenCD != null) {
            this.bGenCD.setEnabled(true);
        }
        this.updateText(this.mtsMgr.getMtsState(), tvMtsStatus);
    }

    private void setUIDisabled() {
        this.updateText("DOWN", tvModemStatus);
        this.setSwitchesEnabled(false);
        if (this.sExpertMode != null) {
            this.sExpertMode.setEnabled(false);
        }
        if (this.bGenCD != null) {
            this.bGenCD.setEnabled(false);
        }
        this.updateText(this.mtsMgr.getMtsState(), tvMtsStatus);
    }

    private void updateUi(ModemConf curModConf) {
        SharedPreferences prefs = this.getActivity().getSharedPreferences("AMTLPrefsData",
                Context.MODE_PRIVATE);
        int id = prefs.getInt("index" + modemName, -2);
        if (id >= 0) {
            if (!curModConf.confTraceEnabled()) {
                if (mtsMgr.getMtsState().equals("running")) {
                    Log.e(TAG, MODULE + ": stopping mts running wrongly");
                    mtsMgr.stopServices();
                }
                Log.d(TAG, MODULE + ": reinit switches");
                this.setSwitchesChecked(false);
            } else {
                for (LogOutput o: configArray) {
                    o.getConfSwitch().setChecked(configArray.indexOf(o) == id);
                }
            }
        } else {
            if (!curModConf.confTraceEnabled() && mtsMgr.getMtsState().equals("running")) {
                mtsMgr.stopServices();
            }
            this.updateText(this.mtsMgr.getMtsState(), tvMtsStatus);
            if (this.isExpertModeEnabled() && !curModConf.confTraceEnabled()) {
                if (curModConf.isMtsRequired() && mtsMgr.getMtsState().equals("running")) {
                    this.sExpertMode.setChecked(true);
                } else if (!curModConf.isMtsRequired()) {
                    this.sExpertMode.setChecked(true);
                }
            } else {
                SystemProperties.set(EXPERT_PROPERTY + modemName, "0");
                this.sExpertMode.setChecked(false);
                // if traces are not enabled or mts not started, uncheck all the conf switches
                if (!curModConf.confTraceEnabled() || (!mtsMgr.getMtsState().equals("running")
                        && curModConf.isMtsRequired())) {
                    this.setSwitchesChecked(false);
                // if traces are enabled, check the corresponding conf switch
                } else {
                    if (id == -2) {
                        LogOutput defaultConf = AMTLApplication.getDefaultConf();
                        if (defaultConf != null) {
                            defaultConf.getConfSwitch().setChecked(true);
                            SharedPreferences.Editor editor = AMTLApplication.getContext()
                                    .getSharedPreferences("AMTLPrefsData",
                                    Context.MODE_PRIVATE).edit();
                            editor.putInt("index" + modemName, defaultConf.getIndex());
                            editor.commit();
                        } else {
                            this.setSwitchesChecked(false);
                        }
                    } else {
                        if (this.configArray != null) {
                            for (LogOutput o: configArray) {
                                if (o != null && o.getConfSwitch() != null && o.getXsio() != null
                                        && o.getMtsOutput() != null
                                        && curModConf.getMtsConf() != null) {
                                    o.getConfSwitch().setChecked(o.getXsio()
                                            .equals(curModConf.getXsio()) && o.getMtsOutput()
                                            .equals(curModConf.getMtsConf().getOutput()));
                                }
                            }
                        }
                    }
                }
            }
        }

        this.updateText(curModConf.getOctMode(), tvOctStatus);
        this.updateText(this.mtsMgr.getMtsState(), tvMtsStatus);
    }

    public void setFirstCreated(boolean created) {
        this.firstCreated = created;
    }

    // retrieve the property value to know if expert mode is set
    private boolean isExpertModeEnabled() {
        boolean ret = false;
        String expertMode = SystemProperties.get(EXPERT_PROPERTY + modemName, null);
        if (expertMode != null) {
            if (expertMode.equals("1")) {
                ret = true;
            }
        }
        return ret;
    }

    private void setSwitchesEnabled(boolean enabled) {
        if (this.configArray != null) {
            for (LogOutput o: configArray) {
                o.getConfSwitch().setEnabled(enabled);
            }
        }
    }

    public void setSwitchesChecked(boolean checked) {
        if (this.configArray != null) {
            for (LogOutput o: configArray) {
                o.getConfSwitch().setChecked(checked);
            }
        }
    }

    // Function overrides for the Fragment instance.
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        gsfCb = (GSFCallBack) activity;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!AMTLApplication.getModemChanged()) {
            setupModemConf();
            gsfCb.onGeneralConfApplied(modemConfToApply);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        gsfCb = nullCb;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.getActivity().registerReceiver(mMessageReceiver, new IntentFilter("modem-event"));
        mtsMgr = new MtsManager();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!firstCreated && !AMTLApplication.getModemChanged()) {
            // update modem status when returning from another fragment
            if (ModemController.isModemUp()) {
                try {
                    ModemController mdmCtrl = ModemController.getInstance();
                    this.currentModemConf = checkModemConfig(mdmCtrl);
                    this.mtsMgr.printMtsProperties();
                    this.updateUi(this.currentModemConf);
                    this.setUIEnabled();
                    mdmCtrl = null;
                } catch (ModemControlException ex) {
                    Log.e(TAG, MODULE + ": fail to send command to the modem " + ex);
                }
            } else if (ModemController.getModemStatus() == ModemStatus.DOWN) {
                this.setUIDisabled();
            }
        }
    }

    @Override
    public void onDestroy() {
        this.getActivity().unregisterReceiver(mMessageReceiver);
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.generalsetupfraglayout, container, false);
        if (view != null) {

            this.sExpertMode = (Switch) view.findViewById(R.id.switchExpertMode);
            this.tvModemStatus = (TextView) view.findViewById(R.id.modemStatusValueTxt);
            this.tvMtsStatus = (TextView) view.findViewById(R.id.mtsStatusValueTxt);
            this.tvOctStatus = (TextView) view.findViewById(R.id.octStatusValueTxt);
            this.ll = (LinearLayout) view.findViewById(R.id.generalsetupfraglayout);

            // definition of switch listeners
            if (this.configArray != null) {
                for (LogOutput o: configArray) {
                    o.setConfigSwitch(ll, configArray.lastIndexOf(o), this.getActivity(), view);
                }
            }

            this.defineLogTagButton(view);
            this.defineCoreDumpButton(view);
        } else {

            UIHelper.exitDialog(this.getActivity(), "Error in UI", "View cannot be displayed.\n"
                    + "AMTL will exit.");
            Log.e(TAG, MODULE + ": context or view are null, AMTL will exit");
            // This is a UI bug - AMTL will end
        }

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (view != null) {

            if (this.configArray != null) {
                for (LogOutput o: configArray) {
                    if (view != null) {
                        ((Switch) view.findViewById(o.getSwitchId()))
                                .setOnCheckedChangeListener(this);
                    }
                }
            }
            if (this.sExpertMode != null) {
                this.sExpertMode.setOnCheckedChangeListener(this);
            }
            if (this.bGenCD != null) {
                this.bGenCD.setOnClickListener(this);
            }
            if (this.bLogTag != null) {
                this.bLogTag.setOnClickListener(this);
            }
        }
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");
            if (message != null) {
                if (message.equals("UP")) {
                    try {
                        ModemController mdmCtrl = ModemController.getInstance();
                        currentModemConf = checkModemConfig(mdmCtrl);
                        mtsMgr.printMtsProperties();
                        updateUi(currentModemConf);
                        setUIEnabled();
                        if (AMTLApplication.getPauseState()) {
                            // Application on Pause => close gsmtty
                            mdmCtrl.close();
                        }
                        mdmCtrl = null;
                    } catch (ModemControlException ex) {
                        Log.e(TAG, MODULE + ": fail to send command to the modem " + ex);
                    }
                    AMTLApplication.setCloseTtyEnable(true);
                } else if (message.equals("DOWN")) {
                    setUIDisabled();
                }
            }
        }
    };

    private ModemConf checkModemConfig(ModemController mdmCtrl) throws ModemControlException {

        return ModemConf.getInstance(mdmCtrl.checkAtXsioState(), mdmCtrl.checkAtTraceState(),
                mdmCtrl.checkAtXsystraceState(), "", mdmCtrl.checkOct());
    }

    public void defineCoreDumpButton(View view) {
        LinearLayout.LayoutParams appCoreDump = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        appCoreDump.setMargins(0, 50, 0, 0);
        this.bGenCD = new Button(this.getActivity());
        this.bGenCD.setId(this.genCDId);
        this.bGenCD.setGravity(Gravity.CENTER);
        this.bGenCD.setTextColor(Color.BLACK);
        this.bGenCD.setText("Generate CoreDump");
        ll.addView(this.bGenCD, appCoreDump);
        this.bGenCD = ((Button)view.findViewById(this.genCDId));
    }

    public void defineLogTagButton(View view) {
        LinearLayout.LayoutParams appLogTag = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        appLogTag.setMargins(0, 50, 0, 0);
        this.bLogTag = new Button(this.getActivity());
        this.bLogTag.setId(this.logTagId);
        this.bLogTag.setGravity(Gravity.CENTER);
        this.bLogTag.setTextColor(Color.BLACK);
        this.bLogTag.setText("Inject TAG in logs");
        ll.addView(this.bLogTag, appLogTag);
        this.bLogTag = ((Button)view.findViewById(this.logTagId));
    }

    private boolean setupModemConf() {
        if (!this.sExpertMode.isChecked()) {
            SystemProperties.set(EXPERT_PROPERTY + modemName, "0");
            if (this.configArray != null) {
                boolean buttonChecked = false;
                for (LogOutput o: configArray) {
                    if (o.getConfSwitch().isChecked()) {
                        modemConfToApply = ModemConf.getInstance(o);
                        modemConfToApply.activateConf(true);
                        buttonChecked = true;
                    }
                }
                if (!buttonChecked) {
                    // no modem configuration selected
                    ModemController mdmCtrl;
                    try {
                        mdmCtrl = ModemController.getInstance();
                        modemConfToApply = mdmCtrl.getNoLoggingConf();
                    } catch (ModemControlException ex) {
                        modemConfToApply = null;
                    } finally {
                        mdmCtrl = null;
                    }
                }
            }
        }

        SharedPreferences.Editor editor =
                AMTLApplication.getContext().getSharedPreferences("AMTLPrefsData",
                Context.MODE_PRIVATE).edit();

        if (modemConfToApply != null) {
            editor.putInt("index" + modemName, modemConfToApply.getIndex());
        } else {
            editor.remove("index" + modemName);
        }
        editor.commit();

        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (this.isIgnore) {
            /* Ignore this callback */
            this.isIgnore = false;
        } else {
            SharedPreferences prefs = this.getActivity().getSharedPreferences("AMTLPrefsData",
                    Context.MODE_PRIVATE);
            /* Determine current index */
            int currentIndex = prefs.getInt("index" + modemName, -2);

            this.updateText(this.mtsMgr.getMtsState(), tvMtsStatus);
            /* set config buttons exclusive by retrieving the id
             * of the button checked and disabling all the others */
            if (this.configArray != null) {
                int idChecked = -1;
                for (LogOutput o: configArray) {
                    if (buttonView.getId() == o.getSwitchId()) {
                        if (isChecked) {
                            idChecked = o.getSwitchId();
                        }
                    }
                }

                if (idChecked != -1) {
                    for (LogOutput o: configArray) {
                        if ((o.getSwitchId() != idChecked) && (o.getConfSwitch().isChecked())) {
                            /* We must ignore the next call to onCheckedChanged because
                            /* it will be due to te modification of this switch */
                            this.isIgnore = true;
                            o.getConfSwitch().setChecked(false);
                            break;
                        }
                    }
                }
            }
        }

        switch (buttonView.getId()) {
            case R.id.switchExpertMode:
                this.setSwitchesEnabled(!isChecked);
                if (isChecked) {
                    this.setSwitchesChecked(false);
                    if (null == expConf.getExpertConf() && !expConf.isConfigSet()) {
                        UIHelper.chooseExpertFile(this.getActivity(), "Choose expert config",
                                "Please select the file you want to apply:\n",
                                this.getActivity(), expConf, sExpertMode);
                    }
                } else {
                    expConf.setExpertConf(null);
                    expConf.setConfigSet(false);
                    SystemProperties.set(EXPERT_PROPERTY + modemName, "0");
                }
                break;
        }

        setupModemConf();
        gsfCb.onGeneralConfApplied(modemConfToApply);
    }

    @Override
    public void onClick(View view) {
        this.updateText(this.mtsMgr.getMtsState(), tvMtsStatus);

        switch (view.getId()) {
            case logTagId:
                UIHelper.logTagDialog(this.getActivity(), "Log TAG", "Please select the TAG"
                        + " you want to set in logs:\n", this.getActivity());
                break;
            case genCDId:
                try {
                    if (bGenCD != null) {
                        bGenCD.setEnabled(false);
                    }
                    ModemController mdmCtrl = ModemController.getInstance();
                    // When sending AT+XLOG=4, no response is given by the modem
                    mdmCtrl.generateModemCoreDump();
                    mdmCtrl = null;
                } catch (ModemControlException ex) {
                    Log.e(TAG, MODULE + ": fail to send command to the modem " + ex);
                }
                break;
        }
    }
}
