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

    public final static int XSIO_0 = 0;
    public final static int XSIO_1 = 1;
    public final static int XSIO_2 = 2;
    public final static int XSIO_4 = 4;

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
    private static final String AT_SET_TRACE_LEVEL_DISABLE
            = "at+trace=0,115200,\"st=0,pr=0,bt=0,ap=0,db=0,lt=0,li=0,ga=0,ae=0\"\r\n";
    private static final String AT_SET_TRACE_LEVEL_BB
            = "at+trace=,115200,\"st=1,pr=1,bt=1,ap=0,db=1,lt=0,li=1,ga=0,ae=0\"\r\n";
    private static final String AT_SET_TRACE_LEVEL_BB_3G
            = "at+trace=,115200,\"st=1,pr=1,bt=1,ap=0,db=1,lt=0,li=1,ga=0,ae=1\"\r\n";
    private static final String AT_SET_TRACE_LEVEL_BB_3G_DIGRF
            = "at+trace=,115200,\"st=1,pr=1,bt=1,ap=0,db=1,lt=0,li=1,ga=0,ae=1\"\r\n";

    protected final static int xsio_00 = 0;
    protected final static int xsio_20 = 1;
    protected final static int xsio_22 = 2;
    protected final static int xsio_02 = 3;
    protected final static int xsio_40 = 4;
    protected final static int xsio_44 = 5;
    protected final static int xsio_04 = 6;
    protected final static int xsio_24 = 7;
    protected final static int xsio_42 = 8;
    protected final static int xsio_01 = 9;
    protected final static int xsio_10 = 10;
    protected final static int xsio_11 = 11;
    protected final static int xsio_14 = 12;
    protected final static int xsio_41 = 13;
    protected final static int reboot_ok0 = 50;
    protected final static int reboot_ko0 = 51;
    protected final static int reboot_ok2 = 52;
    protected final static int reboot_ko2 = 53;
    protected final static int reboot_ok4 = 54;
    protected final static int reboot_ko4 = 55;
    protected final static int reboot_ok1 = 56;
    protected final static int reboot_ko1 = 57;

    /* Simplify the modem status : rebooted (ok) or not rebooted(ko) */
    protected int modem_reboot_status(int reboot_value) {
        int ret = reboot_ok0;
        switch (reboot_value) {
            case xsio_00:
                /* xsio = 0 and modem has been rebooted */
                ret = reboot_ok0;
                break;
            case xsio_01:
            case xsio_02:
            case xsio_04:
                /* xsio = 0 and modem has not been rebooted */
                ret = reboot_ko0;
                break;
            case xsio_11:
                /* xsio = 1 and modem has been rebooted */
                ret = reboot_ok1;
                break;
            case xsio_10:
            case xsio_14:
                /* xsio = 1 and modem has not been rebooted */
                ret = reboot_ko1;
                break;
            case xsio_22:
                /* xsio = 2 and modem has been rebooted */
                ret = reboot_ok2;
                break;
            case xsio_20:
            case xsio_24:
                /* xsio = 2 and modem has not been rebooted */
                ret = reboot_ko2;
                break;
            case xsio_44:
                /* xsio = 4 and modem has been rebooted */
                ret = reboot_ok4;
                break;
            case xsio_40:
            case xsio_41:
            case xsio_42:
                /* xsio = 4 and modem has not been rebooted */
                ret = reboot_ko4;
                break;
            default:
                ret = reboot_ok0;
                break;
        }
        return ret;
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
        int ret = xsio_00;
        if (s.contains("0, *0")) {
            ret = xsio_00;
        } else if (s.contains("2, *0")) {
            ret = xsio_20;
        } else if (s.contains("2, *2")) {
            ret = xsio_22;
        } else if (s.contains("0, *2")) {
            ret = xsio_02;
        } else if (s.contains("4, *0")) {
            ret = xsio_40;
        } else if (s.contains("4, *4")) {
            ret = xsio_44;
        } else if (s.contains("0, *4")) {
            ret = xsio_04;
        } else if (s.contains("2, *4")) {
            ret = xsio_24;
        } else if (s.contains("4, *2")) {
            ret = xsio_42;
        } else if (s.contains("0, *1")) {
            ret = xsio_01;
        } else if (s.contains("1, *0")) {
            ret = xsio_10;
        } else if (s.contains("1, *1")) {
            ret = xsio_11;
        } else if (s.contains("4, *1")) {
            ret = xsio_41;
        } else if (s.contains("1, *4")) {
            ret = xsio_14;
        } else {
            ret= xsio_00;
        }
        return ret;
    }

    /* Set XSIO configuration */
    protected void setXsio(RandomAccessFile f, int xsio) {
        String atCmd;
        switch (xsio) {
            case XSIO_1:
                /* Enable usb acm */
                atCmd = String.format(AT_SET_XSIO_FMT, XSIO_1);
                break;
            case XSIO_2:
                /* Enable coredump */
                atCmd = String.format(AT_SET_XSIO_FMT, XSIO_2);
                break;
            case XSIO_4:
                /* Enable hsi */
                atCmd = String.format(AT_SET_XSIO_FMT, XSIO_4);
                break;
            default:
                /* Disable */
                atCmd = String.format(AT_SET_XSIO_FMT, XSIO_0);
                break;
        }
        try {
            read_write_modem(f, atCmd);
        } catch (IOException e) {
            Log.e(AmtlCore.TAG, MODULE + ": can't enable_frequency");
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
                read_write_modem(f, AT_SET_TRACE_LEVEL_BB);
                read_write_modem(f, sysTraceBb);
                break;
            case CustomCfg.TRACE_LEVEL_BB_3G:
                /* MA & Artemis traces */
                read_write_modem(f, AT_SET_TRACE_LEVEL_BB_3G);
                read_write_modem(f, sysTrace3G);
                break;
            case CustomCfg.TRACE_LEVEL_BB_3G_DIGRF:
                /* MA & Artemis & Digrf traces */
                read_write_modem(f, AT_SET_TRACE_LEVEL_BB_3G_DIGRF);
                read_write_modem(f, sysTraceDigrf);
                break;
            default:
                /* Disable trace */
                read_write_modem(f, AT_SET_TRACE_LEVEL_DISABLE);
                read_write_modem(f, AT_SET_XSYSTRACE_LEVEL_DISABLE);
                break;
            }
        } catch (IOException e) {
            Log.e(AmtlCore.TAG, MODULE + ": can't set trace level");
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

    protected int getMuxTraceState(RandomAccessFile f) throws IOException {
        return read_write_modem(f, AT_GET_MUX_TRACE);
    }
}
