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

package com.intel.amtl.mts;

import android.os.SystemProperties;
import android.util.Log;

import java.io.IOException;
import java.lang.RuntimeException;

public class MtsManager {

    private final String TAG = "AMTL";
    private final String MODULE = "MtsManager";

    public final Runtime rtm = java.lang.Runtime.getRuntime();

    public String getMtsInput() {
        return SystemProperties.get(MtsProperties.getInput());
    }

    public String getMtsOutput() {
        return SystemProperties.get(MtsProperties.getOutput());
    }

    public String getMtsOutputType() {
        return SystemProperties.get(MtsProperties.getOutputType());
    }

    public String getMtsRotateNum() {
        return SystemProperties.get(MtsProperties.getRotateNum());
    }

    public String getMtsRotateSize() {
        return SystemProperties.get(MtsProperties.getRotateSize());
    }

    public String getMtsInterface() {
        return SystemProperties.get(MtsProperties.getInterface());
    }

    public String getMtsBufferSize() {
        return SystemProperties.get(MtsProperties.getBufferSize());
    }

    public void printMtsProperties() {
        Log.d(TAG, MODULE + ": ========= MTS CONFIGURATION =========");
        Log.d(TAG, MODULE + ": INPUT = " + this.getMtsInput());
        Log.d(TAG, MODULE + ": OUTPUT = " + this.getMtsOutput());
        Log.d(TAG, MODULE + ": OUTPUT TYPE = " + this.getMtsOutputType());
        Log.d(TAG, MODULE + ": ROTATE NUM = " + this.getMtsRotateNum());
        Log.d(TAG, MODULE + ": ROTATE SIZE = " + this.getMtsRotateSize());
        Log.d(TAG, MODULE + ": INTERFACE = " + this.getMtsInterface());
        Log.d(TAG, MODULE + ": BUFFER SIZE = " + this.getMtsBufferSize());
        Log.d(TAG, MODULE + ": STATUS = " + this.getMtsState());
        Log.d(TAG, MODULE + ": =======================================");
    }

    public String getMtsState() {
        return SystemProperties.get(MtsProperties.getStatus());
    }

    /* Start mts service */
    public void startService(String service) {
        if (service.equals("persistent")) {
            this.startMtsPersistent();
        } else if (service.equals("oneshot")) {
            this.startMtsOneshot();
        } else {
            Log.d(TAG, MODULE + ": cannot start mts, wrong mts mode");
        }
    }

    /* Start mts persistent */
    private void startMtsPersistent() {
        String persService = MtsProperties.getPersistentService();
        Log.d(TAG, MODULE + ": starting " + persService + " persistent");
        SystemProperties.set(persService, "1");
    }

    /* Start mts oneshot */
    private void startMtsOneshot() {
        String oneshotService = MtsProperties.getOneshotService();
        Log.d(TAG, MODULE + ": starting " + oneshotService + " oneshot");
        try {
            rtm.exec("start " + oneshotService);
        } catch (IOException ex) {
            Log.e(TAG, MODULE + ": cannot start " + oneshotService + " oneshot");
        }
    }

    /* Stop the current service */
    public void stopServices() {
        if (getMtsState().equals("running")) {
            this.stopMtsPersistent();
            this.stopMtsOneshot();
        }
    }

    /* Stop mts persistent */
    private void stopMtsPersistent() {
        String persService = MtsProperties.getPersistentService();
        Log.d(TAG, MODULE + ": stopping " + persService + " persistent");
        SystemProperties.set(persService, "0");
    }

    /* Stop mts oneshot */
    private void stopMtsOneshot() {
        String oneshotService = MtsProperties.getOneshotService();
        try {
            Log.d(TAG, MODULE + ": stopping " + oneshotService + " oneshot");
            rtm.exec("stop " + oneshotService);
        } catch (IOException ex) {
            Log.e(TAG, MODULE + ": can't stop current running " + oneshotService + " oneshot");
        }
    }
}
