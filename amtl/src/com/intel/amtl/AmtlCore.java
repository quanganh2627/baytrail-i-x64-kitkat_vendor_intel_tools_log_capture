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
import android.content.Context;
import android.os.Handler.Callback;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.util.Log;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.intel.internal.telephony.MmgrClientException;
import com.intel.internal.telephony.ModemEventListener;
import com.intel.internal.telephony.ModemNotification;
import com.intel.internal.telephony.ModemNotificationArgs;
import com.intel.internal.telephony.ModemStatus;
import com.intel.internal.telephony.ModemStatusManager;

public class AmtlCore implements ModemEventListener {

    /* AMTL log tag */
    public static final String TAG = "AMTL";
    /* Module log tag */
    private static final String MODULE = "Core";
    /* USB switch service state file */
    public static final String OUTPUT_FILE = "usbswitch.conf";

    /* AMTL core exception messages */
    private static final String ERR_MODEM_NOT_READY = "Modem is not ready";
    private static final String ERR_APPLY_CFG = "Failed to apply configuration";
    private static final String ERR_UPDATE_CFG = "Failed to get current configuration";

    private static final String USBSWITCH_PROP = "ro.amtl.usbswitch";
    private static final String USBACM_PROP = "ro.amtl.usbacm";
    private static final String PTI_PROP = "ro.amtl.pti";

    private static final long MODEM_STATUS_TIMEOUT_MS = 2000;

    /* AMTL Core reference: singleton design pattern */
    private static AmtlCore core;

    private ModemStatus currentStatus = ModemStatus.NONE;

    public static final Runtime rtm = java.lang.Runtime.getRuntime();

    /* AT command for additional is not persisent */
    /* and there is no at command to check its state */
    protected static boolean isAddTracesEnabled = false;

    /* Current predefined configuration */
    private PredefinedCfg curCfg;
    /* Future predefined configuration to set */
    private PredefinedCfg futCfg;

    /* Current custom configuration */
    private CustomCfg curCustomCfg;
    /* Future custom configuration to set */
    private CustomCfg futCustomCfg;

    /* Flag of first configuration set:
       to not interpret as a configuration to set */
    private boolean firstCfgSet = true;

    /* Current configuration values */
    private int serviceValue;
    private int traceLevelValue;
    private int traceStatus;
    private int xsioValue;
    private int infoModemReboot;
    private int muxTraceValue;
    private int addTracesValue;

    private Services services;
    private ModemConfiguration modemCfg;
    private ModemStatusManager modemStatusManager;
    private GsmttyManager ttyManager;

    private Context ctx;

    private RandomAccessFile gsmtty;

    private PlatformConfig platformConfig;

    /* Constructor */
    private AmtlCore() throws AmtlCoreException {
        Log.i(TAG, MODULE + ": create application core");

        try {
            this.platformConfig = PlatformConfig.get();
            /* Create status monitor and open gsmtty device */
            this.modemStatusManager = ModemStatusManager.getInstance();
            this.modemStatusManager.subscribeToEvent(this, ModemStatus.ALL, ModemNotification.ALL);
            this.ttyManager = new GsmttyManager();
        } catch (InstantiationException ex) {
            throw new AmtlCoreException("Modem Status Manager client could not be instantiated." +
                    " Make sur his device has STMD or MMGR deployed.");
        } catch (MmgrClientException ex) {
            throw new AmtlCoreException(ex.getMessage());
        } catch (ExceptionInInitializerError ex) {
            throw new AmtlCoreException("AMTL library not found, please install it first");
        }

        this.firstCfgSet = true;
        this.curCfg = PredefinedCfg.UNKNOWN_CFG;
        this.futCfg = PredefinedCfg.UNKNOWN_CFG;

        this.modemCfg = new ModemConfiguration();

        this.curCustomCfg = new CustomCfg();
        this.futCustomCfg = new CustomCfg();

        this.ctx = null;

        this.services = new Services();

        try {
            /* Don't forget to start modem status monitoring */
            this.modemStatusManager.connect("Amtl");

            if (!this.modemStatusManager.
                    waitForModemStatus(ModemStatus.UP, MODEM_STATUS_TIMEOUT_MS)) {
                throw new AmtlCoreException("Modem is not ready.");
            }
            this.currentStatus = ModemStatus.UP;
        } catch (MmgrClientException ex) {
            throw new AmtlCoreException("Could not connect to Modem Status Monitor service.");
        }
    }

