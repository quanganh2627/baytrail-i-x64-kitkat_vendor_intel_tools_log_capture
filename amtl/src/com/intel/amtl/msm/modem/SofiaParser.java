/* Android Modem Traces and Logs
 *
 * Copyright (C) Intel 2015
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
 * Author: Morgane Butscher <morganeX.butscher@intel.com>
 */

package com.intel.amtl.msm.modem;

import android.util.Log;

import com.intel.amtl.common.log.AlogMarker;
import com.intel.amtl.common.models.config.Master;
import com.intel.amtl.common.modem.CommandParser;

import java.io.IOException;
import java.util.ArrayList;

public class SofiaParser implements CommandParser {

    private final String TAG = "AMTL";
    private final String MODULE = "SofiaParser";

    public String parseXsioResponse(String xsio) {
        AlogMarker.tAB("SofiaParser.parseXsioResponse", "0");
        AlogMarker.tAE("SofiaParser.parseXsioResponse", "0");
        return "1";
    }

    public String parseMasterResponse(String xsystrace, String master) {
        AlogMarker.tAB("SofiaParser.parseMasterResponse", "0");
        int indexOfMaster;
        String masterResponse = "";
        int sizeOfMaster = master.length();

        if (sizeOfMaster <= 0) {
            Log.e(TAG, MODULE + ": cannot parse at+xsystrace=10 response");
        } else {
            indexOfMaster = xsystrace.indexOf(master);
            if ((indexOfMaster == -1)) {
                Log.e(TAG, MODULE + ": cannot parse at+xsystrace=10 response for master: "
                        + master);
            } else {
                String sub = xsystrace.substring(indexOfMaster + sizeOfMaster + 2);
                masterResponse = (sub.substring(0, sub.indexOf("\r\n"))).toUpperCase();
            }
        }
        AlogMarker.tAE("SofiaParser.parseMasterResponse", "0");
        return masterResponse;
    }

    public ArrayList<Master> parseXsystraceResponse(String xsystrace,
            ArrayList<Master> masterArray) {
        AlogMarker.tAB("SofiaParser.parseXsystraceResponse", "0");
        if (masterArray != null) {
            for (Master m: masterArray) {
                m.setDefaultPort(parseMasterResponse(xsystrace, m.getName()));
            }
        }
        AlogMarker.tAE("SofiaParser.parseXsystraceResponse", "0");
        return masterArray;
    }

    public String parseTraceResponse(String trace) {
        AlogMarker.tAB("SofiaParser.parseTraceResponse", "0");
        AlogMarker.tAE("SofiaParser.parseTraceResponse", "0");
        return "1";
    }

    public String parseOct(String xsystrace) {
        AlogMarker.tAB("SofiaParser.parseOct", "0");
        String oct = "";

        if (xsystrace != null) {
            int indexOfOct = xsystrace.indexOf("mode");
            String sub = xsystrace.substring(indexOfOct + 5);
            oct = sub.substring(0, 1);
        }
        AlogMarker.tAE("SofiaParser.parseOct", "0");
        return oct;
    }
}
