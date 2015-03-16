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

package com.intel.amtl.common.gui;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.os.Bundle;
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

import com.intel.amtl.common.AMTLApplication;
import com.intel.amtl.R;
import com.intel.amtl.common.exceptions.ModemControlException;
import com.intel.amtl.common.log.AlogMarker;
import com.intel.amtl.common.models.config.ExpertConfig;
import com.intel.amtl.common.models.config.LogOutput;
import com.intel.amtl.common.models.config.ModemConf;
import com.intel.amtl.common.modem.ModemController;
import com.intel.amtl.common.mts.MtsConf;
import com.intel.amtl.common.mts.MtsManager;

import java.io.File;
import java.io.IOException;
import java.lang.RuntimeException;
import java.util.ArrayList;


public class GeneralSetupFrag extends Fragment implements OnClickListener, OnCheckedChangeListener {

    private final String TAG = "AMTL";
    private final String MODULE = "GeneralSetupFrag";
    private final String BACKUP_LOG_PATH = "/data/amtl_logs_backup/";
    private final int logTagId = 100;
    private final int genCDId = 200;

    // Graphical objects for onClick handling.
    private Switch sExpertMode;
    private Button bLogTag;
    private Button bGenCD;
    private TextView tvModemStatus;
    private TextView tvMtsStatus;
    private TextView tvOctStatus;
    private TextView tvProfileName;
    private TextView tvProfileNameValue;

    // Callback object pointer. Active only when associated to Main layout.
    private GSFCallBack gsfCb = nullCb;

    private LinearLayout ll;
    private ArrayList<LogOutput> configArray;
    private ModemConf modemConfToApply;
    private ModemConf currentModemConf;
    private ExpertConfig expConf;
    private ModemController mdmCtrl;

    private String modemName;
    private Boolean firstCreated = true;

    // Used to not execute OnCheckedChanged()
    private Boolean isIgnore = false;

    private AlogMarker m = new AlogMarker();

    // Callback interface for Toast messages in Main layout.
    public interface GSFCallBack {
        public void onGeneralConfApplied(ModemConf conf);
    }

    public GeneralSetupFrag(ArrayList<LogOutput> outputArray, ExpertConfig expConf, String modem) {
        AlogMarker.tAB("GeneralSetupFrag.GeneralSetupFrag", "0");
        this.configArray = outputArray;
        this.expConf = expConf;
        this.modemName = modem;
        AlogMarker.tAE("GeneralSetupFrag.GeneralSetupFrag", "0");
    }

    public GeneralSetupFrag() {
    }

    private static GSFCallBack nullCb = new GSFCallBack() {
        public void onGeneralConfApplied(ModemConf conf) { }
    };

    private void updateText (String textToDisplay, TextView tv ) {
        AlogMarker.tAB("GeneralSetupFrag.updateText", "0");
        if (AMTLApplication.getUseMmgr()) {
            if (tv != null) {
                if (textToDisplay.equals("")) {
                    tv.setText("stopped");
                } else {
                    tv.setText(textToDisplay);
                }
            }
        }
        AlogMarker.tAE("GeneralSetupFrag.updateText", "0");
    }

    private void setUIEnabled() {
        AlogMarker.tAB("GeneralSetupFrag.setUIEnabled", "0");
        if (AMTLApplication.getUseMmgr()) {
            this.updateText("UP", tvModemStatus);
            this.updateText(MtsManager.getMtsState(), tvMtsStatus);
        }
        // only enable conf switches if expert property is set to 0
        this.setSwitchesEnabled(!ExpertConfig.isExpertModeEnabled(modemName));
        if (this.sExpertMode != null) {
            this.sExpertMode.setEnabled(true);
        }
        if (this.bGenCD != null) {
            this.bGenCD.setEnabled(true);
        }
        AlogMarker.tAE("GeneralSetupFrag.setUIEnabled", "0");
    }

    private void setUIDisabled() {
        AlogMarker.tAB("GeneralSetupFrag.setUIDisabled", "0");
        if (AMTLApplication.getUseMmgr()) {
            this.updateText("DOWN", tvModemStatus);
            this.updateText(MtsManager.getMtsState(), tvMtsStatus);
        }

        this.setSwitchesEnabled(false);
        if (this.sExpertMode != null) {
            this.sExpertMode.setEnabled(false);
        }
        if (this.bGenCD != null) {
            this.bGenCD.setEnabled(false);
        }
        AlogMarker.tAE("GeneralSetupFrag.setUIDisabled", "0");
    }