    protected void openGsmtty () throws AmtlCoreException {
        if (this.gsmtty == null) {
            if (this.ttyManager != null) {
                try {
                    this.ttyManager.openTty();
                    this.gsmtty = new RandomAccessFile(this.ttyManager.getTtyName(), "rw");
                } catch (IOException ex) {
                    throw new AmtlCoreException(String.format("Error while opening gsmtty"));
                } catch (IllegalArgumentException ex2) {
                    Log.e(TAG, MODULE + ": " + ex2.toString());
                }
            } else {
                throw new AmtlCoreException(String.format("Cannot open gsmtty: gsmttyManager null"));
            }
        }
    }

    /* Set Amtl core application context */
    protected void setContext(Context ctx) {
        this.ctx = ctx;
    }

    /* Get a reference of AMTL core application: singleton design pattern */
    protected static AmtlCore get() throws AmtlCoreException {
        if (core == null) {
            core = new AmtlCore();
        }
        return core;
    }

    /* Destructor */
    protected void destroy() {
        /* Close gsmtty device */
        this.closeGsmtty();
        /* Stop modem status monitoring */
        if (this.modemStatusManager != null) {
            this.modemStatusManager.disconnect();
            this.modemStatusManager = null;
        }
        /* Core is not available anymore */
        this.core = null;
    }

    /* Set configuration to use after reboot */
    protected int setCfg(PredefinedCfg cfg) {
        switch (cfg) {
            case COREDUMP:
            case OFFLINE_BP_LOG:
            case ONLINE_BP_LOG:
            case TRACE_DISABLE:
            case OFFLINE_USB_BP_LOG:
            case PTI_BP_LOG:
                this.futCfg = cfg;
                break;
            default:
                this.futCfg = PredefinedCfg.UNKNOWN_CFG;
                Log.e(TAG, MODULE + ": can't set configuration, unknown configuration");
                return -1;
        }
        return 0;
    }

    /* Set custom configuration to use after reboot */
    protected int setCustomCfg(CustomCfg cfg) {
        this.futCfg = PredefinedCfg.CUSTOM;
        this.futCustomCfg = cfg;
        return 0;
    }

    /* Test if a reboot is needed */
    protected boolean rebootNeeded() {
        if (this.futCfg == PredefinedCfg.CUSTOM) {
            return(
                this.curCustomCfg.traceLocation != this.futCustomCfg.traceLocation
                        || this.curCustomCfg.traceLevel != this.futCustomCfg.traceLevel
                        || this.curCustomCfg.traceStatus != this.futCustomCfg.traceStatus
                        || this.curCustomCfg.traceFileSize != this.futCustomCfg.traceFileSize
                        || this.curCustomCfg.offlineLogging != this.futCustomCfg.offlineLogging);
        } else {
            return (this.curCfg != this.futCfg);
        }
    }

    /* Get current active configuration */
    protected PredefinedCfg getCurCfg() {
        return this.curCfg;
    }

    /* Get current custom configuration */
    protected CustomCfg getCurCustomCfg() {
        return this.curCustomCfg;
    }

    /* Apply selected configuration */
    protected void applyCfg() throws AmtlCoreException {
        if (this.currentStatus != ModemStatus.UP) {
            throw new AmtlCoreException(ERR_MODEM_NOT_READY);
        }
        try {
            /* Stop current running service */
            this.services.stop_service();
            switch (this.futCfg) {
                case COREDUMP:
                    applyCoredumpCfg();
                    break;
                case OFFLINE_BP_LOG:
                    applyOfflineBpLogCfg();
                    break;
                case ONLINE_BP_LOG:
                    applyOnlineBpLogCfg();
                    break;
                case OFFLINE_USB_BP_LOG:
                    applyOfflineUsbBpLogCfg();
                    break;
                case PTI_BP_LOG:
                    applyPtiBpLogCfg();
                    break;
                case TRACE_DISABLE:
                    applyTraceDisableCfg();
                    break;
                case CUSTOM:
                    applyCustomCfg();
                    break;
                default:
                    Log.e(TAG, MODULE + ": unknown configuration to apply");
                    break;
            }
        } catch (IOException e) {
            Log.e(TAG, MODULE + ": can't apply configuration");
            throw new AmtlCoreException(ERR_APPLY_CFG);
        }
    }

