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
 * Author: Erwan Bracq <erwan.bracq@intel.com>
 */

package com.intel.amtl.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.intel.amtl.helper.LogManager;
import com.intel.amtl.helper.TelephonyStack;
import com.intel.amtl.models.config.ExpertConfig;
import com.intel.amtl.R;


public class UIHelper {

    /* Print pop-up message with ok and cancel buttons */
    public static void warningDialog(final Activity A, String title, String message,
            DialogInterface.OnClickListener listener) {
        new AlertDialog.Builder(A)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", listener)
                .setNegativeButton("Cancel", listener)
                .show();
    }

    /* Print pop-up message with ok and cancel buttons */
    public static void warningExitDialog(final Activity A, String title, String message) {
        new AlertDialog.Builder(A)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        /* Nothing to do */
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        /* Exit application */
                        A.finish();
                    }
                })
                .show();
    }

    /* Print pop-up message with ok and cancel buttons to save */
    public static void chooseExpertFile(final Activity A, String title, String message,
            final Context context, final ExpertConfig expConf, final Switch expSwitch) {
        final EditText saveInput = new EditText(context);
        saveInput.setText(expConf.getPath(), TextView.BufferType.EDITABLE);
        saveInput.setTextColor(Color.parseColor("#66ccff"));
        saveInput.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        new AlertDialog.Builder(A)
                .setView(saveInput)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String confFilePath = saveInput.getText().toString();
                        InputMethodManager imm = (InputMethodManager)
                                A.getSystemService(context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(saveInput.getWindowToken(), 0);
                        }
                        // if im is null, no specific issue, virtual keyboard will not be cleared
                        // parse file
                        expConf.setPath(confFilePath);
                        expConf.validateFile();
                        expConf.setConfigSet(true);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        expConf.setConfigSet(false);
                        expSwitch.performClick();
                    }
                })
                .setNeutralButton("Show", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String confFilePath = saveInput.getText().toString();
                        InputMethodManager imm = (InputMethodManager)
                                A.getSystemService(context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(saveInput.getWindowToken(), 0);
                        }
                        // if im is null, no specific issue, virtual keyboard will not be cleared
                        // parse file
                        expConf.setPath(confFilePath);
                        expConf.validateFile();
                        if (!expConf.displayConf().equals("")) {
                            okCancelDialog(A, "Expert Config File", expConf, expSwitch);
                        }
                    }
                })
                .show();
    }

    /* Print pop-up message with ok and cancel buttons to save */
    public static void savePopupDialog(final Activity A, String title, String message,
            final Context context, final LogManager snaplog, final SaveLogFrag logProgressFrag) {
        final EditText saveInput = new EditText(context);
        saveInput.setText("snapshot", TextView.BufferType.EDITABLE);
        saveInput.setTextColor(Color.parseColor("#66ccff"));
        saveInput.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        new AlertDialog.Builder(A)
                .setView(saveInput)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String snapshotTag = saveInput.getText().toString();
                        InputMethodManager imm = (InputMethodManager)
                            A.getSystemService(context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(saveInput.getWindowToken(), 0);
                        }
                        // if im is null, no specific issue, virtual keyboard will not be cleared
                        snaplog.setTag(snapshotTag);
                        logProgressFrag.launch(snaplog);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        /* Nothing to do */
                    }
                })
                .show();
    }

    /* Print pop-up message with ok and cancel buttons to save */
    public static void savePopupDialog(final Activity A, String title, String message,
            final String savepath, final Context context, final Runnable onOK,
            final Runnable onCancel) {
        final EditText saveInput = new EditText(context);
        saveInput.setText(savepath, TextView.BufferType.EDITABLE);
        saveInput.setTextColor(Color.parseColor("#66ccff"));
        saveInput.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        new AlertDialog.Builder(A)
                .setView(saveInput)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String snapshotTag = saveInput.getText().toString();
                        InputMethodManager imm = (InputMethodManager)
                            A.getSystemService(context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(saveInput.getWindowToken(), 0);
                        }

                        SharedPreferences sharedPrefs = PreferenceManager
                                .getDefaultSharedPreferences(context);
                        Editor sharedPrefsEdit = sharedPrefs.edit();
                        sharedPrefsEdit.putString(context.getString(
                                R.string.settings_user_save_path_key),
                                saveInput.getText().toString());
                        sharedPrefsEdit.commit();

                        onOK.run();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        SharedPreferences sharedPrefs = PreferenceManager
                                .getDefaultSharedPreferences(context);
                        Editor sharedPrefsEdit = sharedPrefs.edit();
                        sharedPrefsEdit.putString(context.getString(
                                R.string.settings_user_save_path_key), savepath);
                        sharedPrefsEdit.commit();

                        onCancel.run();
                    }
                })
                .show();
    }

    /* Print pop-up message with ok and cancel buttons to save */
    public static void cleanPopupDialog(final Activity A, String title, String message,
            final Runnable onOK, final Runnable onCancel) {
        new AlertDialog.Builder(A)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        onOK.run();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        onCancel.run();
                    }
                })
                .show();
    }

    /* Print pop-up message with ok and cancel buttons to save */
    public static void logTagDialog(final Activity A, String title, String message,
            final Context context) {
        final EditText saveInput = new EditText(context);
        saveInput.setText("TAG_TO_SET_IN_LOGS", TextView.BufferType.EDITABLE);
        saveInput.setTextColor(Color.parseColor("#66ccff"));
        saveInput.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        new AlertDialog.Builder(A)
                .setView(saveInput)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String logTag = saveInput.getText().toString();
                        InputMethodManager imm = (InputMethodManager)
                            A.getSystemService(context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(saveInput.getWindowToken(), 0);
                        }
                        // if im is null, no specific issue, virtual keyboard will not be cleared
                        Log.d("AMTL", "UIHelper: " + logTag);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        /* Nothing to do */
                    }
                })
                .show();
    }

    /* Print pop-up message with ok button */
    public static void okDialog(Activity A, String title, String message) {
        new AlertDialog.Builder(A)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        /* Nothing to do, waiting for user to press OK button */
                    }
                })
                .show();
    }

    /* Print pop-up message with ok and cancel buttons */
    public static void okCancelDialog(Activity A, String title, final ExpertConfig conf,
            final Switch expSwitch) {
        String message = conf.displayConf();
        new AlertDialog.Builder(A)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // validate conf
                        conf.setConfigSet(true);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // uncheck expert switch
                        conf.setConfigSet(false);
                        expSwitch.performClick();
                    }
                })
                .show();
    }

    /* Print a dialog before exiting application */
    public static void exitDialog(final Activity A, String title, String message) {
        new AlertDialog.Builder(A)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        A.finish();
                    }
                })
                .show();
    }

    /* Print pop-up message with ok and cancel buttons */
    public static void messageExitActivity(final Activity A, String title, String message) {
        new AlertDialog.Builder(A)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        A.finish();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        /* Nothing to do */
                    }
                })
                .show();
    }

    /* Print pop-up message with ok and cancel buttons, dedicated to telephony stack
     * property.
     */
    public static void messageSetupTelStack(final Activity A, String title, String message,
            final TelephonyStack telStackSetter) {
        new AlertDialog.Builder(A)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        /* Enable stack and exit. */
                        telStackSetter.enableStack();
                        exitDialog(A, "REBOOT REQUIRED.",
                            "As you enable telephony stack, AMTL will exit. "
                            + " Please reboot your device.");
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        /* We just exit. */
                        exitDialog(A, "Exit",
                            "AMTL will exit. Telephony stack is disabled.");
                    }
                })
                .show();
    }

}
