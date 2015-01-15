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
 * Author: Edward Marmounier <edwardx.marmounier@intel.com>
 * Author: Erwan Bracq <erwan.bracq@intel.com>
 * Author: Morgane Butscher <morganeX.butscher@intel.com>
 */

package com.intel.amtl.config_parser;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Xml;

import com.intel.amtl.AMTLApplication;
import com.intel.amtl.models.config.LogOutput;
import com.intel.amtl.models.config.Master;
import com.intel.amtl.models.config.ModemConf;
import com.intel.amtl.models.config.ModemLogOutput;
import com.intel.amtl.mts.MtsConf;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;


public class ConfigParser {

    private final String TAG = "AMTL";
    private final String MODULE = "ConfigParser";
    private boolean isDefaultConf = false;
    private Context context = null;

    public ConfigParser() {
    }

    public ConfigParser(Context context) {
        this.context = context;
    }

    public ArrayList<ModemLogOutput> parseConfig(InputStream inputStream)
            throws XmlPullParserException, IOException {
        int index = -1;

        ArrayList<ModemLogOutput> configOutputs = new ArrayList<ModemLogOutput>();

        XmlPullParser parser = Xml.newPullParser();
        int eventType = 0;

        parser.setInput(inputStream, null);

        eventType = parser.getEventType();

        Log.d(TAG, MODULE + ": Get XML file to parse.");

        while (eventType != XmlPullParser.END_DOCUMENT) {

            switch (eventType) {
                case XmlPullParser.START_TAG:
                    index++;
                    configOutputs.add(this.handleModemElement(index, parser));
                    break;
            }
            eventType = parser.next();
        }

        Log.d(TAG, MODULE + ": Completed XML file parsing.");
        return configOutputs;
    }

    public ModemConf parseShortConfig(InputStream inputStream)
            throws XmlPullParserException, IOException {

        String atXSIO = "";
        String atTRACE = "";
        String atXSYSTRACE = "";
        String flCmd = "";
        String mtsMode = null;
        MtsConf mtsConf = null;
        XmlPullParser parser = Xml.newPullParser();
        int eventType = 0;

        parser.setInput(inputStream, null);

        eventType = parser.getEventType();

        Log.d(TAG, MODULE + ": Get XML file to parse.");

        while (eventType != XmlPullParser.END_DOCUMENT) {

            switch (eventType) {
                case XmlPullParser.START_TAG:
                    if (isStartOf(parser, "at_trace")) {
                        if (parser.next() == XmlPullParser.TEXT) {
                            atTRACE = "AT+TRACE=" + parser.getText() + "\r\n";
                        }
                        Log.d(TAG, MODULE + ": Get element type AT+TRACE : " + atTRACE);
                    }
                    if (isStartOf(parser, "at_xsystrace")) {
                        if (parser.next() == XmlPullParser.TEXT) {
                            atXSYSTRACE = "AT+XSYSTRACE=" + parser.getText() + "\r\n";
                        }
                        Log.d(TAG, MODULE + ": Get element type AT+XSYSTRACE : " + atXSYSTRACE);
                    }
                    if (isStartOf(parser, "at_xsio")) {
                        if (parser.next() == XmlPullParser.TEXT) {
                            atXSIO = "AT+XSIO=" + parser.getText() + "\r\n";
                        }
                        Log.d(TAG, MODULE + ": Get element type AT+XSIO : " + atXSIO);
                    }
                    if (isStartOf(parser, "flush_cmd")) {
                        if (parser.next() == XmlPullParser.TEXT) {
                            flCmd = parser.getText() + "\r\n";
                        }
                        Log.d(TAG, MODULE + ": Get element type FLUSH COMMAND : " + flCmd);
                    }
                    if (isStartOf(parser, "mts")) {

                        mtsConf = new MtsConf (parser.getAttributeValue(null, "input"),
                                parser.getAttributeValue(null, "output"),
                                parser.getAttributeValue(null, "output_type"),
                                parser.getAttributeValue(null, "rotate_num"),
                                parser.getAttributeValue(null, "rotate_size"),
                                parser.getAttributeValue(null, "interface"),
                                parser.getAttributeValue(null, "buffer_size"));
                        mtsMode = parser.getAttributeValue(null, "mode");
                        Log.d(TAG, MODULE + ": Get mts input : "
                                + parser.getAttributeValue(null, "input"));
                        Log.d(TAG, MODULE + ": Get mts output : "
                                + parser.getAttributeValue(null, "output"));
                        Log.d(TAG, MODULE + ": Get mts output_type : "
                                + parser.getAttributeValue(null, "output_type"));
                        Log.d(TAG, MODULE + ": Get mts rotate_num : "
                                + parser.getAttributeValue(null, "rotate_num"));
                        Log.d(TAG, MODULE + ": Get mts rotate_size : "
                                + parser.getAttributeValue(null, "rotate_size"));
                        Log.d(TAG, MODULE + ": Get mts interface : "
                                + parser.getAttributeValue(null, "interface"));
                        Log.d(TAG, MODULE + ": Get mts buffer_size : "
                                + parser.getAttributeValue(null, "buffer_size"));
                        Log.d(TAG, MODULE + ": Get mts type mode : " + mtsMode);
                    }
                break;
            }
            eventType = parser.next();
        }
        Log.d(TAG, MODULE + ": Completed XML file parsing.");
        ModemConf modConf = ModemConf.getInstance(atXSIO, atTRACE, atXSYSTRACE, flCmd, "");
        if (mtsConf != null) {
            modConf.setMtsConf(mtsConf);
        }
        if (mtsMode != null) {
            modConf.setMtsMode(mtsMode);
        }
        return (modConf);
    }