    /* Apply blue configuration */
    private void applyCoredumpCfg() throws IOException {
        this.services.stop_service();
        this.modemCfg.setXsio(this.gsmtty, this.platformConfig.getCoredumpXsio());
        this.modemCfg.setTraceLevel(this.gsmtty, CustomCfg.TRACE_LEVEL_BB_3G,
                (this.platformConfig.getPlatformVersion()).equals("clovertrail"));
        this.modemCfg.setTraceStatus(this.gsmtty,CustomCfg.TRACE_ENABLED);
        this.services.enable_service(Services.MTS_DISABLE, this.futCfg,
                this.futCustomCfg.offlineLogging);
    }

    /* Apply green configuration */
    private void applyOfflineBpLogCfg() throws IOException {
        this.services.stop_service();
        this.modemCfg.setXsio(this.gsmtty, this.platformConfig.getOfflineHsiXsio());
        this.modemCfg.setTraceLevel(this.gsmtty, CustomCfg.TRACE_LEVEL_BB_3G, false);
        this.modemCfg.setTraceStatus(this.gsmtty,CustomCfg.TRACE_ENABLED);
        this.services.enable_service(Services.MTS_EXTFS, this.futCfg,
                this.futCustomCfg.offlineLogging);
    }

    /* Apply green configuration via USB for Clovertrail */
    private void applyOfflineUsbBpLogCfg() throws IOException {
        this.services.stop_service();
        this.modemCfg.setXsio(this.gsmtty, this.platformConfig.getOfflineUsbXsio());
        this.modemCfg.setTraceLevel(this.gsmtty, CustomCfg.TRACE_LEVEL_BB_3G, false);
        this.modemCfg.setTraceStatus(this.gsmtty,CustomCfg.TRACE_ENABLED);
        this.services.enable_service(Services.MTS_EXTFS, this.futCfg,
                this.futCustomCfg.offlineLogging);
    }

    /* Apply purple configuration */
    private void applyOnlineBpLogCfg() throws IOException {
        this.services.stop_service();
        this.modemCfg.setXsio(this.gsmtty, this.platformConfig.getOnlineUsbXsio());
        this.modemCfg.setTraceLevel(this.gsmtty, CustomCfg.TRACE_LEVEL_BB_3G, false);
        this.modemCfg.setTraceStatus(this.gsmtty,CustomCfg.TRACE_ENABLED);
        this.services.enable_service(Services.ONLINE_BP_LOG, this.futCfg,
                this.futCustomCfg.offlineLogging);
    }

    /* Apply purple configuration for Clovertrail */
    private void applyPtiBpLogCfg() throws IOException {
        this.services.stop_service();
        this.modemCfg.setXsio(this.gsmtty, this.platformConfig.getOnlinePtiXsio());
        this.modemCfg.setTraceLevel(this.gsmtty, CustomCfg.TRACE_LEVEL_BB_3G, false);
        this.modemCfg.setTraceStatus(this.gsmtty,CustomCfg.TRACE_ENABLED);
        this.services.enable_service(Services.MTS_PTI, this.futCfg,
                this.futCustomCfg.offlineLogging);
    }

    /* Apply yellow configuration */
    private void applyTraceDisableCfg() throws IOException {
        this.services.stop_service();
        this.modemCfg.setTraceLevel(this.gsmtty, CustomCfg.TRACE_LEVEL_NONE, false);
        this.modemCfg.setTraceStatus(this.gsmtty,CustomCfg.TRACE_DISABLED);
        this.services.enable_service(Services.MTS_DISABLE, this.futCfg,
                this.futCustomCfg.offlineLogging);
    }

