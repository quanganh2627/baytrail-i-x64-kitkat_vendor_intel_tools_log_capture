/* Android Modem Traces and Logs
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
 * Author: Morgane Butscher <morganeX.butscher@intel.com>
 */

package com.intel.amtl.common.mts;

import android.os.SystemProperties;
import android.util.Log;

import com.intel.amtl.common.log.AlogMarker;

import java.io.IOException;

public class MtsManager {

    private static final String TAG = "AMTL";
    private static final String MODULE = "MtsManager";

    public static final Runtime rtm = java.lang.Runtime.getRuntime();

    public static String getMtsInput() {
        AlogMarker.tAB("MtsManager.getMtsInput", "0");
        AlogMarker.tAE("MtsManager.getMtsInput", "0");
        return SystemProperties.get(MtsProperties.getInput());
    }

    public static String getMtsOutput() {
        AlogMarker.tAB("MtsManager.getMtsOutput", "0");
        AlogMarker.tAE("MtsManager.getMtsOutput", "0");
        return SystemProperties.get(MtsProperties.getOutput());
    }

    public static String getMtsOutputType() {
        AlogMarker.tAB("MtsManager.getMtsOutputType", "0");
        AlogMarker.tAE("MtsManager.getMtsOutputType", "0");
        return SystemProperties.get(MtsProperties.getOutputType());
    }

    public static String getMtsRotateNum() {
        AlogMarker.tAB("MtsManager.getMtsRotateNum", "0");
        AlogMarker.tAE("MtsManager.getMtsRotateNum", "0");
        return SystemProperties.get(MtsProperties.getRotateNum());
    }

    public static String getMtsRotateSize() {
        AlogMarker.tAB("MtsManager.getMtsRotateSize", "0");
        AlogMarker.tAE("MtsManager.getMtsRotateSize", "0");
        return SystemProperties.get(MtsProperties.getRotateSize());
    }

    public static String getMtsInterface() {
        AlogMarker.tAB("MtsManager.getMtsInterface", "0");
        AlogMarker.tAE("MtsManager.getMtsInterface", "0");
        return SystemProperties.get(MtsProperties.getInterface());
    }

    public static String getMtsBufferSize() {
        AlogMarker.tAB("MtsManager.getMtsBufferSize", "0");
        AlogMarker.tAE("MtsManager.getMtsBufferSize", "0");
        return SystemProperties.get(MtsProperties.getBufferSize());
    }

    public static void printMtsProperties() {
        AlogMarker.tAB("MtsManager.printMtsProperties", "0");
        Log.d(TAG, MODULE + ": ========= MTS CONFIGURATION =========");
        Log.d(TAG, MODULE + ": INPUT = " + getMtsInput());
        Log.d(TAG, MODULE + ": OUTPUT = " + getMtsOutput());
        Log.d(TAG, MODULE + ": OUTPUT TYPE = " + getMtsOutputType());
        Log.d(TAG, MODULE + ": ROTATE NUM = " + getMtsRotateNum());
        Log.d(TAG, MODULE + ": ROTATE SIZE = " + getMtsRotateSize());
        Log.d(TAG, MODULE + ": INTERFACE = " + getMtsInterface());
        Log.d(TAG, MODULE + ": BUFFER SIZE = " + getMtsBufferSize());
        Log.d(TAG, MODULE + ": STATUS = " + getMtsState());
        Log.d(TAG, MODULE + ": =======================================");
        AlogMarker.tAE("MtsManager.printMtsProperties", "0");
    }

    public static String getMtsState() {
        AlogMarker.tAB("MtsManager.getMtsState", "0");
        AlogMarker.tAE("MtsManager.getMtsState", "0");
        return SystemProperties.get(MtsProperties.getStatus());
    }

    public static String getMtsMode() {
        AlogMarker.tAB("MtsManager.getMtsMode", "0");
        String mode = "persistent";
        if (SystemProperties.get(MtsProperties.getOneshotService()).equals("1")) {
            mode = "oneshot";
        }
        AlogMarker.tAE("MtsManager.getMtsMode", "0");
        return mode;
    }

    /* Start mts service */
    public static void startService(String service) {
        AlogMarker.tAB("MtsManager.startService", "0");
        if (service.equals("persistent")) {
            startMtsPersistent();
        } else if (service.equals("oneshot")) {
            startMtsOneshot();
        } else {
            Log.d(TAG, MODULE + ": cannot start mts, wrong mts mode");
        }
        AlogMarker.tAE("MtsManager.startService", "0");
    }

    /* Start mts persistent */
    private static void startMtsPersistent() {
        AlogMarker.tAB("MtsManager.startMtsPersistent", "0");
        String persService = MtsProperties.getPersistentService();
        Log.d(TAG, MODULE + ": starting " + persService + " persistent");
        SystemProperties.set(persService, "1");
        AlogMarker.tAE("MtsManager.startMtsPersistent", "0");
    }

    /* Start mts oneshot */
    private static void startMtsOneshot() {
        AlogMarker.tAB("MtsManager.startMtsOneshot", "0");
        String oneshotService = MtsProperties.getOneshotService();
        Log.d(TAG, MODULE + ": starting " + oneshotService + " oneshot");
        try {
            rtm.exec("start " + oneshotService);
        } catch (IOException ex) {
            Log.e(TAG, MODULE + ": cannot start " + oneshotService + " oneshot");
        }
        AlogMarker.tAE("MtsManager.startMtsOneshot", "0");
    }

    /* Stop the current service */
    public static void stopServices() {
        AlogMarker.tAB("MtsManager.stopServices", "0");
        if (getMtsState().equals("running")) {
            stopMtsPersistent();
            stopMtsOneshot();
        }
        AlogMarker.tAE("MtsManager.stopServices", "0");
    }

    /* Stop mts persistent */
    private static void stopMtsPersistent() {
        AlogMarker.tAB("MtsManager.stopMtsPersistent", "0");
        String persService = MtsProperties.getPersistentService();
        Log.d(TAG, MODULE + ": stopping " + persService + " persistent");
        SystemProperties.set(persService, "0");
        AlogMarker.tAE("MtsManager.stopMtsPersistent", "0");
    }

    /* Stop mts oneshot */
    private static void stopMtsOneshot() {
        AlogMarker.tAB("MtsManager.stopMtsOneshot", "0");
        String oneshotService = MtsProperties.getOneshotService();
        try {
            Log.d(TAG, MODULE + ": stopping " + oneshotService + " oneshot");
            rtm.exec("stop " + oneshotService);
        } catch (IOException ex) {
            Log.e(TAG, MODULE + ": can't stop current running " + oneshotService + " oneshot");
        }
        AlogMarker.tAE("MtsManager.stopMtsOneshot", "0");
    }
}
