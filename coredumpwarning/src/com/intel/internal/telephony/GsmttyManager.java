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

package com.intel.internal.telephony.TelephonyEventsNotifier;

import com.intel.internal.telephony.TelephonyEventsNotifier.exceptions.ModemControlException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;

import android.util.Log;

public class GsmttyManager implements Closeable {

    private final String TAG = "TelephonyEventsNotifier";
    private final String MODULE = "GsmttyManager";
    private RandomAccessFile file = null;
    private Gsmtty gsmtty = null;

    public GsmttyManager() throws ModemControlException {

        try {
            this.gsmtty = new Gsmtty(this.getTtyFileName(), 115200);
            this.gsmtty.openTty();
            this.file = new RandomAccessFile(this.getTtyFileName(), "rw");
        } catch (ExceptionInInitializerError ex) {
            throw new ModemControlException("libten_jni library was not found.");
        } catch (IOException ex) {
            throw new ModemControlException(String.format("Error while opening gsmtty"));
        } catch (IllegalArgumentException ex) {
            throw new ModemControlException(String.format("Error while opening gsmtty"));
        }
    }

    private String getTtyFileName() {
        return "/dev/mvpipe-at3";
    }

    public String writeToModemControl(String atCommand) throws ModemControlException {

        String atResponse = "";
        byte[] responseBuffer = new byte[10240];

        try {
            this.file.writeBytes(atCommand);
            Log.i(TAG, MODULE + ": sending to modem " + atCommand);
        }
        catch (IOException ex) {
            throw new ModemControlException("Unable to send to AT command to the modem.");
        }

        try {
            int readCount = this.file.read(responseBuffer);
			
			Log.i(TAG, MODULE + " : read count " + readCount);
            if (readCount >= 0) {
                atResponse = new String(responseBuffer, 0, readCount);
                Log.i(TAG, MODULE + " : response from modem " + atResponse);
				if (readCount >= 100) {
					writeFileSdcard("/sdcard/logs/coredump.txt", atResponse);
					atResponse = "response is too big, pls see /sdcard/logs/coredump.txt";
				}
			} else {
                throw new ModemControlException("Unable to read response from the modem.");
            }
        }
        catch (IOException ex) {
            throw new ModemControlException("Unable to read response from the modem.");
        }
        return atResponse;
    }

    public void close() {
        if (this.file != null) {
            try {
                this.file.close();
                this.file = null;
            }
            catch (IOException ex) {
                Log.e(TAG, MODULE + ex.toString());
            } finally {
                if (this.gsmtty != null) {
                    this.gsmtty.closeTty();
                    this.gsmtty = null;
                }
            }
        }
    }

	public void writeFileSdcard(String fileName,String message){ 
       try { 
	       FileOutputStream fout = new FileOutputStream(fileName);
	       byte [] bytes = message.getBytes(); 
	       fout.write(bytes); 
	       fout.close(); 
	   } 
       catch(IOException e){ 
		   e.printStackTrace(); 
       } 
}

}
