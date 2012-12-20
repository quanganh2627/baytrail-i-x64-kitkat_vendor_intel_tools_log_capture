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

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.SystemProperties;
import android.util.Log;
import java.lang.Void;

import java.io.IOException;

public class BootService extends Service {

    private static final String MODULE = "BootService";
    private boolean serviceToRemove = true;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onStart(Intent intent, int startId) {

        new AsyncBootServiceInitTask().execute((Void)null);
    }

    private synchronized void init() {

        // Get selected service name
        String service_name = "";

        service_name = SystemProperties.get(Services.PERSIST_MTS_NAME);

        /* Test if requested service is already running */
        if (SystemProperties.get("init.svc.mtso").equals("running")) {
            if (service_name.equals("mtspti") || service_name.equals("mtsusb")) {
                Log.i(AmtlCore.TAG, MODULE + ": " + service_name + " service already running");
                serviceToRemove = false;
            }
        } else if (SystemProperties.get("init.svc.mtsp").equals("running")) {
            if (service_name.equals("mtsextfs") || service_name.equals("mtsfs") ||
                service_name.equals("mtsextsd") || service_name.equals("mtssd")) {
                Log.i(AmtlCore.TAG, MODULE + ": " + service_name + " service already running");
                serviceToRemove = false;
            }
        } else if (SystemProperties.get("init.svc.usb_to_modem").equals("running")) {
            if (service_name.equals("usbmodem")) {
                Log.i(AmtlCore.TAG, MODULE + ": " + service_name + " service already running");
                serviceToRemove = false;
            }
        }

        if (serviceToRemove) {
            /* Remove old running service if necessary */
            if (SystemProperties.get("init.svc.mtso").equals("running")) {
                try {
                    AmtlCore.rtm.exec("stop mtso");
                } catch (IOException e) {
                    Log.e(AmtlCore.TAG, MODULE + ": can't stop mtso");
                }
            } else if (SystemProperties.get("init.svc.mtsp").equals("running")) {
                SystemProperties.set("persist.service.mtsp.enable", "0");
            } else if (service_name.equals("usbmodem")) {
                if (SystemProperties.get("init.svc.usb_to_modem").equals("running")) {
                    SystemProperties.set("persist.service.usbmodem.enable", "0");
                }
            }
        }

        if (service_name.equals("mtspti") || service_name.equals("mtsusb")) {
            try {
                Log.i(AmtlCore.TAG, MODULE + ": start " + service_name + " service");
                AmtlCore.rtm.exec("start mtso");
            } catch (IOException e) {
                Log.e(AmtlCore.TAG, MODULE + ": can't start " + service_name  + "service");
            }
        } else if (service_name.equals("disable") || service_name.equals("")) {
            Log.i(AmtlCore.TAG, MODULE + ": MTS service disabled");
        } else if (service_name.equals("usbmodem")) {
            Log.i(AmtlCore.TAG, MODULE + ": start " + service_name + " service");
            SystemProperties.set("persist.service.usbmodem.enable", "1");
        } else {
            Log.i(AmtlCore.TAG, MODULE + ": start " + service_name + " service");
            SystemProperties.set("persist.service.mtsp.enable", "1");
        }
    }

    private class AsyncBootServiceInitTask extends AsyncTask<Void, Integer, Void> {

        protected Void doInBackground(Void... parms) {
            try {
                Log.i(AmtlCore.TAG, MODULE + ": BootService init starting...");

                BootService.this.init();
            } catch (Exception ex) {
                Log.e(AmtlCore.TAG, MODULE + ": " + ex.getMessage(), ex);
            }
            return null;
        }

        protected void onPostExecute(Void result) {
            Log.i(AmtlCore.TAG, MODULE + ": BootService init done.");
        }
    }
}
