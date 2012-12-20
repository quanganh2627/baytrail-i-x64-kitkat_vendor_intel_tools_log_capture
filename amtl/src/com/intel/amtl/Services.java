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

import android.util.Log;
import android.os.SystemProperties;

import java.io.IOException;

public class Services {

    private static final String MODULE = "Services";

    public static final String PERSIST_MTS_NAME = "persist.service.mts.name";

    /* Services values */
    protected final static int MTS_DISABLE = 0;
    protected final static int MTS_FS = 1;
    protected final static int MTS_EXTFS = 2;
    protected final static int MTS_SD = 3;
    protected final static int MTS_EXTSD = 4;
    protected final static int MTS_USB = 5;
    protected final static int ONLINE_BP_LOG = 6;
    protected final static int MTS_PTI = 7;

    protected final static String TTYACM1 = "/dev/ttyACM1";
    protected final static String MDMTRACE = "/dev/mdmTrace";
    protected final static String GSMTTY18 = "/dev/gsmtty18";
    protected final static String EMMC_PATH = "/logs/bplog";
    private final String SDCARD_PATH = "/mnt/sdcard/logs/bplog";
    private final String USB_SOCKET_PORT = "6700";
    private final String PTI_PORT = "/dev/ttyPTI1";
    private final String SMALL_LOG_SIZE = "20000";
    private final String LARGE_LOG_SIZE_SD = "200000";
    private final String LARGE_LOG_SIZE_CTP_MFLD = "200000";
    private final String LARGE_LOG_SIZE_LEX = "25000";
    private final String FILE_OUTPUT_TYPE = "f";
    private final String SOCKET_OUTPUT_TYPE = "p";
    private final String PTI_OUTPUT_TYPE = "k";
    private final String EMPTY_STRING = "";

    private String inputTty;
    private String largeLogSizeEmmc;
    private String largeLogNumberEmmc;

    private int service_val;

    public Services() {
        this.largeLogSizeEmmc = (!AmtlCore.usbAcmEnabled && !AmtlCore.usbswitchEnabled) ?
            LARGE_LOG_SIZE_LEX : LARGE_LOG_SIZE_CTP_MFLD;
        this.largeLogNumberEmmc = (!AmtlCore.usbAcmEnabled && !AmtlCore.usbswitchEnabled) ?
            "6" : "3";
    }

    /* Enable selected service */
    protected void enable_service(int service, PredefinedCfg futurCfg, int offlineLog) {
        String service_name = "";
        switch (service) {
            case MTS_FS:
                /* emmc 100MB persistent */
                service_name = "mtsfs";
                this.inputTty = (offlineLog == CustomCfg.OFFLINE_LOGGING_USB) ?
                    TTYACM1 : AmtlCore.hsiTty;
                fillProperties(this.inputTty, FILE_OUTPUT_TYPE, EMMC_PATH, SMALL_LOG_SIZE, "5");
                break;
            case MTS_EXTFS:
                /* emmc 600MB (medfield-clovertrail) - 150MB (lexington) persistent */
                service_name = "mtsextfs";
                this.inputTty = (futurCfg == PredefinedCfg.OFFLINE_USB_BP_LOG ||
                    offlineLog == CustomCfg.OFFLINE_LOGGING_USB) ?
                    TTYACM1 : AmtlCore.hsiTty;
                fillProperties(this.inputTty, FILE_OUTPUT_TYPE, EMMC_PATH, this.largeLogSizeEmmc,
                    this.largeLogNumberEmmc);
                break;
            case MTS_SD:
                /* sdcard 100MB persistent */
                service_name = "mtssd";
                this.inputTty = (offlineLog == CustomCfg.OFFLINE_LOGGING_USB) ?
                    TTYACM1 : AmtlCore.hsiTty;
                fillProperties(this.inputTty, FILE_OUTPUT_TYPE, SDCARD_PATH, SMALL_LOG_SIZE, "5");
                break;
            case MTS_EXTSD:
                /* sdcard 600MB persistent*/
                service_name = "mtsextsd";
                this.inputTty = (offlineLog == CustomCfg.OFFLINE_LOGGING_USB) ?
                    TTYACM1 : AmtlCore.hsiTty;
                fillProperties(this.inputTty, FILE_OUTPUT_TYPE, SDCARD_PATH, LARGE_LOG_SIZE_SD,
                    "3");
                break;
            case ONLINE_BP_LOG:
                /* Online BP logging => usbmodem */
                service_name = "usbmodem";
                fillProperties(EMPTY_STRING, EMPTY_STRING, EMPTY_STRING, EMPTY_STRING,
                    EMPTY_STRING);
                break;
            case MTS_USB:
                /* USB oneshot */
                service_name = "mtsusb";
                fillProperties(GSMTTY18, SOCKET_OUTPUT_TYPE, USB_SOCKET_PORT, EMPTY_STRING,
                    EMPTY_STRING);
                break;
            case MTS_PTI:
                /* PTI BP logging => PTI */
                service_name = "mtspti";
                fillProperties(TTYACM1, PTI_OUTPUT_TYPE, PTI_PORT, EMPTY_STRING, EMPTY_STRING);
                break;
            case MTS_DISABLE:
                service_name = "disable";
                fillProperties(EMPTY_STRING, EMPTY_STRING, EMPTY_STRING, EMPTY_STRING,
                    EMPTY_STRING);
                break;
            default:
                fillProperties(EMPTY_STRING, EMPTY_STRING, EMPTY_STRING, EMPTY_STRING,
                    EMPTY_STRING);
                break;
        }
        Log.i(AmtlCore.TAG, MODULE + ": enable " + service_name + " service");
        SystemProperties.set(PERSIST_MTS_NAME, service_name);
    }