    private void updateUi(ModemConf curModConf) {
        AlogMarker.tAB("GeneralSetupFrag.upddateUi", "0");
        SharedPreferences prefs = this.getActivity().getSharedPreferences("AMTLPrefsData",
                Context.MODE_PRIVATE);
        int id = prefs.getInt("index" + modemName, -2);
        int updatedId = mdmCtrl.getConfigManager().updateCurrentIndex(curModConf, id, modemName,
                mdmCtrl, configArray);
        Editor editor = prefs.edit();
        editor.putInt("index" + modemName, updatedId);
        editor.commit();

        if (ExpertConfig.isExpertModeEnabled(modemName)) {
            this.sExpertMode.setChecked(true);
        } else if (updatedId <= -1) {
            this.setSwitchesChecked(false);
        } else {
            if (configArray != null) {
                for (LogOutput o: configArray) {
                    o.getConfSwitch().setChecked(configArray.indexOf(o) == updatedId);
                }
            }
        }

        if (AMTLApplication.getUseMmgr()) {
            this.updateText(curModConf.getOctMode(), tvOctStatus);
            this.updateText(MtsManager.getMtsState(), tvMtsStatus);
            if (AMTLApplication.getIsAliasUsed()) {
                this.updateText(curModConf.getProfileName(), tvProfileNameValue);
            }
        }
        AlogMarker.tAE("GeneralSetupFrag.upddateUi", "0");
    }

    public void setFirstCreated(boolean created) {
        AlogMarker.tAB("GeneralSetupFrag.setFirstCreated", "0");
        this.firstCreated = created;
        AlogMarker.tAE("GeneralSetupFrag.setFirstCreated", "0");
    }

    private void setSwitchesEnabled(boolean enabled) {
        AlogMarker.tAB("GeneralSetupFrag.setSwitchesEnabled", "0");
        if (this.configArray != null) {
            for (LogOutput o: configArray) {
                o.getConfSwitch().setEnabled(enabled);
            }
        }
        AlogMarker.tAE("GeneralSetupFrag.setSwitchesEnabled", "0");
    }

    public void setSwitchesChecked(boolean checked) {
        AlogMarker.tAB("GeneralSetupFrag.setSwitchesChecked", "0");
        if (this.configArray != null) {
            for (LogOutput o: configArray) {
                o.getConfSwitch().setChecked(checked);
            }
        }
        AlogMarker.tAE("GeneralSetupFrag.setSwitchesChecked", "0");
    }

    // Function overrides for the Fragment instance.
    @Override
    public void onAttach(Activity activity) {
        AlogMarker.tAB("GeneralSetupFrag.onAttach", "0");
        super.onAttach(activity);
        gsfCb = (GSFCallBack) activity;
        AlogMarker.tAE("GeneralSetupFrag.onAttach", "0");
    }

    @Override
    public void onPause() {
        AlogMarker.tAB("GeneralSetupFrag.onPause", "0");
        super.onPause();
        if (!AMTLApplication.getModemChanged()) {
            setupModemConf();
            gsfCb.onGeneralConfApplied(modemConfToApply);
        }
        mdmCtrl = null;
        AlogMarker.tAE("GeneralSetupFrag.onPause", "0");
    }

    @Override
    public void onDetach() {
        AlogMarker.tAB("GeneralSetupFrag.onDetach", "0");
        super.onDetach();
        gsfCb = nullCb;
        AlogMarker.tAE("GeneralSetupFrag.onDetach", "0");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        AlogMarker.tAB("GeneralSetupFrag.onCreate", "0");
        super.onCreate(savedInstanceState);
        this.getActivity().registerReceiver(mMessageReceiver, new IntentFilter("modem-event"));
        AlogMarker.tAE("GeneralSetupFrag.onCreate", "0");
    }

