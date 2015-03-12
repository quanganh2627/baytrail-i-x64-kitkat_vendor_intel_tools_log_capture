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

import com.intel.amtl.common.AMTLApplication;
import com.intel.amtl.common.exceptions.ModemControlException;
import com.intel.amtl.common.log.AlogMarker;
import com.intel.amtl.common.modem.ModemInterfaceMgr;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;

public class SofiaModemInterfaceMgr implements ModemInterfaceMgr, Closeable {

    private final String TAG = "AMTL";
    private final String MODULE = "SofiaModemInterfaceMgr";
    private RandomAccessFile file = null;

    public SofiaModemInterfaceMgr() throws ModemControlException {
        AlogMarker.tAB("SofiaModemInterfaceMgr.SofiaModemInterfaceMgr", "0");

        try {
            this.file = new RandomAccessFile(this.getModemInterface(), "rw");
        } catch (ExceptionInInitializerError ex) {
            throw new ModemControlException("libamtl_jni library was not found " + ex);
        } catch (IOException ex) {
            throw new ModemControlException("Error while opening modem interface " + ex);
        } catch (IllegalArgumentException ex) {
            throw new ModemControlException("Error while opening modem interface" + ex);
        }
        AlogMarker.tAE("SofiaModemInterfaceMgr.SofiaModemInterfaceMgr", "0");
    }

    public String getModemInterface() {
        return AMTLApplication.getModemInterface();
    }

    public void writeToModemControl(String command) throws ModemControlException {
        AlogMarker.tAB("SofiaModemInterfaceMgr.writeToModemControl", "0");

        try {
            this.file.writeBytes(command);
            Log.i(TAG, MODULE + ": sending to modem " + command);
        } catch (IOException ex) {
            throw new ModemControlException("Unable to send to command to the modem. " + ex);
        }
        AlogMarker.tAE("SofiaModemInterfaceMgr.writeToModemControl", "0");
    }

    public String readFromModemControl() throws ModemControlException {
        AlogMarker.tAB("SofiaModemInterfaceMgr.readFromModemControl", "0");

        String response = "";
        byte[] responseBuffer = new byte[1024];

        try {
            int readCount = this.file.read(responseBuffer);
            if (readCount >= 0) {
                response = new String(responseBuffer, 0, readCount);
                Log.i(TAG, MODULE + " : response from modem " + response);
            } else {
                throw new ModemControlException("Unable to read response from the modem.");
            }
        } catch (IOException ex) {
            throw new ModemControlException("Unable to read response from the modem.");
        }
        AlogMarker.tAE("SofiaModemInterfaceMgr.readFromModemControl", "0");
        return response;
    }

    public void close() {
        AlogMarker.tAB("SofiaModemInterfaceMgr.close", "0");
        if (this.file != null) {
            try {
                this.file.close();
                this.file = null;
            } catch (IOException ex) {
                Log.e(TAG, MODULE + ex.toString());
            }
        }
        AlogMarker.tAE("SofiaModemInterfaceMgr.close", "0");
    }

    public void closeModemInterface() {
        AlogMarker.tAB("SofiaModemInterfaceMgr.closeModemInterface", "0");
        this.close();
        AlogMarker.tAE("SofiaModemInterfaceMgr.closeModemInterface", "0");
    }
}
