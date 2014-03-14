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


public class AMTLApplication extends Application {
    private static Context ctx;
    private static boolean isCloseTtyEnable = true;
    private static boolean isPauseState = true;

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
}
