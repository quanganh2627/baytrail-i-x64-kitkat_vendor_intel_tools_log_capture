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

    /* Platform usbswitch availability flag */
    public static boolean usbswitchEnabled = false;
    /* Platform logging over USB CDC ACM availability flag */
    public static boolean usbAcmEnabled = false;
    /* Platform logging over PTI availability flag */
    public static boolean ptiEnabled = false;
    /* Tty to open to get modem log for hsi logging */
    public static String hsiTty = "";

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
    private int xsioValue;
    private int infoModemReboot;
    private int muxTraceValue;
    private int addTracesValue;

    private Services services;
    private ModemConfiguration modemCfg;
    private ModemStatusManager modemStatusManager;
    private SynchronizeSTMD ttyManager;

    private Context ctx;

    private RandomAccessFile gsmtty;

    /* Constructor */
    private AmtlCore() throws AmtlCoreException {
        Log.i(TAG, MODULE + ": create application core");

        try {
            /* Create status monitor and open gsmtty device */
            this.modemStatusManager = ModemStatusManager.getInstance();
            this.modemStatusManager.subscribeToEvent(this, ModemStatus.ALL, ModemNotification.ALL);
            this.ttyManager = new SynchronizeSTMD();
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

        /* Retrieve platform specificities */
        String usbswitchProperty = SystemProperties.get(USBSWITCH_PROP, null);
        String usbacmProperty = SystemProperties.get(USBACM_PROP, null);
        String ptiProperty = SystemProperties.get(PTI_PROP, null);
        this.hsiTty = Services.GSMTTY18;

        if (usbswitchProperty == null) {
            Log.e(TAG, MODULE + ": usbswitch property cannot be found.");
        } else if (usbswitchProperty.equals("true")) {
            this.usbswitchEnabled = true;
        }
        if (usbacmProperty == null) {
            Log.e(TAG, MODULE + ": usbacm property cannot be found.");
        } else if (usbacmProperty.equals("true")) {
            this.usbAcmEnabled = true;
            this.hsiTty = Services.MDMTRACE;
        }
        if (ptiProperty == null) {
            Log.e(TAG, MODULE + ": pti property cannot be found.");
        } else if (ptiProperty.equals("true")) {
            this.ptiEnabled = true;
        }

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
            try {
                Log.d(TAG, MODULE + ": openGsmtty");
                this.ttyManager.openTty();
                this.gsmtty = new RandomAccessFile(this.ttyManager.getTtyName(), "rw");
            } catch (Exception ex) {
                throw new AmtlCoreException(String.format("Error while opening %s",
                        this.ttyManager.getTtyName()));
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
        closeGsmtty();
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
        int xsioToSend = (this.usbAcmEnabled)
                ? ModemConfiguration.XSIO_0 : ModemConfiguration.XSIO_2;
        this.services.stop_service();
        this.modemCfg.setXsio(this.gsmtty, xsioToSend);
        this.modemCfg.setTraceLevel(this.gsmtty, CustomCfg.TRACE_LEVEL_BB_3G, this.usbAcmEnabled);
        this.services.enable_service(Services.MTS_DISABLE, this.futCfg,
                this.futCustomCfg.offlineLogging);
    }

    /* Apply green configuration */
    private void applyOfflineBpLogCfg() throws IOException {
        this.services.stop_service();
        this.modemCfg.setXsio(this.gsmtty, ModemConfiguration.XSIO_4);
        this.modemCfg.setTraceLevel(this.gsmtty, CustomCfg.TRACE_LEVEL_BB_3G, false);
        this.services.enable_service(Services.MTS_EXTFS, this.futCfg,
                this.futCustomCfg.offlineLogging);
    }

    /* Apply green configuration via USB for Clovertrail */
    private void applyOfflineUsbBpLogCfg() throws IOException {
        this.services.stop_service();
        this.modemCfg.setXsio(this.gsmtty, ModemConfiguration.XSIO_1);
        this.modemCfg.setTraceLevel(this.gsmtty, CustomCfg.TRACE_LEVEL_BB_3G, false);
        this.services.enable_service(Services.MTS_EXTFS, this.futCfg,
                this.futCustomCfg.offlineLogging);
    }

    /* Apply purple configuration */
    private void applyOnlineBpLogCfg() throws IOException {
        this.services.stop_service();
        this.modemCfg.setXsio(this.gsmtty, ModemConfiguration.XSIO_0);
        this.modemCfg.setTraceLevel(this.gsmtty, CustomCfg.TRACE_LEVEL_BB_3G, false);
        this.services.enable_service(Services.ONLINE_BP_LOG, this.futCfg,
                this.futCustomCfg.offlineLogging);
    }

    /* Apply purple configuration for Clovertrail */
    private void applyPtiBpLogCfg() throws IOException {
        this.services.stop_service();
        this.modemCfg.setXsio(this.gsmtty, ModemConfiguration.XSIO_1);
        this.modemCfg.setTraceLevel(this.gsmtty, CustomCfg.TRACE_LEVEL_BB_3G, false);
        this.services.enable_service(Services.MTS_PTI, this.futCfg,
                this.futCustomCfg.offlineLogging);
    }

    /* Apply yellow configuration */
    private void applyTraceDisableCfg() throws IOException {
        this.services.stop_service();
        this.modemCfg.setXsio(this.gsmtty, ModemConfiguration.XSIO_0);
        this.modemCfg.setTraceLevel(this.gsmtty, CustomCfg.TRACE_LEVEL_NONE, false);
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
                    ? ModemConfiguration.XSIO_1 : ModemConfiguration.XSIO_4;

        } else if (this.futCustomCfg.traceLocation == CustomCfg.TRACE_LOC_COREDUMP) {
            xsioToSet = (this.usbAcmEnabled)
                    ? ModemConfiguration.XSIO_0 : ModemConfiguration.XSIO_2;
        } else if (this.futCustomCfg.traceLocation == CustomCfg.TRACE_LOC_PTI_MODEM) {
            xsioToSet = ModemConfiguration.XSIO_1;
        } else {
            xsioToSet = ModemConfiguration.XSIO_0;
        }

        boolean isCoredump = ((xsioToSet == ModemConfiguration.XSIO_0)
                && (futCustomCfg.traceLevel != CustomCfg.TRACE_LEVEL_NONE)
                && (this.usbAcmEnabled));
        /* Apply configuration */
        this.services.stop_service();
        this.modemCfg.setXsio(this.gsmtty, xsioToSet);
        this.modemCfg.setTraceLevel(this.gsmtty, futCustomCfg.traceLevel, isCoredump);
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
            if (this.usbswitchEnabled) {
                /* If necessary Create and Update usbswitch.conf file */
                usbswitch_update();
            }
            this.openGsmtty();
            /* Recover the current configuration */
            /* Current service */
            this.serviceValue = this.services.service_status();
            /* Current trace level */
            this.traceLevelValue = this.modemCfg.getTraceLevel(this.gsmtty);
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
            this.curCustomCfg.muxTrace = this.muxTraceValue;
            this.curCustomCfg.addTraces = this.addTracesValue;

            /* Recover the modem reboot information */
            this.infoModemReboot = this.modemCfg.modem_reboot_status(xsioValue);

            if (((this.infoModemReboot == ModemConfiguration.reboot_ok2)
                    || (this.infoModemReboot == ModemConfiguration.reboot_ko2))
                    && (this.serviceValue == Services.MTS_DISABLE)
                    && (this.traceLevelValue == CustomCfg.TRACE_LEVEL_BB_3G)
                    && (!this.usbAcmEnabled)) {
                /* Trace in coredump enabled */
                this.curCfg = PredefinedCfg.COREDUMP;
            } else if (((this.infoModemReboot == ModemConfiguration.reboot_ok0)
                    || (this.infoModemReboot == ModemConfiguration.reboot_ko0))
                    && (this.serviceValue == Services.MTS_DISABLE)
                    && (this.traceLevelValue == CustomCfg.TRACE_LEVEL_BB_3G)
                    && (this.usbAcmEnabled)) {
                /* Trace in coredump enabled for redhookbay*/
                this.curCfg = PredefinedCfg.COREDUMP;
            } else if (((this.infoModemReboot == ModemConfiguration.reboot_ok4)
                    || (this.infoModemReboot == ModemConfiguration.reboot_ko4))
                    && (this.serviceValue == Services.MTS_EXTFS)
                    && (this.traceLevelValue == CustomCfg.TRACE_LEVEL_BB_3G)) {
                /* Trace in APE log file enabled */
                this.curCfg = PredefinedCfg.OFFLINE_BP_LOG;
            } else if (((this.infoModemReboot == ModemConfiguration.reboot_ok1)
                    || (this.infoModemReboot == ModemConfiguration.reboot_ko1))
                    && (this.serviceValue == Services.MTS_EXTFS)
                    && (this.traceLevelValue == CustomCfg.TRACE_LEVEL_BB_3G)) {
                /* Trace via in APE log file via USB enabled */
                this.curCfg = PredefinedCfg.OFFLINE_USB_BP_LOG;
            } else if (((this.infoModemReboot == ModemConfiguration.reboot_ok0)
                    || (this.infoModemReboot == ModemConfiguration.reboot_ko0))
                    && (this.serviceValue == Services.ONLINE_BP_LOG)
                    && (this.traceLevelValue == CustomCfg.TRACE_LEVEL_BB_3G)) {
                /* Online BP logging */
                this.curCfg = PredefinedCfg.ONLINE_BP_LOG;
            } else if (((this.infoModemReboot == ModemConfiguration.reboot_ok1)
                    || (this.infoModemReboot == ModemConfiguration.reboot_ko1))
                    && (this.serviceValue == Services.MTS_PTI)
                    && (this.traceLevelValue == CustomCfg.TRACE_LEVEL_BB_3G)) {
                /* PTI BP logging */
                this.curCfg = PredefinedCfg.PTI_BP_LOG;
            } else if (((this.infoModemReboot == ModemConfiguration.reboot_ok0)
                    || (this.infoModemReboot == ModemConfiguration.reboot_ko0))
                    && (this.serviceValue == Services.MTS_DISABLE)
                    && (this.traceLevelValue == CustomCfg.TRACE_LEVEL_NONE)) {
                /* Trace disabled */
                this.curCfg = PredefinedCfg.TRACE_DISABLE;
            } else {
                this.curCfg = PredefinedCfg.UNKNOWN_CFG;
            }

            switch (this.serviceValue) {
                case Services.MTS_DISABLE:
                    this.curCustomCfg.offlineLogging = CustomCfg.OFFLINE_LOGGING_NONE;
                    this.curCustomCfg.traceFileSize = CustomCfg.LOG_SIZE_NONE;
                    this.curCustomCfg.traceLocation =
                            (this.traceLevelValue == CustomCfg.TRACE_LEVEL_NONE)
                            ? CustomCfg.TRACE_LOC_NONE : CustomCfg.TRACE_LOC_COREDUMP;
                    break;
                case Services.MTS_FS:
                    this.curCustomCfg.offlineLogging =
                            (this.xsioValue == ModemConfiguration.xsio_44)
                            ? CustomCfg.OFFLINE_LOGGING_HSI : CustomCfg.OFFLINE_LOGGING_USB;
                    this.curCustomCfg.traceLocation = CustomCfg.TRACE_LOC_EMMC;
                    this.curCustomCfg.traceFileSize = CustomCfg.LOG_SIZE_SMALL;
                    break;
                case Services.MTS_EXTFS:
                    this.curCustomCfg.offlineLogging =
                            (this.xsioValue == ModemConfiguration.xsio_44)
                            ? CustomCfg.OFFLINE_LOGGING_HSI : CustomCfg.OFFLINE_LOGGING_USB;
                    this.curCustomCfg.traceLocation = CustomCfg.TRACE_LOC_EMMC;
                    this.curCustomCfg.traceFileSize = CustomCfg.LOG_SIZE_LARGE;
                    break;
                case Services.MTS_SD:
                    this.curCustomCfg.offlineLogging =
                            (this.xsioValue == ModemConfiguration.xsio_44)
                            ? CustomCfg.OFFLINE_LOGGING_HSI : CustomCfg.OFFLINE_LOGGING_USB;
                    this.curCustomCfg.traceLocation = CustomCfg.TRACE_LOC_SDCARD;
                    this.curCustomCfg.traceFileSize = CustomCfg.LOG_SIZE_SMALL;
                    break;
                case Services.MTS_EXTSD:
                    this.curCustomCfg.offlineLogging =
                            (this.xsioValue == ModemConfiguration.xsio_44)
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
            if (tty.equals(Services.TTYACM1)) {
                config = CustomCfg.OFFLINE_LOGGING_USB;
            } else if (tty.equals(hsiTty)) {
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
        ttyManager.closeTty();
        if (this.gsmtty != null) {
            try {
                this.gsmtty.close();
            } catch (IOException ex) {
                Log.e(TAG, MODULE + ": " + ex.toString());
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

        try {
            this.ttyManager.openTty();
            this.gsmtty = new RandomAccessFile(this.ttyManager.getTtyName(), "rw");
        } catch (Exception ex) {
            Log.e(TAG, MODULE + String.format("Error while opening %s",
                    this.ttyManager.getTtyName()));
        }
    }

    @Override
    public void onModemDown() {
        this.currentStatus = ModemStatus.DOWN;

        if (this.ttyManager != null) {
            try {
                this.ttyManager.closeTty();
                if (this.gsmtty != null) {
                    this.gsmtty.close();
                    this.gsmtty = null;
                }
            } catch (Exception ex) {
                Log.e(TAG, MODULE + String.format("Error while opening %s",
                        this.ttyManager.getTtyName()));
            }
        }
    }

    @Override
    public void onModemDead() {
        this.onModemDown();
        this.currentStatus = ModemStatus.DEAD;
    }
}
