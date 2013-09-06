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

import android.util.Log;
import android.util.Xml;

import com.intel.amtl.models.config.LogOutput;
import com.intel.amtl.models.config.Master;
import com.intel.amtl.models.config.ModemConf;
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

    public ArrayList<LogOutput> parseConfig(InputStream inputStream)
            throws XmlPullParserException, IOException {

        ArrayList<LogOutput> configOutputs = new ArrayList<LogOutput>();

        XmlPullParser parser = Xml.newPullParser();
        int eventType = 0;

        parser.setInput(inputStream, null);

        eventType = parser.getEventType();

        Log.d(TAG, MODULE + ": Get XML file to parse.");

        while (eventType != XmlPullParser.END_DOCUMENT) {

            switch (eventType) {
                case XmlPullParser.START_TAG:
                    configOutputs.add(this.handleOutputElement(parser));
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
                    if (isStartOf(parser, "mts")) {

                        mtsConf = new MtsConf (parser.getAttributeValue(null, "input"),
                                parser.getAttributeValue(null, "output"),
                                parser.getAttributeValue(null, "output_type"),
                                parser.getAttributeValue(null, "rotate_num"),
                                parser.getAttributeValue(null, "rotate_size"));
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
                        Log.d(TAG, MODULE + ": Get mts type mode : " + mtsMode);
                    }
                break;
             }
             eventType = parser.next();
         }
         Log.d(TAG, MODULE + ": Completed XML file parsing.");
         ModemConf modConf = new ModemConf(atXSIO, atTRACE, atXSYSTRACE);
         if (mtsConf != null) {
             modConf.setMtsConf(mtsConf);
         }
         if (mtsMode != null) {
             modConf.setMtsMode(mtsMode);
         }
         return (modConf);
    }

    private LogOutput handleOutputElement(XmlPullParser parser)
            throws XmlPullParserException, IOException {

        LogOutput ret = null;

        if (isStartOf(parser, "output")) {

            Log.d(TAG, MODULE + ": Get element type OUTPUT -> WILL PARSE IT.");

            ret = new LogOutput(parser.getAttributeValue(null, "name"),
                    parser.getAttributeValue(null, "value"),
                    parser.getAttributeValue(null, "color"),
                    parser.getAttributeValue(null, "mts_input"),
                    parser.getAttributeValue(null, "mts_output"),
                    parser.getAttributeValue(null, "mts_output_type"),
                    parser.getAttributeValue(null, "mts_rotate_num"),
                    parser.getAttributeValue(null, "mts_rotate_size"),
                    parser.getAttributeValue(null, "mts_mode"),
                    parser.getAttributeValue(null, "oct"),
                    parser.getAttributeValue(null, "pti1"),
                    parser.getAttributeValue(null, "pti2"));

            Log.d(TAG, MODULE + ": name = " + parser.getAttributeValue(null, "name")
                    + ", value = " + parser.getAttributeValue(null, "value")
                    + ", color = " + parser.getAttributeValue(null, "color")
                    + ", mts_input = " + parser.getAttributeValue(null, "mts_input")
                    + ", mts_output = " + parser.getAttributeValue(null, "mts_output")
                    + ", mts_output_type = " + parser.getAttributeValue(null, "mts_output_type")
                    + ", mts_rotate_num = " + parser.getAttributeValue(null, "mts_rotate_num")
                    + ", mts_rotate_size = " + parser.getAttributeValue(null, "mts_rotate_size")
                    + ", mts_mode = " + parser.getAttributeValue(null, "mts_mode")
                    + ", oct = " + parser.getAttributeValue(null, "oct")
                    + ", pti1 = "+ parser.getAttributeValue(null, "pti1")
                    + ", pti2 = "+ parser.getAttributeValue(null, "pti2") + ".");

            while (!isEndOf(parser, "output")) {
                this.handleMasterElements(parser, ret);
                parser.next();
            }
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
