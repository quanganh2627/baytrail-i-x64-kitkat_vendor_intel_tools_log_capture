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

import java.io.IOException;
import java.io.RandomAccessFile;

public class ModemConfiguration {

    private static final String MODULE = "ModemConfiguration";

    /* XSIO AT commands */
    private static final String AT_SET_XSIO_FMT = "at+xsio=%d\r\n";
    private static final String AT_GET_XSIO = "at+xsio?\r\n";

    /* Additional trace AT commands */
    private static final String AT_SET_ADD_TRACES_ON = "at+xl1set=\"L6L7L8L9\"\r\n";
    private static final String AT_SET_ADD_TRACES_OFF = "at+xl1set=\"\"\r\n";

    /* MUX trace AT commands */
    private static final String AT_GET_MUX_TRACE = "at+xmux?\r\n";
    private static final String AT_SET_MUX_TRACE_ON = "at+xmux=1,3,-1\r\n";
    private static final String AT_SET_MUX_TRACE_OFF = "at+xmux=1,1,0\r\n";

    /* XSYSTRACE AT commands */
    private static final String AT_GET_TRACE_LEVEL = "at+xsystrace=10\r\n";
    private static final String AT_SET_XSYSTRACE_LEVEL_DISABLE = "at+xsystrace=0\r\n";
    private static final String AT_SET_XSYSTRACE_LEVEL_BB
            = "at+xsystrace=0,\"bb_sw=1;3g_sw=0;digrf=0\",,\"oct=4\"\r\n";
    private static final String AT_SET_XSYSTRACE_LEVEL_BB_3G
            = "at+xsystrace=0,\"bb_sw=1;3g_sw=1;digrf=0\",,\"oct=4\"\r\n";
    private static final String AT_SET_XSYSTRACE_LEVEL_BB_3G_DIGRF
            = "at+xsystrace=0,\"digrf=1;bb_sw=1;3g_sw=1\",\"digrf=0x84\",\"oct=4\"\r\n";
    /* XSYSTRACE AT commands for redhookbay when bplogs are enabled in coredump */
    private static final String AT_SET_XSYSTRACE_LEVEL_BB_RED
            = "at+xsystrace=0,\"bb_sw=1;3g_sw=0;digrf=0\",,\"oct=3\"\r\n";
    private static final String AT_SET_XSYSTRACE_LEVEL_BB_3G_RED
            = "at+xsystrace=0,\"bb_sw=1;3g_sw=1;digrf=0\",,\"oct=3\"\r\n";
    private static final String AT_SET_XSYSTRACE_LEVEL_BB_3G_DIGRF_RED
            = "at+xsystrace=0,\"digrf=1;bb_sw=1;3g_sw=1\",\"digrf=0x84\",\"oct=3\"\r\n";

    /* TRACE AT commands */
    private static final String AT_GET_TRACE = "at+trace?\r\n";
    private static final String AT_SET_TRACE_LEVEL_DISABLE = "at+trace=0\r\n";
    private static final String AT_SET_TRACE_LEVEL_ENABLE = "at+trace=1\r\n";

    private PlatformConfig platformConfig;

    public void ModemConfiguration () {
        this.platformConfig = PlatformConfig.get();
    }

    /* Send command to the modem and read the response */
    private int read_write_modem(RandomAccessFile f, String ival) throws IOException {
        int ret;
        byte rsp_byte_tmp[] = new byte[1024];
        int bRead = 0;
        int bCount = 0;
        byte rsp_byte[] = new byte[1024];
        byte ok_byte[] = new byte[1024];
        String modem_value;

        for (int i = 0;i < ok_byte.length;i++) {
            rsp_byte[i] = 0;
        }

        if (ival.startsWith("at+") || ival.startsWith("AT+")) {
            Log.d(AmtlCore.TAG, MODULE + ": sent command: " + ival);
        }

        f.writeBytes(ival);

        do {
            bRead = f.read(rsp_byte_tmp);
            for (int i = 0;i < bRead;i++) {
                rsp_byte[i+bCount] = rsp_byte_tmp[i];
            }
            bCount += bRead;
            if (bCount < 4) {
                return -1;
            }
        }
        /* find "OK\r\n" at reponse end */
        while (rsp_byte[bCount-4] != 0x4f || rsp_byte[bCount-3] != 0x4b
                || rsp_byte[bCount-2] != 0x0d || rsp_byte[bCount-1] != 0x0a);

        modem_value = new String(rsp_byte);
        logAtCommand(modem_value, ival);

        if (ival.equals(AT_GET_XSIO)) {
            ret = getXsioValue(modem_value);
        } else if (ival.equals(AT_GET_TRACE_LEVEL)) {
            ret = getTraceLevelValue(modem_value);
        } else if (ival.equals(AT_GET_TRACE)) {
            ret = getTraceStatus(modem_value);
        } else if (ival.equals(AT_GET_MUX_TRACE)) {
            if ((modem_value.contains("1,3,-1"))) {
                ret = CustomCfg.MUX_TRACE_ON;
            } else {
                ret = CustomCfg.MUX_TRACE_OFF;
            }
        } else {
            ret = -1;
        }
        return ret;
    }

