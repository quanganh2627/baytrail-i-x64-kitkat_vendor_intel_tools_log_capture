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
import android.app.AlarmManager;
import android.app.Application;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.intel.amtl.common.AMTLApplication;
import com.intel.amtl.common.config_parser.ConfigParser;
import com.intel.amtl.common.exceptions.ModemControlException;
import com.intel.amtl.common.exceptions.ParsingException;
import com.intel.amtl.common.helper.TelephonyStack;
import com.intel.amtl.common.log.AlogMarker;
import com.intel.amtl.common.models.config.ExpertConfig;
import com.intel.amtl.common.models.config.LogOutput;
import com.intel.amtl.common.models.config.ModemConf;
import com.intel.amtl.common.models.config.ModemLogOutput;
import com.intel.amtl.common.modem.ModemController;
import com.intel.amtl.common.platform.Platform;
import com.intel.amtl.R;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

// AMTL is using a sharedPreferences file to store volatile data.
// As this is spread through the classes, here the summary of the
// stored data:
// @index (int) the current executed configuration
// @default_flush_cmd (string) if populated, the command to execute to flush to NVM

public class AMTLTabLayout extends Activity implements GeneralSetupFrag.GSFCallBack,
        MasterSetupFrag.OnModeChanged, LogcatTraces.OnLogcatTraceModeApplied,
        SystemStatsTraces.OnSystemStatsTraceModeApplied {

    private final String TAG = "AMTL";
    private final String MODULE = "AMTLTabLayout";
    private final String MDM_CONNECTION_TAG = "AMTL_modem_connection";

    // Tab list
    private final String TAB1_FRAG_NAME = "Modem setup";
    private final String TAB2_FRAG_NAME = "Advanced options";
    private final String TAB3_FRAG_NAME = "Android Log";

    private String currentCatalogPath = null;

    private TabHost mTabHost;

    // Target fragment for progress popup.
    private FragmentManager fragManager;

    private GeneralSetupFrag tab1_frag;
    private MasterSetupFrag tab2_frag;
    private LogcatTraceFrag tab3_frag;
    private ActionMenu actionMenu;

    private ConfigParser configParser = null;
    private ArrayList<LogOutput> configOutputs = null;
    private ArrayList<ModemLogOutput> modemConfigOutputs = null;
    private static ArrayList<String> modemNames;
    private static ExpertConfig expConf;
    private static ModemConf generalModemConf = null;
    private static GeneralTracing logcatTraces = null;
    private static GeneralTracing modemTraces = null;
    private static GeneralTracing systemStatsTraces = null;

    private MenuItem settingsMenu;

    // Fragment to display progress dialog during modem connection
    private ModemConnectionFrag mdmConnectFrag = null;

    // Handle modem connection
    private ModemController mdmCtrl;

    private Platform platform = null;

    private Boolean buttonChanged = false;
    private Boolean firstCreated = true;

    private static int currentLoggingModem = 0;

    // Telephony stack check - in order to enable it if disabled
    private TelephonyStack telStackSetter;

    private AlogMarker m = new AlogMarker();
    private static AlogMarker ms = new AlogMarker();

    private void loadConfiguration() throws ParsingException {
        AlogMarker.tAB("AMTLTabLayout.loadConfiguration", "0");
        FileInputStream fin = null;
        SharedPreferences prefs = this.getSharedPreferences("AMTLPrefsData", Context.MODE_PRIVATE);
        Editor editor = prefs.edit();

        Log.d(TAG, MODULE + ": Will remove default_flush_cmd entry.");
        editor.remove("default_flush_cmd");
        editor.commit();

        try {
            // Use of getXmlPlatform
            AMTLApplication.setModemChanged(false);
            this.platform = new Platform();
            this.currentCatalogPath = this.platform.getPlatformConf();

            Log.d(TAG, MODULE + ": Will load " + this.currentCatalogPath + " configuration file");
            fin = new FileInputStream(this.currentCatalogPath);

            if (fin != null) {
                this.modemConfigOutputs
                        = new ArrayList<ModemLogOutput>(this.configParser.parseConfig(fin));
            }
        } catch (FileNotFoundException ex) {
            throw new ParsingException("Cannot load config file " + ex.getMessage());
        } catch (XmlPullParserException ex) {
            throw new ParsingException("Cannot load config file " + ex.getMessage());
        } catch (IOException ex) {
            throw new ParsingException("Cannot load config file " + ex.getMessage());
        } finally {
            if (fin != null) {
                try {
                    fin.close();
                } catch (IOException ex) {
                    Log.e(TAG, MODULE + ": Error during close " + ex);
                }
            }
        }
        this.setConfigArray();
        if (expConf.isExpertModeEnabled(modemNames.get(currentLoggingModem))) {
            ExpertConfig.setConfigSet(true);
        }
        this.updateLoggingPreferences();

        AlogMarker.tAE("AMTLTabLayout.loadConfiguration", "0");
    }

    private void setConfigArray() {
        AlogMarker.tAB("AMTLTabLayout.setConfigArray", "0");
        String curModem = PreferenceManager.getDefaultSharedPreferences(this)
               .getString(this.getString(R.string.settings_modem_name_key), "0");
        currentLoggingModem = Integer.parseInt(curModem);
        modemNames = new ArrayList<String>();

        for (ModemLogOutput m: modemConfigOutputs) {
            modemNames.add(modemConfigOutputs.indexOf(m), m.getName());
        }

        AMTLApplication.setModemNameList(modemNames);

        ModemLogOutput currModemLogOut = new ModemLogOutput();
        currModemLogOut = modemConfigOutputs.get(currentLoggingModem);
        if (currModemLogOut != null) {
            currModemLogOut.printToLog();
            configOutputs = new ArrayList<LogOutput>();
            configOutputs.addAll(currModemLogOut.getOutputList());
            setModemParameters(currModemLogOut);
        }
        AlogMarker.tAE("AMTLTabLayout.setConfigArray", "0");
    }

    private void setModemParameters(ModemLogOutput mdmLogOutput) {
        AlogMarker.tAB("AMTLTabLayout.setModemParameters", "0");
        AMTLApplication.setModemConnectionId(mdmLogOutput.getConnectionId());
        AMTLApplication.setDefaultConf(mdmLogOutput.getDefaultConfig());
        AMTLApplication.setModemInterface(mdmLogOutput.getModemInterface());
        AMTLApplication.setTraceLegacy(mdmLogOutput.getAtLegacyCmd());
        AMTLApplication.setServiceToStart(mdmLogOutput.getServiceToStart());
        AMTLApplication.setUseMmgr(mdmLogOutput.getUseMmgr());
        AlogMarker.tAE("AMTLTabLayout.setModemParameters", "0");
    }

    private void updateLoggingPreferences() {
        AlogMarker.tAB("AMTLTabLayout.updateLoggingPreferences", "0");
        Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
        edit.putString(this.getString(R.string.settings_user_save_path_key),
                AMTLApplication.getApLoggingPath());
        edit.putString(this.getString(R.string.settings_save_path_key),
                AMTLApplication.getApLoggingPath());
        edit.commit();
        AlogMarker.tAE("AMTLTabLayout.updateLoggingPreferences", "0");
    }

    public void initializeTab() {
        AlogMarker.tAB("AMTLTabLayout.initializeTab", "0");
        TabHost.TabSpec spec = mTabHost.newTabSpec(TAB3_FRAG_NAME);
        spec.setContent(new TabHost.TabContentFactory() {
            public View createTabContent(String tag) {
                return findViewById(android.R.id.tabcontent);
            }
        });
        spec.setIndicator(createTabView(TAB3_FRAG_NAME));
        mTabHost.addTab(spec);

        spec = mTabHost.newTabSpec(TAB1_FRAG_NAME);
        spec.setContent(new TabHost.TabContentFactory() {
            public View createTabContent(String tag) {
                return findViewById(android.R.id.tabcontent);
            }
        });
        spec.setIndicator(createTabView(TAB1_FRAG_NAME));
        mTabHost.addTab(spec);

        spec = mTabHost.newTabSpec(TAB2_FRAG_NAME);
        spec.setContent(new TabHost.TabContentFactory() {
            public View createTabContent(String tag) {
                return findViewById(android.R.id.tabcontent);
            }
        });
        spec.setIndicator(createTabView(TAB2_FRAG_NAME));
        mTabHost.addTab(spec);

        // Focus on first tab
        mTabHost.setCurrentTab(1);
        AlogMarker.tAE("AMTLTabLayout.initializeTab", "0");
    }

    TabHost.OnTabChangeListener listener = new TabHost.OnTabChangeListener() {
        public void onTabChanged(String tabId) {
            if (AMTLApplication.getModemChanged()) {
                firstCreated = true;
            }
            if (tabId.equals(TAB1_FRAG_NAME)) {
                pushFragments(tabId, tab1_frag);
                tab1_frag.setFirstCreated(firstCreated);
            } else if (tabId.equals(TAB2_FRAG_NAME)) {
                pushFragments(tabId, tab2_frag);
                tab2_frag.setButtonChanged(buttonChanged);
            } else if(tabId.equals(TAB3_FRAG_NAME)) {
                pushFragments(tabId, tab3_frag);
            }

            firstCreated = false;
        }
    };

    public void pushFragments(String tag, Fragment fragment){
        AlogMarker.tAB("AMTLTabLayout.pushFragments", "0");

        FragmentManager manager = getFragmentManager();
        FragmentTransaction ft = manager.beginTransaction();

        ft.replace(android.R.id.tabcontent, fragment);
        ft.commit();
        AlogMarker.tAE("AMTLTabLayout.pushFragments", "0");
    }

    /*
     * returns the tab view i.e. the tab text
     */
    private View createTabView(final String text) {
        AlogMarker.tAB("AMTLTabLayout.createTabView", "0");
        View view = LayoutInflater.from(this).inflate(R.layout.tab_button, null);
        if (view != null) {
            ((TextView) view.findViewById(R.id.tab_button_text)).setText(text);
        } else {
            UIHelper.exitDialog(this, "Error on UI", "View cannot be displayed, AMTL will exit");
        }
        AlogMarker.tAE("AMTLTabLayout.createTabView", "0");
        return view;
    }

    // Activity overrides.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AlogMarker.tAB("AMTLTabLayout.onCreate", "0");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.amtltablayout);
        Log.d(TAG, MODULE + ": creation of AMTL activity ");
        telStackSetter = new TelephonyStack();
        ((AMTLApplication) this.getApplication()).setContext(getBaseContext());

        if (!telStackSetter.isEnabled()) {
            UIHelper.messageSetupTelStack(this,
                    "Telephony stack disabled !", "Would you like to enable it ?",
                    telStackSetter);
        } else {
            try {
                this.configParser = new ConfigParser(this);
                this.loadConfiguration();

            } catch (ParsingException ex) {
                Log.e(TAG, MODULE + "Error during XML configuration file parsing: "
                        + ex.getMessage());
            }
            this.firstCreated = true;
            expConf = new ExpertConfig(this);
            tab1_frag = new GeneralSetupFrag(this.configOutputs, expConf,
                    modemNames.get(currentLoggingModem));
            tab2_frag = new MasterSetupFrag();
            tab3_frag = new LogcatTraceFrag();
            actionMenu = new ActionMenu(this);
            modemTraces = new ModemTraces(this, tab1_frag);
            logcatTraces = new LogcatTraces(this);

            ViewGroup inclusionViewGroup = (ViewGroup) this.findViewById(android.R.id.content)
                    .findViewById(R.id.actionmenubar);
            View child = getLayoutInflater().inflate(actionMenu.getViewID(), null);
            inclusionViewGroup.addView(child);
            actionMenu.attachReferences(this.findViewById(android.R.id.content));
            actionMenu.attachListeners();

            mTabHost = (TabHost) findViewById(android.R.id.tabhost);
            if (mTabHost != null) {
                mTabHost.setOnTabChangedListener(listener);
                mTabHost.setup();
            }

            initializeTab();

            fragManager = getFragmentManager();

            // fragment to display progress dialog during modem connection
            mdmConnectFrag = new ModemConnectionFrag();

            try {
                // instantiation of mdmCtrl to be able to connect and disconnect when exiting AMTL
                this.mdmCtrl = ModemController.getInstance();
            } catch (ModemControlException ex) {
                Log.e(TAG, MODULE + ": connection to Modem Status Manager failed");
                UIHelper.exitDialog(this,
                        "Modem connection failed ", "AMTL will exit: " + ex.getMessage());
            }

            // launch connection to modem with a progress dialog
            if (mdmConnectFrag != null) {
                mdmConnectFrag.handlerConn();
                mdmConnectFrag.show(fragManager, MDM_CONNECTION_TAG);
            }
        }
        AlogMarker.tAE("AMTLTabLayout.onCreate", "0");
    }

    @Override
    public void onDestroy() {
        AlogMarker.tAB("AMTLTabLayout.onDestroy", "0");
        Log.d(TAG, MODULE + ": onDestroy AMTL activity");
        if (this.mdmCtrl != null) {
            this.mdmCtrl.cleanBeforeExit();
            this.mdmCtrl = null;
        }
        super.onDestroy();
        AlogMarker.tAE("AMTLTabLayout.onDestroy", "0");
    }

    @Override
    public void onResume() {
        AlogMarker.tAB("AMTLTabLayout.onResume", "0");
        super.onResume();
        AMTLApplication.setPauseState(false);
        try {
            String curModem = PreferenceManager.getDefaultSharedPreferences(this)
                    .getString(this.getString(R.string.settings_modem_name_key), "0");
            int readModem = Integer.parseInt(curModem);
            if (readModem != currentLoggingModem) {
                currentLoggingModem = readModem;
                AMTLApplication.setModemChanged(true);

                if (this.mdmCtrl != null) {
                    this.mdmCtrl.cleanBeforeExit();
                    this.mdmCtrl = null;
                }

                // If the modem has changed, exit and start AMTL again
                Intent startActivity = getIntent();
                int pendingIntentId = 123456;
                PendingIntent pendingIntent = PendingIntent.getActivity(this, pendingIntentId,
                        startActivity, PendingIntent.FLAG_CANCEL_CURRENT);
                AlarmManager mgr = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
                mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1, pendingIntent);
                finish();

            } else {
                if (this.mdmCtrl != null) {
                    this.mdmCtrl.acquireResource();
                    if (this.mdmCtrl.isModemAcquired() && this.mdmCtrl.isModemUp()) {
                        this.mdmCtrl.openModemInterface();
                    }
                }
            }
        } catch (ModemControlException ex) {
            Log.e(TAG, MODULE + ex);
            UIHelper.exitDialog(this, "Connection failed", "AMTL will exit: " + ex);
        }
        AlogMarker.tAE("AMTLTabLayout.onResume", "0");
    }

    @Override
    public void onPause() {
        AlogMarker.tAB("AMTLTabLayout.onPause", "0");
        super.onPause();
        AMTLApplication.setPauseState(true);
        if (this.mdmCtrl != null) {
            this.mdmCtrl.releaseResource();
            if (AMTLApplication.getCloseTtyEnable()) {
                // Close Modem Interface
                this.mdmCtrl.closeModemInterface();
            }
        }
        AlogMarker.tAE("AMTLTabLayout.onPause", "0");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        AlogMarker.tAB("AMTLTabLayout.onCreateOptionsMenu", "0");
        settingsMenu = menu.add(R.string.settings_menu);
        super.onCreateOptionsMenu(menu);
        AlogMarker.tAE("AMTLTabLayout.onCreateOptionsMenu", "0");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        AlogMarker.tAB("AMTLTabLayout.onOptionsItemSelected", "0");
        if (item.equals(settingsMenu)) {
            Intent intent = new Intent(getApplicationContext(), AMTLSettings.class);
            intent.putStringArrayListExtra("modem", modemNames);
            startActivity(intent);
            AlogMarker.tAE("AMTLTabLayout.onOptionsItemSelected", "0");
            return true;
        }
        Toast.makeText(this, "Clicked on something", Toast.LENGTH_LONG).show();
        AlogMarker.tAE("AMTLTabLayout.onOptionsItemSelected", "0");
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onLogcatTraceConfApplied(GeneralTracing conf) {
        AlogMarker.tAB("AMTLTabLayout.onLogcatTraceConfApplied", "0");
        if (conf != null) {
            this.logcatTraces = conf;
        }
        AlogMarker.tAE("AMTLTabLayout.onLogcatTraceConfApplied", "0");
    }

    @Override
    public void onGeneralConfApplied(ModemConf conf) {
        AlogMarker.tAB("AMTLTabLayout.onGeneralConfApplied", "0");
        this.generalModemConf = conf;
        AlogMarker.tAE("AMTLTabLayout.onGeneralConfApplied", "0");
    }

    @Override
    public void onModeChanged(Boolean changed) {
        AlogMarker.tAB("AMTLTabLayout.onModeChanged", "0");
        if (changed != null) {
            this.buttonChanged = changed;
        }
        AlogMarker.tAE("AMTLTabLayout.onModeChanged", "0");
    }

    @Override
    public void onSystemStatsTraceConfApplied(GeneralTracing conf) {
        AlogMarker.tAB("AMTLTabLayout.onSystemStatsTraceConfApplied", "0");
        if (conf != null) {
            this.systemStatsTraces = conf;
        }
        AlogMarker.tAE("AMTLTabLayout.onSystemStatsTraceConfApplied", "0");
    }

    // Embedded class to handle connection to modem (Dialog part).
    public static class ModemConnectionFrag extends DialogFragment implements Handler.Callback {
        final static int MSG_CONNECTION_SUCCESS = 0;
        final static int MSG_CONNECTION_FAIL = 1;
        private AlogMarker m = new AlogMarker();
        ProgressBar connectProgNot;
        // thread executed while Dialog Box is displayed.
        ConnectModemTask connectMdm;

        public void handlerConn() {
            // This allows to get ModemConnectTerminated on the specified Fragment.
            AlogMarker.tAB("AMTLTabLayout.ModemConnectionFrag.handlerConn", "0");
            connectMdm = new ConnectModemTask(this);
            AlogMarker.tAE("AMTLTabLayout.ModemConnectionFrag.handlerConn", "0");
        }

        public void ModemConnectTerminated(String exceptReason) {
            /* dismiss() is possible only if we are on the current Activity.
            And will crash if we have switched to another one.*/
            AlogMarker.tAB("AMTLTabLayout.ModemConnectionFrag.ModemConnectTerminated", "0");
            if (isResumed()) {
                dismiss();
            }

            // if the modem connection thread has returned an exception, AMTL exits
            if (exceptReason != null) {
                UIHelper.exitDialog(getActivity(),
                        "Modem connection failed ", "AMTL will exit: " + exceptReason);
            }
            connectMdm = null;
            AlogMarker.tAE("AMTLTabLayout.ModemConnectionFrag.ModemConnectTerminated", "0");
        }

        // Function overrides for the DialogFragment instance.
        @Override
        public void onCreate(Bundle savedInstanceState) {
            AlogMarker.tAB("AMTLTabLayout.ModemConnectionFrag.onCreate", "0");
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
            // Spawn the thread to connect to modem.
            if (connectMdm != null) {
                connectMdm.start();
            }
            AlogMarker.tAE("AMTLTabLayout.ModemConnectionFrag.onCreate", "0");
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            // Create dialog box.
            AlogMarker.tAB("AMTLTabLayout.ModemConnectionFrag.onCreateView", "0");
            View view = inflater.inflate(R.layout.fragment_task, container);
            connectProgNot = (ProgressBar)view.findViewById(R.id.progressBar);
            getDialog().setTitle("Connection to modem");
            setCancelable(false);

            AlogMarker.tAE("AMTLTabLayout.ModemConnectionFrag.onCreateView", "0");
            return view;
        }

        @Override
        public void onDestroyView() {
            /* This will allow dialog box to stay even if parent layout configuration is
            changed (rotation). */
            AlogMarker.tAB("AMTLTabLayout.ModemConnectionFrag.onDestroyView", "0");
            if (getDialog() != null && getRetainInstance()) {
                getDialog().setDismissMessage(null);
            }
            super.onDestroyView();
            AlogMarker.tAE("AMTLTabLayout.ModemConnectionFrag.onDestroyView", "0");
        }

        @Override
        public void onResume() {
            AlogMarker.tAB("AMTLTabLayout.ModemConnectionFrag.onResume", "0");
            /* This allows to close dialog box if the thread ends while we are not focused
            on the activity.*/
            super.onResume();
            if (connectMdm == null) {
                dismiss();
            }
            AlogMarker.tAE("AMTLTabLayout.ModemConnectionFrag.onResume", "0");
        }

        @Override
        public boolean handleMessage(Message msg) {
            AlogMarker.tAB("AMTLTabLayout.ModemConnectionFrag.handleMessage", "0");
            switch (msg.what) {
                case MSG_CONNECTION_SUCCESS:
                    ModemConnectTerminated(null);
                    break;
                case MSG_CONNECTION_FAIL:
                    String message = (String)msg.obj;
                    ModemConnectTerminated(message);
                    break;
            }
            AlogMarker.tAE("AMTLTabLayout.ModemConnectionFrag.handleMessage", "0");
            return true;
        }
    }

    // embedded class to handle modem connection (thread part).
    public static class ConnectModemTask implements Runnable {
        private final String TAG = "AMTL";
        private final String MODULE = "ConnectModemTask";
        private static final long RETRY_DELAY_MS = 20000;
        private static final int NUM_RETRIES = 3;
        private Handler mHandler;
        private ModemController mdmCtrl;
        private Thread mThread;
        private AlogMarker m = new AlogMarker();

        public ConnectModemTask(ModemConnectionFrag modConnFrag) {
            AlogMarker.tAB("AMTLTabLayout.ConnectModemTask.ConnectModemTask", "0");
            mHandler = new Handler(modConnFrag);
            AlogMarker.tAE("AMTLTabLayout.ConnectModemTask.ConnectModemTask", "0");
        }

        public void start() {
            AlogMarker.tAB("AMTLTabLayout.ConnectModemTask.start", "0");
            mThread = new Thread(this);
            mThread.setName("AMTL Modem connection");
            mThread.start();
            AlogMarker.tAE("AMTLTabLayout.ConnectModemTask.start", "0");
        }

        // Function overrides for modem connection thread.
        public void run() {
            AlogMarker.tAB("AMTLTabLayout.ConnectModemTask.run", "0");
            String exceptReason = null;
            try {
                mdmCtrl = ModemController.getInstance();
            } catch (ModemControlException ex) {
                mdmCtrl = null;
                exceptReason = ex.getMessage();
                Log.e(TAG, MODULE + ": Connection to modem failed: "
                        + exceptReason);
                if (mHandler != null)
                    mHandler.obtainMessage(ModemConnectionFrag.MSG_CONNECTION_FAIL,
                            exceptReason).sendToTarget();
                AlogMarker.tAE("AMTLTabLayout.ConnectModemTask.run", "0");
                return;
            }

            int retry = 0;
            boolean connected = false;
            while (!connected && retry < NUM_RETRIES) {
                try {
                    retry++;
                    mdmCtrl.connectToModem();
                    connected = true;
                } catch (ModemControlException ex) {
                    exceptReason = ex.getMessage();
                    Log.e(TAG, MODULE + ": Connection to modem, try "
                            + retry + " failed: " + exceptReason);
                    try {
                        Thread.currentThread().sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Log.d(TAG, MODULE + ": Sleep interrupted: "
                                + ie.getMessage());
                    }
                }
            }

            if (mHandler != null) {
                if (connected) {
                    mHandler.obtainMessage(ModemConnectionFrag.MSG_CONNECTION_SUCCESS)
                            .sendToTarget();
                } else {
                    mdmCtrl = null;
                    mHandler.obtainMessage(ModemConnectionFrag.MSG_CONNECTION_FAIL,
                            exceptReason).sendToTarget();
                }
            }
        AlogMarker.tAE("AMTLTabLayout.ConnectModemTask.run", "0");
        }
    }

    public static ModemConf getModemConfiguration() {
        AlogMarker.tAB("AMTLTabLayout.getModemConfiguration", "0");
        if (expConf.getExpertConf() != null && expConf.isConfigSet()) {
            ExpertConfig.setExpertMode(modemNames.get(currentLoggingModem), true);
            AlogMarker.tAE("AMTLTabLayout.getModemConfiguration", "0");
            return expConf.getExpertConf();
        } else {
            AlogMarker.tAE("AMTLTabLayout.getModemConfiguration", "0");
            return generalModemConf;
        }
    }

    public static List < GeneralTracing > getActiveTraces() {
        AlogMarker.tAB("AMTLTabLayout.getActiveTraces", "0");
        List < GeneralTracing > tracers = new ArrayList < GeneralTracing > ();
        tracers.add(logcatTraces);
        tracers.add(modemTraces);
        AlogMarker.tAE("AMTLTabLayout.getActiveTraces", "0");
        return tracers;
    }
}
