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
 */

package com.intel.amtl;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RadioButton;
import android.widget.TextView;

import java.io.IOException;

public class SettingsActivity extends Activity {

    private static final String MODULE = "SettingsActivity";

    private final String LOG_SIZE_150MB = "150MB";
    private final String LOG_SIZE_600MB = "600MB";

    private CompoundButton button_location_emmc;
    private CompoundButton button_location_sdcard;
    private CompoundButton button_location_coredump;
    private CompoundButton button_location_usb_ape;
    private CompoundButton button_location_usb_modem;
    private CompoundButton button_location_pti_modem;
    private CompoundButton button_location_none;
    private CompoundButton button_level_bb;
    private CompoundButton button_level_bb_3g;
    private CompoundButton button_level_bb_3g_digrf;
    private CompoundButton button_level_none;
    private CompoundButton button_trace_size_small;
    private CompoundButton button_trace_size_large;
    private CompoundButton button_trace_size_none;
    private CompoundButton button_offline_logging_hsi;
    private CompoundButton button_offline_logging_usb;
    private CompoundButton button_offline_logging_none;
    private CheckBox checkbox_activate;
    private CheckBox checkbox_mux;
    private CheckBox checkbox_additional_traces;
    private TextView header_additional_traces;
    /* when set_trace_file_size is called by the listener on emmc button */
    /* button_location_emmc is not considered as checked yet */
    private boolean isEmmcChecked = false;

    private boolean invalidateFlag;

    private AmtlCore core;

    /* Local selected values */
    private CustomCfg cfg;

    /* Update other buttons after enabling coredump, pti, usb ape or modem as trace location */
    private void callOnlineButtonListerner() {
        button_level_bb_3g.performClick();
        button_trace_size_none.performClick();
        button_offline_logging_none.performClick();
    }

    /* Update other buttons after enabling sdcard or emmc as trace location */
    private void callOfflineButtonListerner() {
        button_level_bb_3g.performClick();
        button_trace_size_large.performClick();
        if (AmtlCore.usbAcmEnabled) {
            button_offline_logging_usb.performClick();
        } else {
            button_offline_logging_hsi.performClick();
        }
    }

    /* Update other buttons after enabling none as trace location */
    private void callDisableButtonListerner() {
        button_level_none.performClick();
        button_trace_size_none.performClick();
        button_offline_logging_none.performClick();
    }

    /* Set the clickable buttons in coredump, pti, usb ape and usb modem cases */
    private void enableOnlineButtons() {
        /* By default trace level is bb_3g */
        button_level_bb.setEnabled(true);
        button_level_bb_3g.setEnabled(true);
        button_level_bb_3g.setChecked(true);
        button_level_bb_3g_digrf.setEnabled(true);
        button_level_none.setEnabled(false);
        /* By default trace file size is none */
        button_trace_size_small.setEnabled(false);
        if (!AmtlCore.usbswitchEnabled && !AmtlCore.usbAcmEnabled) {
            if (button_location_emmc.isChecked()) {
                button_trace_size_large.setText(LOG_SIZE_150MB);
            } else {
                button_trace_size_large.setText(LOG_SIZE_600MB);
            }
        }
        button_trace_size_large.setEnabled(false);
        button_trace_size_none.setEnabled(true);
        button_trace_size_none.setChecked(true);
        /* By default offline logging is none */
        button_offline_logging_hsi.setEnabled(false);
        button_offline_logging_usb.setEnabled(false);
        button_offline_logging_none.setEnabled(true);
        button_offline_logging_none.setChecked(true);
    }

    /* Set the clickable buttons in sdcard or emmc cases */
    private void enableOfflineButtons() {
        /* By default trace level is bb_3g */
        button_level_none.setEnabled(false);
        button_level_bb.setEnabled(true);
        button_level_bb_3g.setEnabled(true);
        button_level_bb_3g.setChecked(true);
        button_level_bb_3g_digrf.setEnabled(true);
        /* By default trace file size is large */
        button_trace_size_small.setEnabled(true);
        if (!AmtlCore.usbswitchEnabled && !AmtlCore.usbAcmEnabled) {
            if (isEmmcChecked) {
                button_trace_size_large.setText(LOG_SIZE_150MB);
            } else {
                button_trace_size_large.setText(LOG_SIZE_600MB);
            }
        }
        button_trace_size_large.setEnabled(true);
        button_trace_size_large.setChecked(true);
        button_trace_size_none.setEnabled(false);
        /* By default offline logging is usb for redhookbay and hsi for blackbay and lexington */
        /* to be uncommented when logging over HSI is working */
        // button_offline_logging_hsi.setEnabled(true);
        // button_offline_logging_hsi.setChecked(true);
        // button_offline_logging_usb.setEnabled(AmtlCore.usbAcmEnabled);
        /* to be removed when logging over HSI is working */
        button_offline_logging_none.setEnabled(false);
        button_offline_logging_hsi.setEnabled(!AmtlCore.usbAcmEnabled);
        button_offline_logging_hsi.setChecked(!AmtlCore.usbAcmEnabled);
        button_offline_logging_usb.setEnabled(AmtlCore.usbAcmEnabled);
        button_offline_logging_usb.setChecked(AmtlCore.usbAcmEnabled);
    }

