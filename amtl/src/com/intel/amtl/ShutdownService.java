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
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import android.os.SystemProperties;

import java.io.IOException;
import java.lang.RuntimeException;

public class ShutdownService extends Service {

    private static final String MODULE = "ShutdownService";
    private final Handler handler = new Handler();

    private AmtlCore core;

    private static final int PTI_KILL_WAIT = 1000;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        /* Get application core */
        try {
            core = AmtlCore.get();
            this.core.setContext(this.getApplicationContext());
        } catch (AmtlCoreException e) {
            Log.e(AmtlCore.TAG, MODULE + ": failed to get Amtl core");
            this.stopSelf();
        }
    }

    @Override
    public void onStart(Intent intent, int startId) {
        new AsyncShutdownServiceInitTask().execute((Void)null);
    }

    private synchronized void init() {
        if (this.core != null) {
            /* If configuration has changed, tells AmtlCore to apply the new configuration */
            if (this.core.rebootNeeded()) {
                Log.i(AmtlCore.TAG, MODULE + ": apply new configuration");
                try {
                    if (this.core.signalToSend()) {
                        try {
                            AmtlCore.rtm.exec("start pti_sigusr1");
                            android.os.SystemClock.sleep(PTI_KILL_WAIT);
                        } catch (IOException e) {
                            Log.e(AmtlCore.TAG, MODULE + ": can't send sigusr1 signal");
                        }
                    }
                    this.core.applyCfg();
                    Runnable displayToast = new Runnable() {
                        @Override
                        public void run() {
                            Toast toast = Toast.makeText(ShutdownService.this, "New Amtl configuration applied", Toast.LENGTH_LONG);
                            toast.show();
                        }
                    };
                    this.handler.post(displayToast);
                } catch (final AmtlCoreException e) {
                    Runnable displayToast = new Runnable() {
                        @Override
                        public void run() {
                            Toast toast = Toast.makeText(ShutdownService.this, e.getMessage(), Toast.LENGTH_LONG);
                            toast.show();
                        }
                    };
                    this.handler.post(displayToast);
                }
            }
        }
    }

    private class AsyncShutdownServiceInitTask extends AsyncTask<Void, Integer, Void> {

        protected Void doInBackground(Void... parms) {

            try {
                Log.i(AmtlCore.TAG, MODULE + ": ShutdownService init starting...");
                ShutdownService.this.init();
            } catch (Exception ex) {
                Log.e(AmtlCore.TAG, MODULE + ": " + ex.getMessage(), ex);
            }
            return null;
        }

        protected void onPostExecute(Void result) {
            Log.i(AmtlCore.TAG, MODULE + ": ShutdownService init done.");
        }
    }
}
