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
import android.util.Xml;

import java.io.FileInputStream;
import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class ConfigParser {

    private static final String MODULE = "ConfigParser";

    private final String CONFIGURATION_FILE = "/system/etc/amtl_configuration.xml";

    public void parseConfig(PlatformConfig cfg) {

        int eventType = 0;

        try {
            XmlPullParser parser = Xml.newPullParser();
            FileInputStream file = new FileInputStream(CONFIGURATION_FILE);

            parser.setInput(file, null);
            eventType = parser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {

                switch (eventType) {
                case XmlPullParser.START_TAG:
                    cfg = this.handleConfig(parser, cfg);
                    break;
                }
                eventType = parser.next();
            }
            file.close();
        } catch (IOException ex) {
            Log.e(AmtlCore.TAG, MODULE + ex.getMessage());
        } catch (XmlPullParserException ex) {
            Log.e(AmtlCore.TAG, MODULE + ex.getMessage());
        }
    }

    private PlatformConfig handleConfig(XmlPullParser parser, PlatformConfig ret) {

        try {
            if (isStartOf(parser, "configuration")) {

                ret.setPlatformVersion(parser.getAttributeValue(null, "platform_version"));
                ret.setCoredumpAvailable(Boolean
                        .parseBoolean(parser.getAttributeValue(null, "coredump")));
                ret.setOfflineUsbAvailable(Boolean
                        .parseBoolean(parser.getAttributeValue(null, "offline_usb")));
                ret.setOfflineHsiAvailable(Boolean
                        .parseBoolean(parser.getAttributeValue(null, "offline_hsi")));
                ret.setOnlineUsbAvailable(Boolean
                        .parseBoolean(parser.getAttributeValue(null, "online_usb")));
                ret.setOnlinePtiAvailable(Boolean
                        .parseBoolean(parser.getAttributeValue(null, "online_pti")));
                ret.setUsbswitchAvailable(Boolean
                        .parseBoolean(parser.getAttributeValue(null, "usbswitch")));

                ret.setCoredumpXsio(Integer
                        .parseInt(parser.getAttributeValue(null, "at_xsio_coredump")));
                ret.setOfflineUsbXsio(Integer
                        .parseInt(parser.getAttributeValue(null, "at_xsio_offline_usb")));
                ret.setOfflineHsiXsio(Integer
                        .parseInt(parser.getAttributeValue(null, "at_xsio_offline_hsi")));
                ret.setOnlineUsbXsio(Integer
                        .parseInt(parser.getAttributeValue(null, "at_xsio_online_usb")));
                ret.setOnlinePtiXsio(Integer
                        .parseInt(parser.getAttributeValue(null, "at_xsio_online_pti")));

                ret.setSmallLogSizeEmmc(parser.getAttributeValue(null, "small_log_size_emmc"));
                ret.setLargeLogSizeEmmc(parser.getAttributeValue(null, "large_log_size_emmc"));
                ret.setSmallLogSizeSdcard(parser.getAttributeValue(null, "small_log_size_sdcard"));
                ret.setLargeLogSizeSdcard(parser.getAttributeValue(null, "large_log_size_sdcard"));

                ret.setSmallLogNumEmmc(parser.getAttributeValue(null, "small_log_num_emmc"));
                ret.setLargeLogNumEmmc(parser.getAttributeValue(null, "large_log_num_emmc"));
                ret.setSmallLogNumSdcard(parser.getAttributeValue(null, "small_log_num_sdcard"));
                ret.setLargeLogNumSdcard(parser.getAttributeValue(null, "large_log_num_sdcard"));

                ret.setInputOfflineUsb(parser.getAttributeValue(null, "input_offline_usb"));
                ret.setInputOfflineHsi(parser.getAttributeValue(null, "input_offline_hsi"));
                ret.setInputOnlineUsb(parser.getAttributeValue(null, "input_online_usb"));
                ret.setInputOnlinePti(parser.getAttributeValue(null, "input_online_pti"));

                while (!isEndOf(parser, "configuration")) {
                    parser.next();
                }
            }
        } catch (IOException ex) {
            Log.e(AmtlCore.TAG, MODULE + ex.getMessage());
        } catch (XmlPullParserException ex) {
            Log.e(AmtlCore.TAG, MODULE + ex.getMessage());
        }
        return ret;
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