    private void enableDisableButtons () {
        /* By default trace level is none */
        button_level_bb.setEnabled(false);
        button_level_bb_3g.setEnabled(false);
        button_level_bb_3g_digrf.setEnabled(false);
        button_level_none.setEnabled(true);
        button_level_none.setChecked(true);
        /* By default trace file size is none */
        button_trace_size_small.setEnabled(false);
        button_trace_size_large.setEnabled(false);
        button_trace_size_none.setEnabled(true);
        button_trace_size_none.setChecked(true);
        /* By default offline logging is none */
        button_offline_logging_hsi.setEnabled(false);
        button_offline_logging_usb.setEnabled(false);
        button_offline_logging_none.setEnabled(true);
        button_offline_logging_none.setChecked(true);
    }

    /* Set trace level button when opening settings */
    private void set_trace_level_button() {
        switch(cfg.traceLevel) {
        case CustomCfg.TRACE_LEVEL_NONE:
            break;
        case CustomCfg.TRACE_LEVEL_BB:
            button_level_bb.setChecked(true);
            break;
        case CustomCfg.TRACE_LEVEL_BB_3G:
            button_level_bb_3g.setChecked(true);
            break;
        case CustomCfg.TRACE_LEVEL_BB_3G_DIGRF:
            button_level_bb_3g_digrf.setChecked(true);
            break;
        default:
            /* Do nothing */
            break;
        }
    }

    /* Set trace location button when opening settings */
    private void set_location_button() {
        switch (cfg.traceLocation) {
        case CustomCfg.TRACE_LOC_EMMC:
            button_location_emmc.setChecked(true);
            isEmmcChecked = true;
            enableOfflineButtons();
            break;
        case CustomCfg.TRACE_LOC_SDCARD:
            button_location_sdcard.setChecked(true);
            isEmmcChecked = false;
            enableOfflineButtons();
            break;
        case CustomCfg.TRACE_LOC_COREDUMP:
            button_location_coredump.setChecked(true);
            enableOnlineButtons();
            break;
        case CustomCfg.TRACE_LOC_USB_APE:
            button_location_usb_ape.setChecked(true);
            enableOnlineButtons();
            break;
        case CustomCfg.TRACE_LOC_USB_MODEM:
            button_location_usb_modem.setChecked(true);
            enableOnlineButtons();
            break;
        case CustomCfg.TRACE_LOC_PTI_MODEM:
            button_location_pti_modem.setChecked(true);
            enableOnlineButtons();
            break;
        case CustomCfg.TRACE_LOC_NONE:
            button_location_none.setChecked(true);
            enableDisableButtons();
            break;
        default:
            /* Do nothing */
            break;
        }
    }

    /* Set log size button when opening settings */
    private void set_log_size_button() {
        switch (cfg.traceFileSize) {
        case CustomCfg.LOG_SIZE_NONE:
            break;
        case CustomCfg.LOG_SIZE_SMALL:
            button_trace_size_small.setChecked(true);
            break;
        case CustomCfg.LOG_SIZE_LARGE:
            if (!AmtlCore.usbswitchEnabled && !AmtlCore.usbAcmEnabled) {
                if (isEmmcChecked) {
                    button_trace_size_large.setText(LOG_SIZE_150MB);
                } else {
                    button_trace_size_large.setText(LOG_SIZE_600MB);
                }
            }
            button_trace_size_large.setChecked(true);
            break;
        default:
            /* Do nothing */
            break;
        }
    }

