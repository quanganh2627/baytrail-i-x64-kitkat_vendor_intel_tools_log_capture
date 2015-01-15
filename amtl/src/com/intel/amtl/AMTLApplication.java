/* Android AMTL
 *
 * Copyright (C) Intel 2014
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
 * Author: Damien Charpentier <damienx.charpentier@intel.com>
 */

package com.intel.amtl;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.intel.amtl.models.config.LogOutput;

import java.util.ArrayList;

public class AMTLApplication extends Application {
    private static Context ctx;
    private static boolean isCloseTtyEnable = true;
    private static boolean isPauseState = true;
    private static boolean hasModemChanged = false;
    private static LogOutput defaultConf = null;
    private static String modemInterface;
    private static int modConnectionId;
    private static boolean traceLegacy = false;
    private static String serviceToStart;
    private static ArrayList<String> modemNameList = new ArrayList<String>();

    public static Context getContext() {
        return ctx;
    }

    public static void setContext(Context context) {
        ctx = context;
    }

    public static boolean getCloseTtyEnable() {
        return isCloseTtyEnable;
    }

    public static void setCloseTtyEnable(boolean isEnable) {
        isCloseTtyEnable = isEnable;
    }

    public static boolean getPauseState() {
        return isPauseState;
    }

    public static void setPauseState(boolean bState) {
        isPauseState = bState;
    }

    public static boolean getModemChanged() {
        return hasModemChanged;
    }

    public static void setModemChanged(boolean change) {
        hasModemChanged = change;
    }

    public static LogOutput getDefaultConf() {
        return defaultConf;
    }

    public static void setDefaultConf(LogOutput defConf) {
        defaultConf = defConf;
    }

    public static void setModemConnectionId(String id) {
        modConnectionId = Integer.parseInt(id);
    }

    public static int getModemConnectionId() {
        return modConnectionId;
    }

    public static void setModemInterface(String modInterface) {
        modemInterface = modInterface;
    }

    public static String getModemInterface() {
        return modemInterface;
    }

    public static void setTraceLegacy(String legacy) {
        traceLegacy = (legacy.equals("true")) ? true : false;
    }

    public static boolean getTraceLegacy() {
        return traceLegacy;
    }

    public static void setServiceToStart(String service) {
        serviceToStart = service;
    }

    public static String getServiceToStart() {
        return serviceToStart;
    }

    public static void setModemNameList(ArrayList<String> modemNames) {
        modemNameList = modemNames;
    }

    public static ArrayList<String> getModemNameList() {
        return modemNameList;
    }

}
