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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.text.Html;
import android.util.Log;
/**
 * This class catch broadcasted events and display notifications or alarm dialog
 * This class extends the BroadcastReceiver class
 * @see BroadcastReceiver
 */
public class EventReceiver extends BroadcastReceiver {

    private final String TAG = "TelephonyEventsNotifier";

    /*modem event */
    public static final String CUSTOM_INTENT = "com.msm.android.MSMClientLib.modemState";

    private static final String CORE_DUMP_START_INTENT = "com.intel.action.CORE_DUMP_WARNING";
    private static final String CORE_DUMP_STOP_INTENT = "com.intel.action.CORE_DUMP_COMPLETE";
    private static final String MODEM_OUT_INTENT = "com.intel.action.MODEM_OUT_OF_SERVICE";
    private static final String WARM_INTENT = "com.intel.action.MODEM_WARM_RESET";
    private static final String COLD_INTENT = "com.intel.action.MODEM_COLD_RESET";
    private static final String REBOOT_INTENT = "com.intel.action.PLATFORM_REBOOT";
    private static final String MODEM_UNSOLICITED_RESET_INTENT =
            "com.intel.action.MODEM_UNSOLICITED_RESET";
    private static final String MODEM_NOT_RESPONSIVE_INTENT =
            "com.intel.action.MODEM_NOT_RESPONSIVE";
    private static final String MODEM_FW_BAD_FAMILY_INTENT =
            "com.intel.action.MODEM_FW_BAD_FAMILY";
    private static final String MODEM_FW_OUTDATED_INTENT =
            "com.intel.action.MODEM_FW_OUTDATED";
    private static final String MODEM_FW_CORRUPTED_INTENT =
            "com.intel.action.MODEM_FW_SECURITY_CORRUPTED";
    private static final String MODEM_FW_RESTRICTED_INTENT =
            "com.intel.action.MODEM_FW_RESTRICTED";
    private static final String MODEM_FW_UPDATE_FAILURE_INTENT =
            "com.intel.action.MODEM_FW_UPDATE_FAILURE";

    private Boolean mIsPopupEnabled;
    private Boolean mIsNotificationEnabled;
    private Boolean mIsNotificationSoundEnabled;
    private Boolean mIsNotificationVibrateEnabled;
    private Boolean mIsCoreDumpStartEnabled;
    private Boolean mIsCoreDumpStopEnabled;
    private Boolean mIsModemWarmResetEnabled;
    private Boolean mIsModemColdResetEnabled;
    private Boolean mIsModemRebootEnabled;
    private Boolean mIsModemOutEnabled;
    private Boolean mIsModemUnsolicitedReset;
    private Boolean mIsModemNotResponsive;
    private Boolean mIsModemFwBadFamily;
    private Boolean mIsModemFwOutdated;
    private Boolean mIsModemFwSecurityCorrupted;
    private Boolean mIsModemFwRestricted;
    private Boolean mIsModemFwUpdateFailed;

    public enum MessageId {
        NONE(0),
        CORE_DUMP_START(1),
        CORE_DUMP_STOP(2),
        MODEM_OUT(3),
        WARM_RESET(4),
        COLD_RESET(5),
        REBOOT(6),
        MODEM_UNSOLICITED(7),
        MODEM_NOT_RESPONSIVE(8),
        MODEM_FW_BAD_FAMILY(9),
        MODEM_FW_OUTDATED(10),
        MODEM_FW_SECURITY_CORRUPTED(11),
        MODEM_FW_RESTRICTED(12),
        MODEM_FW_UPDATE_FAILED(13);


        final private int value;

        private MessageId(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }

    /**
     * Get application preferences
     *
     * @param context
     *
     * @return void
     */
    private void loadPreferences(Context context) {
        PreferenceReader reader = new PreferenceReader(context);
        reader.updateDefaultValues();

        mIsPopupEnabled = reader.isPopupEnabled();
        mIsNotificationEnabled = reader.isNotificationEnabled();
        mIsNotificationSoundEnabled = reader.isNotificationSoundEnabled();
        mIsNotificationVibrateEnabled = reader.isNotificationVibrateEnabled();
        mIsCoreDumpStartEnabled = reader.isCoreDumpStartEnabled();
        mIsCoreDumpStopEnabled = reader.isCoreDumpCompleteEnabled();
        mIsModemWarmResetEnabled = reader.isModemWarmResetEnabled();
        mIsModemColdResetEnabled = reader.isModemColdResetEnabled();
        mIsModemRebootEnabled = reader.isModemRebootEnabled();
        mIsModemOutEnabled = reader.isModemOutOfServiceEnabled();
        mIsModemUnsolicitedReset = reader.isModemUnsolicitedResetEnabled();
        mIsModemNotResponsive = reader.isModemNotResponsiveEnabled();
        mIsModemFwBadFamily = reader.isModemFwBadFamilyEnabled();
        mIsModemFwSecurityCorrupted = reader.isModemFwSecurityCorruptedEnabled();
        mIsModemFwOutdated = reader.isModemFwOutdatedEnabled();
        mIsModemFwRestricted = reader.isModemFwRestrictedEnabled();
        mIsModemFwUpdateFailed = reader.isModemFwUpdateFailed();
    }

    /**
     * add the notification to Android NotificationManager
     *
     * @param context
     * @param notificationIntent,
     * @param notifyMsg
     * @param id
     *
     * @return void
     */
     private void addNotify(Context context, Intent notificationIntent,
            String notifyMsg, int id) {
        Notification.Builder builder = new Notification.Builder(context);
        builder.setContentTitle(context.getResources().getString(R.string.app_name))
            .setContentText(notifyMsg)
            .setSmallIcon(R.drawable.intel_notification)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, id, notificationIntent,
                PendingIntent.FLAG_ONE_SHOT));
        if (mIsNotificationSoundEnabled)
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        if (mIsNotificationVibrateEnabled)
            builder.setVibrate(new long[] {0,200,100,200,100,200});