    /* Set log size button when opening settings */
    private void set_offline_logging_button() {
        switch (cfg.offlineLogging) {
        case CustomCfg.OFFLINE_LOGGING_NONE:
            break;
        case CustomCfg.OFFLINE_LOGGING_HSI:
            button_offline_logging_hsi.setChecked(true);
            break;
        case CustomCfg.OFFLINE_LOGGING_USB:
            button_offline_logging_usb.setChecked(true);
            break;
        default:
            /* Do nothing */
            break;
        }
    }

    /* Set MUX trace checkbox state */
    private void set_checkbox_mux() {
        checkbox_mux.setChecked(cfg.muxTrace == CustomCfg.MUX_TRACE_ON);
    }

    /* Set Additional traces checkbox state */
    private void set_checkbox_add_traces() {
        checkbox_additional_traces.setChecked(cfg.addTraces == CustomCfg.ADD_TRACES_ON);
    }

    /* Update settings menu buttons when opening settings*/
    private void update_settings_menu() {
        set_location_button();
        set_trace_level_button();
        set_log_size_button();
        set_offline_logging_button();
        set_checkbox_mux();
        set_checkbox_add_traces();
        invalidate();
    }

    private void invalidate() {
        invalidateFlag = true;
        checkbox_activate.setChecked(!reboot_needed());
        invalidateFlag = false;
    }

    private boolean reboot_needed() {
        CustomCfg curCfg = core.getCurCustomCfg();
        return (
            (cfg.traceLocation != curCfg.traceLocation)
                || (cfg.traceLevel != curCfg.traceLevel)
                || (cfg.traceFileSize != curCfg.traceFileSize)
                || (cfg.offlineLogging != curCfg.offlineLogging));
    }

    public void onTraceLocationClicked(View V) {

        boolean checked = ((RadioButton) V).isChecked();

        /* Check which radio button was clicked */
        switch(V.getId()) {
            case R.id.settings_location_emmc_btn:
                if (checked) {
                    isEmmcChecked = true;
                    cfg.traceLocation = CustomCfg.TRACE_LOC_EMMC;
                    enableOfflineButtons();
                    callOfflineButtonListerner();
                    invalidate();
                }
                break;
            case R.id.settings_location_sdcard_btn:
                if (checked) {
                    isEmmcChecked = false;
                    cfg.traceLocation = CustomCfg.TRACE_LOC_SDCARD;
                    enableOfflineButtons();
                    callOfflineButtonListerner();
                    invalidate();
                }
                break;
            case R.id.settings_location_coredump_btn:
                if (checked) {
                    cfg.traceLocation = CustomCfg.TRACE_LOC_COREDUMP;
                    enableOnlineButtons();
                    callOnlineButtonListerner();
                    invalidate();
                }
                break;
            case R.id.settings_location_usb_ape_btn:
                if (checked) {
                    cfg.traceLocation = CustomCfg.TRACE_LOC_USB_APE;
                    enableOnlineButtons();
                    callOnlineButtonListerner();
                    invalidate();
                }
                break;
            case R.id.settings_location_usb_modem_btn:
                if (checked) {
                    cfg.traceLocation = CustomCfg.TRACE_LOC_USB_MODEM;
                    enableOnlineButtons();
                    callOnlineButtonListerner();
                    button_level_bb_3g_digrf.setEnabled(false);
                    invalidate();
                }
                break;
            case R.id.settings_location_pti_modem_btn:
                if (checked) {
                    cfg.traceLocation = CustomCfg.TRACE_LOC_PTI_MODEM;
                    enableOnlineButtons();
                    callOnlineButtonListerner();
                    invalidate();
                }
                break;
            case R.id.settings_location_none_btn:
                if (checked) {
                    cfg.traceLocation = CustomCfg.TRACE_LOC_NONE;
                    enableDisableButtons();
                    callDisableButtonListerner();
                    invalidate();
                }
                break;
            default: /* Error, unknown button ID */
                Log.e(AmtlCore.TAG, MODULE + ": unknown button ID, set trace location to none.");
                break;
        }
    }

    public void onTraceLevelClicked(View V) {

        boolean checked = ((RadioButton) V).isChecked();

       /* Check which radio button was clicked */
        switch(V.getId()) {
            case R.id.settings_level_bb_btn:
                if (checked) {
                    cfg.traceLevel = CustomCfg.TRACE_LEVEL_BB;
                    invalidate();
                }
                break;
            case R.id.settings_level_bb_3g_btn:
                if (checked) {
                    cfg.traceLevel = CustomCfg.TRACE_LEVEL_BB_3G;
                    invalidate();
                }
                break;
            case R.id.settings_level_bb_3g_digrf_btn:
                if (checked) {

                    cfg.traceLevel = CustomCfg.TRACE_LEVEL_BB_3G_DIGRF;
                    invalidate();
                }
                break;
            case R.id.settings_level_none_btn:
                if (checked) {
                    cfg.traceLevel = CustomCfg.TRACE_LEVEL_NONE;
                    invalidate();
                }
                break;
            default: /* Error, unknown button ID */
                Log.e(AmtlCore.TAG, MODULE + ": unknown button ID, set trace level to none.");
                break;
        }
    }

