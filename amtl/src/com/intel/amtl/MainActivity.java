/* Android Modem Traces and Logs
 *
 * Copyright (C) Intel 2012
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
 * ============================================================================
 * AMTL version:
 *
 * The AMTL versioning convention uses three digits as the following scheme:
 * <project code>.<Major version>.<Minor version>
 *
 * 2.1.0  - 2012-07-11 - BZ 34413 - Remove USBswitch on Clovertrail
 * 2.1.1  - 2012-07-11 - BZ 43865 - Handling of logging over USB CDC ACM
 * 2.1.2  - 2012-08-03 - BZ 47868 - Solve Klockwork critical issues
 * 2.1.3  - 2012-08-16 - BZ 46497 - Disable TOGGLE_PIN_ON1 button
 * 2.1.4  - 2012-08-22 - BZ 53487 - Configuration suggested in SettingsActivity
 * 2.1.5  - 2012-08-28 - BZ 46162 - Identation problem
 * 2.1.6  - 2012-09-05 - BZ 46849 - Activate additional traces
 * ============================================================================
 */

package com.intel.amtl;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ToggleButton;

import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends Activity {

    private static final String MODULE = "MainActivity";

    private ToggleButton button_modem_coredump;
    private ToggleButton button_ape_log_file;
    private ToggleButton button_online_bp_log;
    private ToggleButton button_disable_modem_trace;

    private AmtlCore core;

    /* Create advanced menu */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.layout.menu_advanced, menu);
        return true;
    }

    /* Start activity according to pressed button */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.settings:
            Intent i = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(i);
            return true;
        case R.id.additional_features:
            Intent j = new Intent(MainActivity.this, AdditionalFeaturesActivity.class);
            startActivity(j);
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }

    private void setCfg(PredefinedCfg cfg) {
        this.core.setCfg(cfg);
        setUI(cfg);
        if (this.core.rebootNeeded()) {
            UIHelper.message_pop_up(this, "WARNING", "Your board need a HARDWARE REBOOT");
        }
    }

    private void setUI(PredefinedCfg cfg) {

        /* Uncheck all buttons first */
        button_modem_coredump.setChecked(false);
        button_ape_log_file.setChecked(false);
        button_online_bp_log.setChecked(false);
        button_disable_modem_trace.setChecked(false);

        /* Check selected button */
        switch (cfg) {
            case COREDUMP:
                button_modem_coredump.setChecked(true);
                break;
            case OFFLINE_BP_LOG:
            case OFFLINE_USB_BP_LOG:
                button_ape_log_file.setChecked(true);
                break;
            case ONLINE_BP_LOG:
                button_online_bp_log.setChecked(true);
                break;
            case TRACE_DISABLE:
                button_disable_modem_trace.setChecked(true);
                break;
            default:
                /* Other configuration not available in standard mode */
                UIHelper.message_pop_up(this, "WARNING","Please use Advanced Menu to know your current configuration");
                break;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        button_modem_coredump = (ToggleButton) findViewById(R.id.modem_coredump_btn);
        button_ape_log_file = (ToggleButton) findViewById(R.id.ape_log_file_btn);
        button_disable_modem_trace = (ToggleButton) findViewById(R.id.disable_modem_trace_btn);
        button_online_bp_log = (ToggleButton) findViewById(R.id.online_bp_log_btn);

        /* Check if the buttons are not null*/
        AmtlCore.exitIfNull(button_modem_coredump, this);
        AmtlCore.exitIfNull(button_ape_log_file, this);
        AmtlCore.exitIfNull(button_disable_modem_trace, this);
        AmtlCore.exitIfNull(button_online_bp_log, this);

        button_modem_coredump.setChecked(false);
        button_ape_log_file.setChecked(false);
        button_disable_modem_trace.setChecked(false);
        button_online_bp_log.setChecked(false);

        /* Get application core */
        try {
            this.core = AmtlCore.get();
            this.core.setContext(this.getApplicationContext());
            this.core.invalidate();
            setUI(this.core.getCurCfg());
        } catch (AmtlCoreException e) {
            /* Failed to initialize application core */
            this.core = null;
            Log.e(AmtlCore.TAG, MODULE + ": " + e.getMessage());
            UIHelper.exitDialog(this, "ERROR", e.getMessage() + "\nAMTL will exit.");
        }

        /* On start, print a warning message */
        UIHelper.message_warning(this, "WARNING","This is a R&D Application. Please do not use unless you are asked to!");

        if (!AmtlCore.usbAcmEnabled) {
            /* Listener on Modem Coredump button */
            button_modem_coredump.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (button_modem_coredump.isChecked()) {
                        setCfg(PredefinedCfg.COREDUMP);
                    } else {
                        /* If user presses again on button_modem_coredump, traces are stopped */
                        setCfg(PredefinedCfg.TRACE_DISABLE);
                    }
                }
            });
        } else {
            /*Disable modem coredump button for Clovertrail*/
            button_modem_coredump.setVisibility(View.GONE);
        }

        /* Listener on APE Log File button */
        button_ape_log_file.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (button_ape_log_file.isChecked()) {
                    if (!AmtlCore.usbAcmEnabled) {
                        setCfg(PredefinedCfg.OFFLINE_BP_LOG);
                    } else {
                        setCfg(PredefinedCfg.OFFLINE_USB_BP_LOG);
                    }
                } else {
                    /* If user presses again on button_ape_log_file, traces are stopped */
                    setCfg(PredefinedCfg.TRACE_DISABLE);
                }
            }
        });

        if (AmtlCore.usbswitchEnabled) {
            /* Listener on online BP logging  button */
            button_online_bp_log.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (button_online_bp_log.isChecked()) {
                        setCfg(PredefinedCfg.ONLINE_BP_LOG);
                    } else {
                        /* If user presses again on button_ape_log_file, traces are stopped */
                        setCfg(PredefinedCfg.TRACE_DISABLE);
                    }
                }
            });
        } else {
            /*Disable online bp log button for Clovertrail and Lexington*/
            button_online_bp_log.setVisibility(View.GONE);
        }

        /* Listener on Disable button */
        button_disable_modem_trace.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setCfg(PredefinedCfg.TRACE_DISABLE);
            }
        });
    }

    /* User returns to the activity -> update it */
    @Override
    protected void onResume() {
        super.onResume();
        if (this.core != null) {
            if (this.core.rebootNeeded()) {
                UIHelper.message_pop_up(this, "WARNING", "Your board need a HARDWARE REBOOT");
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(AmtlCore.TAG, MODULE + ": onDestroy() call");
        if (this.core != null) {
            this.core.destroy();
        }
        super.onDestroy();
    }
}