    @Override
    public void onResume() {
        AlogMarker.tAB("GeneralSetupFrag.onResume", "0");
        super.onResume();
        if (!firstCreated && !AMTLApplication.getModemChanged()) {
            // update modem status when returning from another fragment
            try {
                mdmCtrl = ModemController.getInstance();
                if (mdmCtrl != null && mdmCtrl.isModemUp()) {
                    this.currentModemConf = checkModemConfig(mdmCtrl);
                    this.currentModemConf.setMtsConf(checkMtsConfig());
                    this.currentModemConf.setMtsMode(MtsManager.getMtsMode());
                    if (AMTLApplication.getIsAliasUsed()) {
                        this.currentModemConf.setProfileName(mdmCtrl.checkProfileName());
                    }
                    if (this.currentModemConf != null) {
                        this.updateUi(this.currentModemConf);
                    }
                    this.setUIEnabled();
                } else {
                    this.setUIDisabled();
                }
            } catch (ModemControlException ex) {
                Log.e(TAG, MODULE + ": fail to send command to the modem " + ex);
                this.mdmCtrl = null;
            }
        }
        AlogMarker.tAE("GeneralSetupFrag.onResume", "0");
    }

    @Override
    public void onDestroy() {
        AlogMarker.tAB("GeneralSetupFrag.onDestroy", "0");
        this.getActivity().unregisterReceiver(mMessageReceiver);
        super.onDestroy();
        AlogMarker.tAE("GeneralSetupFrag.onDestroy", "0");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        AlogMarker.tAB("GeneralSetupFrag.onCreateView", "0");

        View view = inflater.inflate(R.layout.generalsetupfraglayout, container, false);
        if (view != null) {

            this.sExpertMode = (Switch) view.findViewById(R.id.switchExpertMode);
            this.tvModemStatus = (TextView) view.findViewById(R.id.modemStatusValueTxt);
            this.tvMtsStatus = (TextView) view.findViewById(R.id.mtsStatusValueTxt);
            this.tvOctStatus = (TextView) view.findViewById(R.id.octStatusValueTxt);
            this.tvProfileName = (TextView) view.findViewById(R.id.profileNameTxt);
            this.tvProfileNameValue = (TextView) view.findViewById(R.id.profileNameValueTxt);
            this.ll = (LinearLayout) view.findViewById(R.id.generalsetupfraglayout);

            // definition of switch listeners
            if (this.configArray != null) {
                for (LogOutput o: configArray) {
                    o.setConfigSwitch(ll, configArray.lastIndexOf(o), this.getActivity(), view);
                }
            }

            this.defineLogTagButton(view);
            if (AMTLApplication.getUseMmgr()) {
                this.defineCoreDumpButton(view);
            }
            if (!AMTLApplication.getIsAliasUsed()) {
                this.tvProfileName.setVisibility(View.GONE);
                this.tvProfileNameValue.setVisibility(View.GONE);
            }
        } else {

            UIHelper.exitDialog(this.getActivity(), "Error in UI", "View cannot be displayed.\n"
                    + "AMTL will exit.");
            Log.e(TAG, MODULE + ": context or view are null, AMTL will exit");
            // This is a UI bug - AMTL will end
        }
        AlogMarker.tAE("GeneralSetupFrag.onCreateView", "0");

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AlogMarker.tAB("GeneralSetupFrag.onViewCreated", "0");
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
        AlogMarker.tAE("GeneralSetupFrag.onViewCreated", "0");
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            AlogMarker.tAB("GeneralSetupFrag.mMessageReceiver.onReceive", "0");
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");
            if (message != null) {
                if (message.equals("UP")) {
                    try {
                        mdmCtrl = ModemController.getInstance();
                        if (mdmCtrl != null) {
                            currentModemConf = checkModemConfig(mdmCtrl);
                            currentModemConf.setMtsConf(checkMtsConfig());
                            currentModemConf.setMtsMode(MtsManager.getMtsMode());
                            if (AMTLApplication.getIsAliasUsed()) {
                                currentModemConf.setProfileName(mdmCtrl.checkProfileName());
                            }
                            if (currentModemConf != null) {
                                updateUi(currentModemConf);
                            }
                            setUIEnabled();
                            if (AMTLApplication.getPauseState()) {
                                // when the application is paused, modem interface needs to be
                                // closed
                                mdmCtrl.closeModemInterface();
                            }
                        } else {
                            setUIDisabled();
                        }
                    } catch (ModemControlException ex) {
                        Log.e(TAG, MODULE + ": fail to send command to the modem " + ex);
                    }
                    AMTLApplication.setCloseTtyEnable(true);
                } else if (message.equals("DOWN")) {
                    setUIDisabled();
                }
            }
            AlogMarker.tAE("GeneralSetupFrag.mMessageReceiver.onReceive", "0");
        }
    };

    private ModemConf checkModemConfig(ModemController mdmCtrl) throws ModemControlException {
        AlogMarker.tAB("GeneralSetupFrag.checkModemConfig", "0");
        AlogMarker.tAE("GeneralSetupFrag.checkModemConfig", "0");
        return ModemConf.getInstance(mdmCtrl.checkAtXsioState(), mdmCtrl.checkAtTraceState(),
                mdmCtrl.checkAtXsystraceState(), "", mdmCtrl.checkOct());
    }

    private MtsConf checkMtsConfig() {
        AlogMarker.tAB("GeneralSetupFrag.checkMtsConfig", "0");
        MtsConf conf = new MtsConf(MtsManager.getMtsInput(), MtsManager.getMtsOutput(),
                MtsManager.getMtsOutputType(), MtsManager.getMtsRotateNum(),
                MtsManager.getMtsRotateSize(), MtsManager.getMtsInterface(),
                MtsManager.getMtsBufferSize());
        MtsManager.printMtsProperties();
        AlogMarker.tAE("GeneralSetupFrag.checkMtsConfig", "0");
        return conf;
    }

    public void defineCoreDumpButton(View view) {
        AlogMarker.tAB("GeneralSetupFrag.defineCoreDumpButton", "0");
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
        AlogMarker.tAE("GeneralSetupFrag.defineCoreDumpButton", "0");
    }

    public void defineLogTagButton(View view) {
        AlogMarker.tAB("GeneralSetupFrag.defineLogTagButton", "0");
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
        AlogMarker.tAE("GeneralSetupFrag.defineLogTagButton", "0");
    }

    private boolean setupModemConf() {
        AlogMarker.tAB("GeneralSetupFrag.setupModemConf", "0");
        if (!this.sExpertMode.isChecked()) {
            ExpertConfig.setExpertMode(modemName, false);
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
                    if (mdmCtrl != null) {
                        modemConfToApply = mdmCtrl.getNoLoggingConf();
                    }
                }
            }
        }

        Editor editor = AMTLApplication.getContext().getSharedPreferences("AMTLPrefsData",
                Context.MODE_PRIVATE).edit();

        if (modemConfToApply != null) {
            editor.putInt("index" + modemName, modemConfToApply.getIndex());
        } else {
            editor.remove("index" + modemName);
        }
        editor.commit();
        AlogMarker.tAE("GeneralSetupFrag.setupModemConf", "0");

        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        AlogMarker.tAB("GeneralSetupFrag.onCheckedChanged", "0");
        if (this.isIgnore) {
            /* Ignore this callback */
            this.isIgnore = false;
        } else {
            SharedPreferences prefs = this.getActivity().getSharedPreferences("AMTLPrefsData",
                    Context.MODE_PRIVATE);
            /* Determine current index */
            int currentIndex = prefs.getInt("index" + modemName, -2);

            this.updateText(MtsManager.getMtsState(), tvMtsStatus);
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
                    ExpertConfig.setExpertMode(modemName, false);
                }
                break;
        }

        setupModemConf();
        gsfCb.onGeneralConfApplied(modemConfToApply);
        AlogMarker.tAE("GeneralSetupFrag.onCheckedChanged", "0");
    }

    @Override
    public void onClick(View view) {
        AlogMarker.tAB("GeneralSetupFrag.onClick", "0");
        this.updateText(MtsManager.getMtsState(), tvMtsStatus);

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
                    mdmCtrl.generateModemCoreDump();
                } catch (ModemControlException ex) {
                    Log.e(TAG, MODULE + ": fail to send command to the modem " + ex);
                }
                break;
        }
        AlogMarker.tAE("GeneralSetupFrag.onClick", "0");
    }
}