    public void onTraceSizeClicked(View V) {

        boolean checked = ((RadioButton) V).isChecked();

       /* Check which radio button was clicked */
        switch(V.getId()) {
            case R.id.settings_trace_size_small_btn:
                if (checked) {
                    cfg.traceFileSize = CustomCfg.LOG_SIZE_SMALL;
                    invalidate();
                }
                break;
            case R.id.settings_trace_size_large_btn:
                if (checked) {
                    cfg.traceFileSize = CustomCfg.LOG_SIZE_LARGE;
                    invalidate();
                }
                break;
            case R.id.settings_trace_size_none_btn:
                if (checked) {
                    cfg.traceFileSize = CustomCfg.LOG_SIZE_NONE;
                    invalidate();
                }
                break;
            default: /* Error, unknown button ID */
                Log.e(AmtlCore.TAG, MODULE + ": unknown button ID, set trace size to none.");
                break;
        }
    }

    public void onOfflineLoggingClicked(View V) {

        boolean checked = ((RadioButton) V).isChecked();

       /* Check which radio button was clicked */
        switch(V.getId()) {
            case R.id.settings_offline_logging_hsi_btn:
                if (checked) {
                    cfg.offlineLogging = CustomCfg.OFFLINE_LOGGING_HSI;
                    invalidate();
                }
                break;
            case R.id.settings_offline_logging_usb_btn:
                if (checked) {
                    cfg.offlineLogging = CustomCfg.OFFLINE_LOGGING_USB;
                    invalidate();
                }
                break;
            case R.id.settings_offline_logging_none_btn:
                if (checked) {
                    cfg.offlineLogging = CustomCfg.OFFLINE_LOGGING_NONE;
                    invalidate();
                }
                break;
            default: /* Error, unknown button ID */
                Log.e(AmtlCore.TAG, MODULE + ": unknown button ID, set offline to none.");
                break;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        /* Trace location buttons */
        button_location_emmc = (CompoundButton) findViewById(R.id.settings_location_emmc_btn);
        button_location_sdcard = (CompoundButton) findViewById (R.id.settings_location_sdcard_btn);
        button_location_coredump
                = (CompoundButton) findViewById (R.id.settings_location_coredump_btn);
        button_location_usb_ape
                = (CompoundButton) findViewById (R.id.settings_location_usb_ape_btn);
        button_location_usb_modem
                = (CompoundButton) findViewById (R.id.settings_location_usb_modem_btn);
        button_location_pti_modem
                = (CompoundButton) findViewById (R.id.settings_location_pti_modem_btn);
        button_location_none
                = (CompoundButton) findViewById (R.id.settings_location_none_btn);

        /* Trace level buttons */
        button_level_bb = (CompoundButton) findViewById (R.id.settings_level_bb_btn);
        button_level_bb_3g = (CompoundButton) findViewById (R.id.settings_level_bb_3g_btn);
        button_level_bb_3g_digrf
                = (CompoundButton) findViewById (R.id.settings_level_bb_3g_digrf_btn);
        button_level_none = (CompoundButton) findViewById (R.id.settings_level_none_btn);

        /* Log size buttons */
        button_trace_size_small
                = (CompoundButton) findViewById (R.id.settings_trace_size_small_btn);
        button_trace_size_large
                = (CompoundButton) findViewById (R.id.settings_trace_size_large_btn);
        button_trace_size_none
                = (CompoundButton) findViewById (R.id.settings_trace_size_none_btn);

        /* HSI or USB logging */
        button_offline_logging_hsi
                = (CompoundButton) findViewById (R.id.settings_offline_logging_hsi_btn);
        button_offline_logging_usb
                = (CompoundButton) findViewById (R.id.settings_offline_logging_usb_btn);
        button_offline_logging_none
                = (CompoundButton) findViewById (R.id.settings_offline_logging_none_btn);

        /* Activate check box */
        checkbox_activate = (CheckBox) findViewById (R.id.activate_checkBox);

        /* MUX traces check box */
        checkbox_mux = (CheckBox) findViewById (R.id.mux_checkBox);

        /* Additional traces check box and header */
        checkbox_additional_traces = (CheckBox) findViewById (R.id.additional_traces_checkBox);
        header_additional_traces = (TextView) findViewById (R.id.textView7);

        /* Check if the buttons and checkboxes are not null */
        AmtlCore.exitIfNull(button_location_emmc, this);
        AmtlCore.exitIfNull(button_location_sdcard, this);
        AmtlCore.exitIfNull(button_location_coredump, this);
        AmtlCore.exitIfNull(button_location_usb_ape, this);
        AmtlCore.exitIfNull(button_location_usb_modem, this);
        AmtlCore.exitIfNull(button_location_pti_modem, this);
        AmtlCore.exitIfNull(button_location_none, this);
        AmtlCore.exitIfNull(button_level_bb, this);
        AmtlCore.exitIfNull(button_level_bb_3g, this);
        AmtlCore.exitIfNull(button_level_bb_3g_digrf, this);
        AmtlCore.exitIfNull(button_level_none, this);
        AmtlCore.exitIfNull(button_trace_size_small, this);
        AmtlCore.exitIfNull(button_trace_size_large, this);
        AmtlCore.exitIfNull(button_trace_size_none, this);
        AmtlCore.exitIfNull(button_offline_logging_hsi, this);
        AmtlCore.exitIfNull(button_offline_logging_usb, this);
        AmtlCore.exitIfNull(button_offline_logging_none, this);
        AmtlCore.exitIfNull(checkbox_activate, this);
        AmtlCore.exitIfNull(checkbox_mux, this);
        AmtlCore.exitIfNull(checkbox_additional_traces, this);
        AmtlCore.exitIfNull(header_additional_traces, this);

        checkbox_activate.setChecked(false);

        /* Get application core */
        try {
            cfg = new CustomCfg();
            this.core = AmtlCore.get();
            this.core.setContext(this.getApplicationContext());
            this.core.invalidate();

            CustomCfg curCfg = core.getCurCustomCfg();
            /* Get current custom configuration */
            cfg.traceLocation = curCfg.traceLocation;
            cfg.traceLevel = curCfg.traceLevel;
            cfg.traceFileSize = curCfg.traceFileSize;
            cfg.offlineLogging = curCfg.offlineLogging;
            cfg.muxTrace = curCfg.muxTrace;
            cfg.addTraces = curCfg.addTraces;

            update_settings_menu();
        } catch (AmtlCoreException e) {
            /* Failed to initialize application core */
            this.core = null;
            Log.e(AmtlCore.TAG, MODULE + ": " + e.getMessage());
            UIHelper.message_pop_up(this, "ERROR",e.getMessage());
        }

        if (!AmtlCore.usbswitchEnabled) {
            button_location_usb_ape.setVisibility(View.GONE);
            button_location_usb_modem.setVisibility(View.GONE);
        }
        if (!AmtlCore.ptiEnabled) {
            button_location_pti_modem.setVisibility(View.GONE);
        }

        /* Listener on MUX trace Checkbox */
        checkbox_mux.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                cfg.muxTrace = (isChecked) ? CustomCfg.MUX_TRACE_ON: CustomCfg.MUX_TRACE_OFF;
                core.setMuxTrace(cfg.muxTrace);
            }
        });

        /* Listener on Additional traces Checkbox */
        checkbox_additional_traces.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                cfg.addTraces = (isChecked) ? CustomCfg.ADD_TRACES_ON: CustomCfg.ADD_TRACES_OFF;
                core.isAddTracesEnabled = isChecked;
                core.setAdditionalTraces(cfg.addTraces);
                if (isChecked) {
                    UIHelper.message_pop_up(SettingsActivity.this,
                            "WARNING", "Traces are not persistent");
                } else {
                    UIHelper.message_pop_up(SettingsActivity.this, "WARNING",
                            "To disable additional traces please reboot");
                }
            }
        });

        /* Listener on Activate Checkbox */
        checkbox_activate.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!invalidateFlag) {
                    if (isChecked) {
                        core.setCustomCfg(cfg);
                        UIHelper.message_pop_up(SettingsActivity.this, "WARNING",
                                "Your board needs a HARDWARE REBOOT");
                    } else {
                        invalidate();
                    }
                }
            }
        });
    }
}