    /* Apply custom configuration */
    private void applyCustomCfg() {
        int serviceToStart;
        int xsioToSet;

        /* Determine service to configure */
        if (this.futCustomCfg.traceLocation == CustomCfg.TRACE_LOC_EMMC) {
            serviceToStart = (this.futCustomCfg.traceFileSize == CustomCfg.LOG_SIZE_SMALL)
                    ? Services.MTS_FS: Services.MTS_EXTFS;
        } else if (this.futCustomCfg.traceLocation == CustomCfg.TRACE_LOC_SDCARD) {
            serviceToStart = (this.futCustomCfg.traceFileSize == CustomCfg.LOG_SIZE_SMALL)
                    ? Services.MTS_SD: Services.MTS_EXTSD;
        } else if (this.futCustomCfg.traceLocation == CustomCfg.TRACE_LOC_USB_APE) {
            serviceToStart = Services.MTS_USB;
        } else if (this.futCustomCfg.traceLocation == CustomCfg.TRACE_LOC_USB_MODEM) {
            serviceToStart = Services.ONLINE_BP_LOG;
        } else if (this.futCustomCfg.traceLocation == CustomCfg.TRACE_LOC_PTI_MODEM) {
            serviceToStart = Services.MTS_PTI;
        } else {
            serviceToStart = Services.MTS_DISABLE;
        }

        /* Determine XSIO value to set */
        if (this.futCustomCfg.traceLocation == CustomCfg.TRACE_LOC_EMMC
                || this.futCustomCfg.traceLocation == CustomCfg.TRACE_LOC_SDCARD) {

            xsioToSet = (this.futCustomCfg.offlineLogging == CustomCfg.OFFLINE_LOGGING_USB)
                    ? this.platformConfig.getOfflineUsbXsio()
                    : this.platformConfig.getOfflineHsiXsio();

        } else if (this.futCustomCfg.traceLocation == CustomCfg.TRACE_LOC_COREDUMP) {
            xsioToSet = this.platformConfig.getCoredumpXsio();
        } else if (this.futCustomCfg.traceLocation == CustomCfg.TRACE_LOC_PTI_MODEM) {
            xsioToSet = this.platformConfig.getOnlinePtiXsio();
        } else {
            xsioToSet = -1;
        }

        boolean isCoredump = ((xsioToSet == this.platformConfig.getCoredumpXsio())
                && (futCustomCfg.traceLevel != CustomCfg.TRACE_LEVEL_NONE)
                && (futCustomCfg.traceStatus == CustomCfg.TRACE_DISABLED)
                && ((this.platformConfig.getPlatformVersion()).equals("clovertrail")));
        /* Apply configuration */
        this.services.stop_service();
        if (xsioToSet != -1) {
            this.modemCfg.setXsio(this.gsmtty, xsioToSet);
        }
        this.modemCfg.setTraceLevel(this.gsmtty, futCustomCfg.traceLevel, isCoredump);
        this.modemCfg.setTraceStatus(this.gsmtty, futCustomCfg.traceStatus);
        this.services.
                enable_service(serviceToStart, this.futCfg, this.futCustomCfg.offlineLogging);
    }

    /* Enable/Disable MUX traces */
    protected void setMuxTrace(int muxTrace) {
        if (muxTrace == CustomCfg.MUX_TRACE_ON) {
            this.modemCfg.setMuxTraceOn(this.gsmtty);
        } else {
            this.modemCfg.setMuxTraceOff(this.gsmtty);
        }
    }

    /* Enable/Disable Additional traces */
    protected void setAdditionalTraces(int addTraces) {
        if (addTraces == CustomCfg.ADD_TRACES_ON) {
            this.modemCfg.setAdditionalTracesOn(this.gsmtty);
            this.isAddTracesEnabled = true;
        } else {
            this.modemCfg.setAdditionalTracesOff(this.gsmtty);
            this.isAddTracesEnabled = false;
        }
    }