    /* Log the response of the modem to an AT command */
    private void logAtCommand(String modemVal,String ival){
        String subModemValue;
        int indexOk = modemVal.indexOf("OK");

        if (indexOk == -1) {
            Log.e(AmtlCore.TAG, MODULE + ": cannot find OK in AT command response");
        } else {
            subModemValue = modemVal.substring(0, indexOk + 3);
            subModemValue = subModemValue.replace("\r\n\r\n", "  ");
            subModemValue = subModemValue.replace("\r\n", "  ");

            if (ival == AT_GET_TRACE_LEVEL) {
                int indexOfBb;
                int indexOf3g;
                int indexOfDigrf;
                String modemResponse;

                /* Only the states of bb_sw, 3g_sw and digrf are needed */
                indexOfBb = subModemValue.indexOf("0)");
                indexOf3g = subModemValue.indexOf("4)");
                indexOfDigrf = subModemValue.indexOf("10)");
                /* the index has changed because carriage return has been replaced by spaces */
                indexOk = subModemValue.indexOf("OK");

                if ((indexOfBb == -1) || (indexOf3g == -1) || (indexOfDigrf == -1)) {
                    Log.e(AmtlCore.TAG, MODULE + ": cannot find at+systrace=10 response");
                } else {
                    modemResponse = subModemValue.substring(indexOfBb,indexOfBb + 13);
                    modemResponse += " " + subModemValue.substring(indexOf3g,indexOf3g + 13);
                    modemResponse += " " + subModemValue.substring(indexOfDigrf,indexOfDigrf + 14);
                    modemResponse += "  " + subModemValue.substring(indexOk,indexOk + 2);
                    Log.d(AmtlCore.TAG, MODULE + ": modem response : " + modemResponse);
                }
            } else {
                Log.d(AmtlCore.TAG, MODULE + ": modem response : " + subModemValue);
            }
        }
    }

    /* Get trace from string */
    private int getTraceStatus(String s) {
        int ret = CustomCfg.TRACE_DISABLED;
        if (s.contains("+TRACE: 1")) {
            ret = CustomCfg.TRACE_ENABLED;
        }
        return ret;
    }

    /* Get trace level from string */
    private int getTraceLevelValue(String s) {
        int ret = CustomCfg.TRACE_LEVEL_NONE;
        if ((s.contains("bb_sw: Oct")) && (s.contains("3g_sw: Oct"))
                && (s.contains("digrf: Oct"))) {
            ret = CustomCfg.TRACE_LEVEL_BB_3G_DIGRF;
        } else if ((s.contains("bb_sw: Oct")) && (s.contains("3g_sw: Oct"))) {
            ret = CustomCfg.TRACE_LEVEL_BB_3G;
        } else if (s.contains("bb_sw: Oct")) {
            ret = CustomCfg.TRACE_LEVEL_BB;
        } else {
            ret = CustomCfg.TRACE_LEVEL_NONE;
        }
        return ret;
    }

    /* Get XSIO value from string */
    private int getXsioValue(String s) {
        int indexXsio = s.indexOf("+XSIO: ");
        return Integer.parseInt(s.substring(indexXsio+7, indexXsio+8));
    }

    /* Set XSIO configuration */
    protected void setXsio(RandomAccessFile f, int xsio) {
        this.platformConfig = PlatformConfig.get();
        if (this.platformConfig.isXsioAllowed(xsio)) {
            try {
                read_write_modem(f, String.format(AT_SET_XSIO_FMT, xsio));
            } catch (IOException e) {
                Log.e(AmtlCore.TAG, MODULE + ": can't set xsio");
            }
        } else {
            Log.e(AmtlCore.TAG, MODULE + ": the xsio to set is not allowed");
        }
    }