    private ModemLogOutput handleModemElement(int index, XmlPullParser parser)
            throws XmlPullParserException, IOException {
        ModemLogOutput ret = null;
        int outIndex = -1;
        if (isStartOf(parser, "modem")) {
            Log.d(TAG, MODULE + ": Get element type MODEM, index: " + index
                    + ", -> WILL PARSE IT.");

            // default_flush_cmd is the flush ops that will be performed
            // by default (if specified in xml) on all use case:
            // log start, log stop and at command error.

            // default_flush_cmd have to be specified only once in whatever
            // of the output type in the xml.

            // default_flush_cmd can be overwritten by flush_cmd parameter
            // flush_cmd will only by used on log start use case, and only
            // for the output type where it is specified in xml.
            // This implies that flush_cmd parameter does not have effect
            // on log stop and at command error use cases.

            // TODO default flush cmd per modem
            if (parser.getAttributeValue(null, "default_flush_cmd") != null
                    && this.context != null) {
                SharedPreferences.Editor editor =
                        context.getSharedPreferences("AMTLPrefsData", Context.MODE_PRIVATE).edit();
                editor.putString("default_flush_cmd",
                        parser.getAttributeValue(null, "default_flush_cmd"));
                editor.commit();
            }

            ret = new ModemLogOutput(index,
                    parser.getAttributeValue(null, "name"),
                    parser.getAttributeValue(null, "connection_id"),
                    parser.getAttributeValue(null, "service_to_start"),
                    parser.getAttributeValue(null, "default_flush_cmd"),
                    parser.getAttributeValue(null, "at_legacy_cmd"),
                    parser.getAttributeValue(null, "use_mmgr"),
                    parser.getAttributeValue(null, "full_stop_cmd"),
                    parser.getAttributeValue(null, "dft_cfg_onstop"),
                    parser.getAttributeValue(null, "modem_interface"));

            Log.d(TAG, MODULE + ": index = " + index
                    + ", name = " + parser.getAttributeValue(null, "name")
                    + ", connection_id = " + parser.getAttributeValue(null, "connection_id")
                    + ", service_to_start = " + parser.getAttributeValue(null, "service_to_start")
                    + ", default_flush_cmd = " + parser.getAttributeValue(null, "default_flush_cmd")
                    + ", at_legacy_cmd = " + parser.getAttributeValue(null, "at_legacy_cmd")
                    + ", use_mmgr = " + parser.getAttributeValue(null, "use_mmgr")
                    + ", full_stop_cmd = " + parser.getAttributeValue(null, "full_stop_cmd")
                    + ", dft_cfg_onstop = " + parser.getAttributeValue(null, "dft_cfg_onstop")
                    + ", modem_interface = " + parser.getAttributeValue(null, "modem_interface")
                    + ".");

            while (!isEndOf(parser, "modem")) {
                LogOutput out = this.handleOutputElement(outIndex + 1, parser);
                if (out != null) {
                    if (isDefaultConf) {
                        ret.setDefaultConfig(out);
                    }
                    ret.addOutputToList(out);
                    outIndex++;
                }
                parser.next();
            }
            Log.d(TAG, MODULE + ": Completed element type MODEM parsing.");
        }

        return ret;
    }