    /* Force AMTL Core to update its internal modem configuration values */
    protected void invalidate() throws AmtlCoreException {
        if (this.currentStatus != ModemStatus.UP) {
            throw new AmtlCoreException(ERR_MODEM_NOT_READY);
        }
        try {
            Log.i(TAG, MODULE + ": update current config");
            if (this.platformConfig.getUsbswitchAvailable()) {
                /* If necessary Create and Update usbswitch.conf file */
                usbswitch_update();
            }
            this.openGsmtty();
            /* Recover the current configuration */
            /* Current service */
            this.serviceValue = this.services.service_status();
            /* Current trace level */
            this.traceLevelValue = this.modemCfg.getTraceLevel(this.gsmtty);
            /* Current trace status */
            this.traceStatus = this.modemCfg.getTraceStatus(this.gsmtty);
            /* Current XSIO */
            this.xsioValue = this.modemCfg.getXsio(this.gsmtty);
            /* Current MUX trace state */
            this.muxTraceValue = this.modemCfg.getMuxTraceState(this.gsmtty);
            /* Current Additional traces state */
            if (isAddTracesEnabled) {
                this.addTracesValue = CustomCfg.ADD_TRACES_ON;
            } else {
                this.addTracesValue = CustomCfg.ADD_TRACES_OFF;
            }
            /* Update custom configuration for settings activity */
            this.curCustomCfg.traceLevel = this.traceLevelValue;
            this.curCustomCfg.traceStatus = this.traceStatus;
            this.curCustomCfg.muxTrace = this.muxTraceValue;
            this.curCustomCfg.addTraces = this.addTracesValue;

            if ((this.xsioValue == this.platformConfig.getCoredumpXsio())
                    && (this.serviceValue == Services.MTS_DISABLE)
                    && (this.traceStatus == CustomCfg.TRACE_ENABLED)
                    && (this.traceLevelValue == CustomCfg.TRACE_LEVEL_BB_3G)) {
                /* Trace in coredump enabled */
                this.curCfg = PredefinedCfg.COREDUMP;
            } else if ((this.xsioValue == this.platformConfig.getOfflineHsiXsio())
                    && (this.serviceValue == Services.MTS_EXTFS)
                    && (this.traceStatus == CustomCfg.TRACE_ENABLED)
                    && (this.traceLevelValue == CustomCfg.TRACE_LEVEL_BB_3G)) {
                /* Trace in APE log file enabled */
                this.curCfg = PredefinedCfg.OFFLINE_BP_LOG;
            } else if ((this.xsioValue == this.platformConfig.getOfflineUsbXsio())
                    && (this.serviceValue == Services.MTS_EXTFS)
                    && (this.traceStatus == CustomCfg.TRACE_ENABLED)
                    && (this.traceLevelValue == CustomCfg.TRACE_LEVEL_BB_3G)) {
                /* Trace via in APE log file via USB enabled */
                this.curCfg = PredefinedCfg.OFFLINE_USB_BP_LOG;
            } else if ((this.xsioValue == this.platformConfig.getOnlineUsbXsio())
                    && (this.serviceValue == Services.ONLINE_BP_LOG)
                    && (this.traceStatus == CustomCfg.TRACE_ENABLED)
                    && (this.traceLevelValue == CustomCfg.TRACE_LEVEL_BB_3G)) {
                /* Online BP logging */
                this.curCfg = PredefinedCfg.ONLINE_BP_LOG;
            } else if ((this.xsioValue == this.platformConfig.getOnlinePtiXsio())
                    && (this.serviceValue == Services.MTS_PTI)
                    && (this.traceStatus == CustomCfg.TRACE_ENABLED)
                    && (this.traceLevelValue == CustomCfg.TRACE_LEVEL_BB_3G)) {
                /* PTI BP logging */
                this.curCfg = PredefinedCfg.PTI_BP_LOG;
            } else if ((this.traceStatus == CustomCfg.TRACE_DISABLED)
                    && (this.traceLevelValue == CustomCfg.TRACE_LEVEL_NONE)) {
                this.services.stop_service();
                this.serviceValue = this.services.service_status();
                this.curCfg = PredefinedCfg.TRACE_DISABLE;
            } else {
                this.curCfg = PredefinedCfg.UNKNOWN_CFG;
            }

            switch (this.serviceValue) {
                case Services.MTS_DISABLE:
                    this.curCustomCfg.offlineLogging = CustomCfg.OFFLINE_LOGGING_NONE;
                    this.curCustomCfg.traceFileSize = CustomCfg.LOG_SIZE_NONE;
                    this.curCustomCfg.traceLocation =
                            (this.traceLevelValue != CustomCfg.TRACE_LEVEL_NONE
                            && this.traceStatus == CustomCfg.TRACE_ENABLED)
                            ? CustomCfg.TRACE_LOC_COREDUMP : CustomCfg.TRACE_LOC_NONE;
                    break;
                case Services.MTS_FS:
                    this.curCustomCfg.offlineLogging =
                            (this.xsioValue == this.platformConfig.getOfflineHsiXsio())
                            ? CustomCfg.OFFLINE_LOGGING_HSI : CustomCfg.OFFLINE_LOGGING_USB;
                    this.curCustomCfg.traceLocation = CustomCfg.TRACE_LOC_EMMC;
                    this.curCustomCfg.traceFileSize = CustomCfg.LOG_SIZE_SMALL;
                    break;
                case Services.MTS_EXTFS:
                    this.curCustomCfg.offlineLogging =
                            (this.xsioValue == this.platformConfig.getOfflineHsiXsio())
                            ? CustomCfg.OFFLINE_LOGGING_HSI : CustomCfg.OFFLINE_LOGGING_USB;
                    this.curCustomCfg.traceLocation = CustomCfg.TRACE_LOC_EMMC;
                    this.curCustomCfg.traceFileSize = CustomCfg.LOG_SIZE_LARGE;
                    break;
                case Services.MTS_SD:
                    this.curCustomCfg.offlineLogging =
                            (this.xsioValue == this.platformConfig.getOfflineHsiXsio())
                            ? CustomCfg.OFFLINE_LOGGING_HSI : CustomCfg.OFFLINE_LOGGING_USB;
                    this.curCustomCfg.traceLocation = CustomCfg.TRACE_LOC_SDCARD;
                    this.curCustomCfg.traceFileSize = CustomCfg.LOG_SIZE_SMALL;
                    break;
                case Services.MTS_EXTSD:
                    this.curCustomCfg.offlineLogging =
                            (this.xsioValue == this.platformConfig.getOfflineHsiXsio())
                            ? CustomCfg.OFFLINE_LOGGING_HSI : CustomCfg.OFFLINE_LOGGING_USB;
                    this.curCustomCfg.traceLocation = CustomCfg.TRACE_LOC_SDCARD;
                    this.curCustomCfg.traceFileSize = CustomCfg.LOG_SIZE_LARGE;
                    break;
                case Services.MTS_USB:
                    this.curCustomCfg.offlineLogging = CustomCfg.OFFLINE_LOGGING_NONE;
                    this.curCustomCfg.traceLocation = CustomCfg.TRACE_LOC_USB_APE;
                    this.curCustomCfg.traceFileSize = CustomCfg.LOG_SIZE_NONE;
                    break;
                case Services.ONLINE_BP_LOG:
                    this.curCustomCfg.offlineLogging = CustomCfg.OFFLINE_LOGGING_NONE;
                    this.curCustomCfg.traceLocation = CustomCfg.TRACE_LOC_USB_MODEM;
                    this.curCustomCfg.traceFileSize = CustomCfg.LOG_SIZE_NONE;
                    break;
                case Services.MTS_PTI:
                    this.curCustomCfg.offlineLogging = CustomCfg.OFFLINE_LOGGING_NONE;
                    this.curCustomCfg.traceLocation = CustomCfg.TRACE_LOC_PTI_MODEM;
                    this.curCustomCfg.traceFileSize = CustomCfg.LOG_SIZE_NONE;
                    break;
            }

            if (this.firstCfgSet) {
                this.futCfg = this.curCfg;
                this.firstCfgSet = false;
            }

        } catch (IOException e) {
            Log.e(TAG, MODULE + ": can't retrieve current configuration => " + e.getMessage());
            throw new AmtlCoreException(ERR_UPDATE_CFG);
        }
    }