    /* Set trace level */
    protected void setTraceLevel(RandomAccessFile f, int level, boolean isCoredump) {
        String sysTraceBb = (isCoredump)
                ? AT_SET_XSYSTRACE_LEVEL_BB_RED : AT_SET_XSYSTRACE_LEVEL_BB;
        String sysTrace3G = (isCoredump)
                ? AT_SET_XSYSTRACE_LEVEL_BB_3G_RED : AT_SET_XSYSTRACE_LEVEL_BB_3G;
        String sysTraceDigrf = (isCoredump)
                ? AT_SET_XSYSTRACE_LEVEL_BB_3G_DIGRF_RED : AT_SET_XSYSTRACE_LEVEL_BB_3G_DIGRF;
        try {
            switch (level) {
            case CustomCfg.TRACE_LEVEL_BB:
                /* MA traces */
                read_write_modem(f, sysTraceBb);
                break;
            case CustomCfg.TRACE_LEVEL_BB_3G:
                /* MA & Artemis traces */
                read_write_modem(f, sysTrace3G);
                break;
            case CustomCfg.TRACE_LEVEL_BB_3G_DIGRF:
                /* MA & Artemis & Digrf traces */
                read_write_modem(f, sysTraceDigrf);
                break;
            default:
                /* Disable trace */
                read_write_modem(f, AT_SET_XSYSTRACE_LEVEL_DISABLE);
                break;
            }
        } catch (IOException e) {
            Log.e(AmtlCore.TAG, MODULE + ": can't set trace level");
        }
    }

   /* Set trace status */
    protected void setTraceStatus(RandomAccessFile f, int status) {
         try {
            switch (status) {
            case CustomCfg.TRACE_ENABLED:
                /* Enable traces */
                read_write_modem(f, AT_SET_TRACE_LEVEL_ENABLE);
                break;
            case CustomCfg.TRACE_DISABLED:
                /* Disable traces */
                read_write_modem(f, AT_SET_TRACE_LEVEL_DISABLE);
                break;
            default:
                /* Disable traces */
                read_write_modem(f, AT_SET_TRACE_LEVEL_DISABLE);
                break;
            }
        } catch (IOException e) {
            Log.e(AmtlCore.TAG, MODULE + ": can't set trace status");
        }
    }


    /* Set MUX traces on */
    protected void setMuxTraceOn(RandomAccessFile f) {
        try {
            read_write_modem(f, AT_SET_MUX_TRACE_ON);
        } catch (IOException e) {
            Log.e(AmtlCore.TAG, MODULE + ": can't set MUX trace ON");
        }
    }

    /* Set MUX traces off */
    protected void setMuxTraceOff(RandomAccessFile f) {
        try {
            read_write_modem(f, AT_SET_MUX_TRACE_OFF);
        } catch (IOException e) {
            Log.e(AmtlCore.TAG, MODULE + ": can't set MUX trace OFF");
        }
    }

    /* Set Additional traces on */
    protected void setAdditionalTracesOn(RandomAccessFile f) {
        try {
            read_write_modem(f, AT_SET_ADD_TRACES_ON);
        } catch (IOException e) {
            Log.e(AmtlCore.TAG, MODULE + ": can't set Additional traces ON");
        }
    }

    /* Set Additional traces off */
    protected void setAdditionalTracesOff(RandomAccessFile f) {
        try {
            read_write_modem(f, AT_SET_ADD_TRACES_OFF);
        } catch (IOException e) {
            Log.e(AmtlCore.TAG, MODULE + ": can't set Additional traces OFF");
        }
    }

    /* Get current trace level */
    protected int getTraceLevel(RandomAccessFile f) throws IOException {
        return read_write_modem(f, AT_GET_TRACE_LEVEL);
    }

    /* Get current XSIO */
    protected int getXsio(RandomAccessFile f) throws IOException {
        return read_write_modem(f, AT_GET_XSIO);
    }

     /* Get current trace*/
    protected int getTraceStatus(RandomAccessFile f) throws IOException {
        return read_write_modem(f, AT_GET_TRACE);
    }

    protected int getMuxTraceState(RandomAccessFile f) throws IOException {
        return read_write_modem(f, AT_GET_MUX_TRACE);
    }
}