        NotificationManager nm = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(id, builder.build());
    }

    /**
     * Onverride of onReceive function
     * This function loads app preferences and display the alert dialog or/and
     * add notification according to app preferences.
     *
     * @param context
     * @param intent
     *
     * @return void
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String notifyMsg = "";
        String dialogMsg = "";
        MessageId id = MessageId.NONE;
        loadPreferences(context);
	if (intent.getAction().equals(CUSTOM_INTENT) && mIsCoreDumpStartEnabled) {
	    Log.d("chetan", "onReceive\r\n");
	    String modemState;
	    modemState = intent.getStringExtra("modemState");
	    if (modemState.equals("MODEM_DOWN")){
                Log.d("chetan", "MODEM_DOWN\r\n");
		id = MessageId.CORE_DUMP_START;
            	notifyMsg = context.getResources().getString(R.string.notify_core_dump_start);
            	dialogMsg = context.getResources().getString(R.string.dialog_core_dump_start);
	    } 
	    else if (modemState.equals("MODEM_UP")){
		Log.d("chetan", "MODEM_UP\r\n");
		id = MessageId.CORE_DUMP_STOP;
                notifyMsg = context.getResources().getString(R.string.notify_core_dump_stop);
                dialogMsg = context.getResources().getString(R.string.dialog_core_dump_stop);
	    }
	    else {
		Log.d("chetan", "UNKNOWN\r\n");
	    }
	}
/*	
        if (mIsCoreDumpStartEnabled && CORE_DUMP_START_INTENT.equals(intent.getAction())) {
            id = MessageId.CORE_DUMP_START;
            notifyMsg = context.getResources().getString(R.string.notify_core_dump_start);
            dialogMsg = context.getResources().getString(R.string.dialog_core_dump_start);
        } else if (mIsCoreDumpStopEnabled && CORE_DUMP_STOP_INTENT.equals(intent.getAction())) {
            id = MessageId.CORE_DUMP_STOP;
            notifyMsg = context.getResources().getString(R.string.notify_core_dump_stop);
            dialogMsg = context.getResources().getString(R.string.dialog_core_dump_stop);
        } else if (mIsModemOutEnabled && MODEM_OUT_INTENT.equals(intent.getAction())) {
            id = MessageId.MODEM_OUT;
            notifyMsg = context.getResources().getString(R.string.notify_modem_out);
            dialogMsg = context.getResources().getString(R.string.dialog_modem_out);
        } else if (mIsModemRebootEnabled && REBOOT_INTENT.equals(intent.getAction())) {
            id = MessageId.REBOOT;
            notifyMsg = context.getResources().getString(R.string.notify_modem_reboot);
            dialogMsg = context.getResources().getString(R.string.dialog_modem_reboot);
        } else if (mIsModemWarmResetEnabled && WARM_INTENT.equals(intent.getAction())) {
            id = MessageId.WARM_RESET;
            notifyMsg = context.getResources().getString(R.string.notify_warm_reset);
            dialogMsg = context.getResources().getString(R.string.dialog_warm_reset);
        } else if (mIsModemColdResetEnabled && COLD_INTENT.equals(intent.getAction())) {
            id = MessageId.COLD_RESET;
            notifyMsg = context.getResources().getString(R.string.notify_cold_reset);
            dialogMsg = context.getResources().getString(R.string.dialog_cold_reset);
        } else if (mIsModemUnsolicitedReset &&
                    MODEM_UNSOLICITED_RESET_INTENT.equals(intent.getAction())) {
            id = MessageId.MODEM_UNSOLICITED;
            notifyMsg = context.getResources().getString(R.string.notify_unsolicited_reset);
            dialogMsg = context.getResources().getString(R.string.dialog_unsolicited_reset);
        } else if (mIsModemNotResponsive &&
                    MODEM_NOT_RESPONSIVE_INTENT.equals(intent.getAction())) {
            id = MessageId.MODEM_NOT_RESPONSIVE;
            notifyMsg = context.getResources().getString(R.string.notify_not_responsive);
            dialogMsg = context.getResources().getString(R.string.dialog_not_responsive);
        } else if (mIsModemFwBadFamily &&
                    MODEM_FW_BAD_FAMILY_INTENT.equals(intent.getAction())) {
            id = MessageId.MODEM_FW_BAD_FAMILY;
            notifyMsg = context.getResources().getString(R.string.notify_bad_fw_family);
            dialogMsg = context.getResources().getString(R.string.dialog_bad_fw_family);
        } else if (mIsModemFwOutdated &&
                    MODEM_FW_OUTDATED_INTENT.equals(intent.getAction())) {
            id = MessageId.MODEM_FW_OUTDATED;
            notifyMsg = context.getResources().getString(R.string.notify_fw_outdated);
            dialogMsg = context.getResources().getString(R.string.dialog_fw_outdated);
        } else if (mIsModemFwSecurityCorrupted &&
                    MODEM_FW_CORRUPTED_INTENT.equals(intent.getAction())) {
            id = MessageId.MODEM_FW_SECURITY_CORRUPTED;
            notifyMsg =
                    context.getResources().getString(R.string.notify_fw_security_corrupted);
            dialogMsg =
                    context.getResources().getString(R.string.dialog_fw_security_corrupted);
        } else if (mIsModemFwRestricted &&
                    MODEM_FW_RESTRICTED_INTENT.equals(intent.getAction())) {
            id = MessageId.MODEM_FW_RESTRICTED;
            notifyMsg = context.getResources().getString(R.string.notify_fw_restricted);
            dialogMsg = context.getResources().getString(R.string.dialog_fw_restricted);
        } else if (mIsModemFwUpdateFailed &&
                    MODEM_FW_UPDATE_FAILURE_INTENT.equals(intent.getAction())) {
            id = MessageId.MODEM_FW_UPDATE_FAILED;
            notifyMsg = context.getResources().getString(R.string.notify_fw_update_failure);
            dialogMsg = context.getResources().getString(R.string.dialog_fw_update_failure);
        }
*/
        if (id != MessageId.NONE) {
            CharSequence dialogMsgHtml = Html.fromHtml(dialogMsg);
            Intent notificationIntent = new Intent(context, AlertDialogActivity.class);
            notificationIntent.putExtra("dialogMsg", dialogMsgHtml);
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            if (mIsPopupEnabled) {
                context.startActivity(notificationIntent);
            }
            if (mIsNotificationEnabled) {
                addNotify(context, notificationIntent, notifyMsg, id.getValue());
            }
        }
    }
}