    /* Create usbswitch.conf file if it doesn't exist */
    protected void usbswitch_update() {
        FileOutputStream usbswitch = null;
        try {
            if (ctx != null) {
                usbswitch = this.ctx.openFileOutput(OUTPUT_FILE, Context.MODE_APPEND);
            } else {
                Log.e(TAG, MODULE + ": failed to open " + OUTPUT_FILE + " (NULL context)");
            }
        } catch (IOException e) {
            Log.e(TAG, MODULE + ": can't create the file usbswitch.conf");
        } finally {
            try {
                if (usbswitch != null)
                    usbswitch.close();
            } catch (IOException e) {
                Log.e(TAG, MODULE + ": " + e.getMessage());
            }
        }

        /* Update the value of usbswitch in /data/data/com.intel.amtl/file/usbswitch.conf
        * 0: usb ape
        * 1: usb modem */
        try {
            this.rtm.exec("start usbswitch_status");
        } catch (IOException e) {
            Log.e(TAG, MODULE + ": can't start the service usbswitch_status");
        }
    }

    /* Get current service value */
    protected int getCurService() {
        return this.serviceValue;
    }

    /* Get current trace level value */
    protected int getCurTraceLevel() {
        return this.traceLevelValue;
    }

    /* Get current XSIO value */
    protected int getXsioValue() {
        return this.xsioValue;
    }

