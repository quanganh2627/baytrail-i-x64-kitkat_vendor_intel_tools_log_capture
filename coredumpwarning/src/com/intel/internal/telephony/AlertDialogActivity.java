/*
 * Copyright (C) 2012 Intel Corporation, All rights Reserved.
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
 */

package com.intel.internal.telephony.TelephonyEventsNotifier;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.content.Intent;
import android.app.NotificationManager;

/**
 * This activity displays an AlertDialog
 * The message is provided by dialogMsg intent extra parameter
 * This class extends the Activity class
 * @see Activity
 */
public class AlertDialogActivity extends Activity {
    /**
     * Override of onCreate method. It will display the AlertDialog
     *
     * @param savedInstanceState
     *
     * @return void
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CharSequence dialogMsg = getIntent().getCharSequenceExtra("dialogMsg");
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        AlertDialog alert = builder.setMessage(dialogMsg)
            .setTitle(getResources().getString(R.string.app_name))
            .setCancelable(false)
            .setIcon(android.R.drawable.stat_notify_error)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            })
            .show();
    }
}