    protected void fillProperties(String input, String output_type, String output,
                                  String rotate_size, String rotate_num) {

        SystemProperties.set("persist.service.mts.input", input);
        SystemProperties.set("persist.service.mts.output_type", output_type);
        SystemProperties.set("persist.service.mts.output", output);
        SystemProperties.set("persist.service.mts.rotate_size", rotate_size);
        SystemProperties.set("persist.service.mts.rotate_num", rotate_num);
    }

    /* Return the number of service which is enabled */
    protected int service_status() {
        String persistMtsName = SystemProperties.get(PERSIST_MTS_NAME);
        Log.d(AmtlCore.TAG, MODULE + ": service " + persistMtsName);
        if (SystemProperties.get("init.svc.mtsp").equals("running")) {
            if (persistMtsName.equals("mtsfs")) {
                /* emmc 100MB persistent */
                service_val = MTS_FS;
            } else if (persistMtsName.equals("mtsextfs")) {
                /* emmc 600MB (medfield-clovertrail) - 150MB (lexington) persistent */
                service_val = MTS_EXTFS;
            } else if (persistMtsName.equals("mtssd")) {
                /* sdcard 100MB persistent */
                service_val = MTS_SD;
            } else if (persistMtsName.equals("mtsextsd")) {
                /* sdcard 600MB persistent */
                service_val = MTS_EXTSD;
            }
        } else if (SystemProperties.get("init.svc.mtso").equals("running")) {
            if (persistMtsName.equals("mtsusb")) {
                /* USB oneshot */
                service_val = MTS_USB;
            } else if (persistMtsName.equals("mtspti")) {
                /* PTI oneshot */
                service_val = MTS_PTI;
            }
        } else {
            if (SystemProperties.get("persist.service.usbmodem.enable").equals("1")) {
                /* Online BP logging => persistent USB to modem service */
                /* USB modem is done by a script starting and exiting continuously,
                   we can't rely on init.svc.... property */
                service_val = ONLINE_BP_LOG;
            } else {
                /* No service enabled */
                service_val = MTS_DISABLE;
            }
        }
        return service_val;
    }

    /* Stop the current service */
    protected void stop_service() {
        try {
            int service_status = service_status();
            switch(service_status) {
                case MTS_DISABLE:
                    /* Already disable => nothing to do */
                    break;
                case MTS_FS:
                case MTS_EXTFS:
                case MTS_SD:
                case MTS_EXTSD:
                    SystemProperties.set("persist.service.mtsp.enable", "0");
                    break;
                case MTS_USB:
                case MTS_PTI:
                    AmtlCore.rtm.exec("stop mtso");
                    break;
                case ONLINE_BP_LOG:
                    /* Persistent USB to modem service */
                    SystemProperties.set("persist.service.usbmodem.enable", "0");
                    break;
                default:
                    /* Do nothing */
                    break;
            }
            SystemProperties.set(PERSIST_MTS_NAME, "disable");
        } catch (IOException e) {
            Log.e(AmtlCore.TAG, MODULE + ": can't stop current running mtso");
        }
    }
}