    /* Get current info modem reboot value */
    protected int getInfoModemReboot() {
        return this.infoModemReboot;
    }

    /* Get MUX trace status */
    protected int getMuxTraceValue() {
        return this.muxTraceValue;
    }

    /* Get offline logging */
    protected int getOfflineLogging() {
        int config = CustomCfg.OFFLINE_LOGGING_NONE;
        String tty = SystemProperties.get("persist.service.mts.input", null);
        if (tty != null) {
            if (tty.equals(this.platformConfig.getInputOfflineUsb())) {
                config = CustomCfg.OFFLINE_LOGGING_USB;
            } else if (tty.equals(this.platformConfig.getInputOfflineHsi())) {
                config = CustomCfg.OFFLINE_LOGGING_HSI;
            }
        } else {
            Log.e(TAG, MODULE + ": tty cannot be found.");
        }
        return config;
    }

    /* Check if SIGUSR1 signal has to be sent to mts to unconfigure the ldisc */
    protected boolean signalToSend() {
        boolean SignalToSend = false;
        if ((this.futCfg != PredefinedCfg.PTI_BP_LOG)
                && (this.futCustomCfg.traceLocation != CustomCfg.TRACE_LOC_PTI_MODEM)
                && (this.serviceValue == Services.MTS_PTI))
            SignalToSend = true;
        return SignalToSend;
    }

    /* Exit from Amtl if a TextView is null */
    static protected void exitIfNull (TextView tv, Activity a) {
        if (tv == null) {
            Log.e(TAG, MODULE + ": invalid null reference.\nAMTL will exit.");
            a.finish();
        }
    }

    private void closeGsmtty() {
        try {
            if (this.gsmtty != null) {
                this.gsmtty.close();
                this.gsmtty = null;
            }
        } catch (IOException ex) {
                Log.e(TAG, MODULE + ": " + ex.toString() + "\nError while close gsmtty");
        } finally {
            if (this.ttyManager != null) {
                this.ttyManager.closeTty();
                this.ttyManager = null;
            }
        }
    }

    @Override
    public void onModemWarmReset(ModemNotificationArgs args) { }

    @Override
    public void onModemColdReset(ModemNotificationArgs args) { }

    @Override
    public void onModemShutdown(ModemNotificationArgs args) { }

    @Override
    public void onPlatformReboot(ModemNotificationArgs args) { }

    @Override
    public void onModemCoreDump(ModemNotificationArgs args) { }

    @Override
    public void onModemUp() {
        this.currentStatus = ModemStatus.UP;
        if (this.gsmtty == null) {
            if (this.ttyManager != null) {
                try {
                    this.ttyManager.openTty();
                    this.gsmtty = new RandomAccessFile(this.ttyManager.getTtyName(), "rw");
                } catch (IOException ex) {
                    Log.e(TAG, MODULE + String.format("Error while opening gsmtty"));
                } catch (IllegalArgumentException ex2) {
                    Log.e(TAG, MODULE + ": " + ex2.toString());
                }
            } else {
                Log.e(TAG, MODULE + "Cannot open gsmtty: ttyManager null");
            }
        }
    }

    @Override
    public void onModemDown() {
        this.currentStatus = ModemStatus.DOWN;

        try {
            if (this.gsmtty != null) {
                this.gsmtty.close();
                this.gsmtty = null;
            }
        } catch (IOException ex) {
            Log.e(TAG, MODULE + "Error while closing gsmtty");
        } finally {
            if (this.ttyManager != null) {
                this.ttyManager.closeTty();
            }
        }
    }

    @Override
    public void onModemDead() {
        this.onModemDown();
        this.currentStatus = ModemStatus.DEAD;
    }
}