    private LogOutput handleOutputElement(int index, XmlPullParser parser)
            throws XmlPullParserException, IOException {
        LogOutput ret = null;
        String flcmd = null;

        this.isDefaultConf = false;

        if (isStartOf(parser, "output")) {
            Log.d(TAG, MODULE + ": Get element type OUTPUT, index: " + index
                    + ", -> WILL PARSE IT.");
        } else if (isStartOf(parser, "defaultconf")) {
            Log.d(TAG, MODULE + ": Get element type DEFAULTCONF, index: " + index
                    + ", -> WILL PARSE IT.");
            this.isDefaultConf = true;
        } else {
            return null;
        }

        ret = new LogOutput(index,
                parser.getAttributeValue(null, "name"),
                parser.getAttributeValue(null, "value"),
                parser.getAttributeValue(null, "color"),
                parser.getAttributeValue(null, "mts_input"),
                parser.getAttributeValue(null, "mts_output"),
                parser.getAttributeValue(null, "mts_output_type"),
                parser.getAttributeValue(null, "mts_rotate_num"),
                parser.getAttributeValue(null, "mts_rotate_size"),
                parser.getAttributeValue(null, "mts_interface"),
                parser.getAttributeValue(null, "mts_mode"),
                parser.getAttributeValue(null, "mts_buffer_size"),
                parser.getAttributeValue(null, "oct"),
                parser.getAttributeValue(null, "oct_fcs"),
                parser.getAttributeValue(null, "pti1"),
                parser.getAttributeValue(null, "pti2"),
                parser.getAttributeValue(null, "sigusr1_to_send"),
                parser.getAttributeValue(null, "flush_cmd"));

        Log.d(TAG, MODULE + ": index = " + index
                + ", name = " + parser.getAttributeValue(null, "name")
                + ", value = " + parser.getAttributeValue(null, "value")
                + ", color = " + parser.getAttributeValue(null, "color")
                + ", mts_input = " + parser.getAttributeValue(null, "mts_input")
                + ", mts_output = " + parser.getAttributeValue(null, "mts_output")
                + ", mts_output_type = " + parser.getAttributeValue(null, "mts_output_type")
                + ", mts_rotate_num = " + parser.getAttributeValue(null, "mts_rotate_num")
                + ", mts_rotate_size = " + parser.getAttributeValue(null, "mts_rotate_size")
                + ", mts_interface = " + parser.getAttributeValue(null, "mts_interface")
                + ", mts_mode = " + parser.getAttributeValue(null, "mts_mode")
                + ", mts_buffer_size = " + parser.getAttributeValue(null, "mts_buffer_size")
                + ", oct = " + parser.getAttributeValue(null, "oct")
                + ", oct_fcs = " + parser.getAttributeValue(null, "oct_fcs")
                + ", pti1 = " + parser.getAttributeValue(null, "pti1")
                + ", pti2 = " + parser.getAttributeValue(null, "pti2")
                + ", sigusr1_to_send = " + parser.getAttributeValue(null, "sigusr1_to_send")
                + ", default_flush_cmd = " + parser.getAttributeValue(null, "default_flush_cmd")
                + ", flush_cmd = " + parser.getAttributeValue(null, "flush_cmd") + ".");

        while (!isEndOf(parser, "output") && !isEndOf(parser, "defaultconf")) {
            this.handleMasterElements(parser, ret);
            parser.next();
        }

        if (isEndOf(parser, "defaultconf")) {
            Log.d(TAG, MODULE + ": Completed element type DEFAULTCONF parsing.");
        } else {
            Log.d(TAG, MODULE + ": Completed element type OUTPUT parsing.");
        }

        return ret;
    }

    private void handleMasterElements(XmlPullParser parser, LogOutput output)
            throws XmlPullParserException, IOException {
        String name = null;
        String defaultPort = null;
        String defaultConf = null;
        Master master = null;

        if (isStartOf(parser, "master")) {

            Log.d(TAG, MODULE + ": Get element type MASTER -> WILL PARSE IT.");
            name = parser.getAttributeValue(null, "name");
            defaultPort = parser.getAttributeValue(null, "default_port");
            defaultConf = parser.getAttributeValue(null, "default_conf");

            Log.d(TAG, MODULE + ": Element MASTER, name = " + name + ", default_port = "
                    + defaultPort + ", default_conf = " + defaultConf + ".");

            if (name != null) {
                master = new Master(name, defaultPort, defaultConf);
                output.addMasterToList(name, master);
            }
            Log.d(TAG, MODULE + ": Completed element type MASTER parsing.");
        }

    }

    private static boolean isEndOf(XmlPullParser parser, String tagName)
            throws XmlPullParserException {

        return (parser.getEventType() == XmlPullParser.END_TAG
                && tagName.equalsIgnoreCase(parser.getName()));
    }

    private static boolean isStartOf(XmlPullParser parser, String tagName)
            throws XmlPullParserException {

        return (parser.getEventType() == XmlPullParser.START_TAG
                && tagName.equalsIgnoreCase(parser.getName()));
    }
}
