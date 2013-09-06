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

import android.util.Log;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParserException;

public class PlatformConfig {

    private static final String MODULE = "PlatformConfig";

    private String platformVersion = "";

    private boolean coredumpAvailable = false;
    private boolean offlineUsbAvailable = false;
    private boolean offlineHsiAvailable = false;
    private boolean onlineUsbAvailable = false;
    private boolean onlinePtiAvailable = false;
    private boolean usbswitchAvailable = false;

    private int coredumpXsio = -1;
    private int offlineUsbXsio = -1;
    private int offlineHsiXsio = -1;
    private int onlineUsbXsio = -1;
    private int onlinePtiXsio = -1;

    private String smallLogSizeEmmc = "";
    private String largeLogSizeEmmc = "";
    private String smallLogSizeSdcard = "";
    private String largeLogSizeSdcard = "";

    private String smallLogNumEmmc = "";
    private String largeLogNumEmmc = "";
    private String smallLogNumSdcard = "";
    private String largeLogNumSdcard = "";

    private String inputOfflineUsb = "";
    private String inputOfflineHsi = "";
    private String inputOnlineUsb = "";
    private String inputOnlinePti = "";

    public int[] allowedXsio = new int[5];

    /* PlatformConfig reference: singleton design pattern */
    private static PlatformConfig platformConfig;

    private ConfigParser configParser = null;

    private PlatformConfig() {
        this.configParser = new ConfigParser();
        this.configParser.parseConfig(this);
        this.setAllowedXsio();
    }

    protected static PlatformConfig get() {
        if (platformConfig == null) {
            platformConfig = new PlatformConfig();
            platformConfig.printVersion();
        }
        return platformConfig;
    }

    private void printVersion() {
      Log.d(AmtlCore.TAG, MODULE + "Platform version: " + getPlatformVersion());
    }

    protected void destroy() {
        this.platformConfig = null;
    }

    private void setAllowedXsio () {
        int i = 0;
        if (getCoredumpXsio() != -1) {
            this.allowedXsio[i] = getCoredumpXsio();
            i++;
        }
        if (getOfflineUsbXsio()!= -1) {
            this.allowedXsio[i] = getOfflineUsbXsio();
            i++;
        }
        if (getOfflineHsiXsio()!= -1) {
            this.allowedXsio[i] = getOfflineHsiXsio();
            i++;
        }
        if (getOfflineHsiXsio()!= -1) {
            this.allowedXsio[i] = getOfflineHsiXsio();
            i++;
        }
        if (getOnlineUsbXsio()!= -1) {
            this.allowedXsio[i] = getOnlineUsbXsio();
            i++;
        }
        if (getOnlinePtiXsio()!= -1) {
            this.allowedXsio[i] = getOnlinePtiXsio();
            i++;
        }
    }

    public boolean isXsioAllowed (int xsio) {
        for (int i = 0 ; i < 6 ; i++) {
            if (this.allowedXsio[i] == xsio) {
                return true;
            }
        }
        return false;
    }

    public String getPlatformVersion() {
        return this.platformVersion;
    }

    public boolean getCoredumpAvailable() {
        return this.coredumpAvailable;
    }

    public boolean getOfflineUsbAvailable() {
        return this.offlineUsbAvailable;
    }

    public boolean getOfflineHsiAvailable() {
        return this.offlineHsiAvailable;
    }

    public boolean getOnlineUsbAvailable() {
        return this.onlineUsbAvailable;
    }

    public boolean getOnlinePtiAvailable() {
        return this.onlinePtiAvailable;
    }

    public boolean getUsbswitchAvailable() {
        return this.usbswitchAvailable;
    }

    public int getCoredumpXsio() {
        return this.coredumpXsio;
    }

    public int getOfflineUsbXsio() {
        return this.offlineUsbXsio;
    }

    public int getOfflineHsiXsio() {
        return this.offlineHsiXsio;
    }

    public int getOnlineUsbXsio() {
        return this.onlineUsbXsio;
    }

    public int getOnlinePtiXsio() {
        return this.onlinePtiXsio;
    }

    public String getSmallLogSizeEmmc() {
        return this.smallLogSizeEmmc;
    }

    public String getLargeLogSizeEmmc() {
        return this.largeLogSizeEmmc;
    }

    public String getSmallLogSizeSdcard() {
        return this.smallLogSizeSdcard;
    }

    public String getLargeLogSizeSdcard() {
        return this.largeLogSizeSdcard;
    }

    public String getSmallLogNumEmmc() {
        return this.smallLogNumEmmc;
    }

    public String getLargeLogNumEmmc() {
        return this.largeLogNumEmmc;
    }

    public String getSmallLogNumSdcard() {
        return this.smallLogNumSdcard;
    }

    public String getLargeLogNumSdcard() {
        return this.largeLogNumSdcard;
    }

    public String getInputOfflineUsb() {
        return this.inputOfflineUsb;
    }

    public String getInputOfflineHsi() {
        return this.inputOfflineHsi;
    }

    public String getInputOnlineUsb() {
        return this.inputOnlineUsb;
    }

    public String getInputOnlinePti() {
        return this.inputOnlinePti;
    }

    public void setPlatformVersion(String platformVersion) {
        this.platformVersion = platformVersion;
    }

    public void setCoredumpAvailable(boolean coredumpAvailable) {
        this.coredumpAvailable = coredumpAvailable;
    }

    public void setOfflineUsbAvailable(boolean offlineUsbAvailable) {
        this.offlineUsbAvailable = offlineUsbAvailable;
    }

    public void setOfflineHsiAvailable(boolean offlineHsiAvailable) {
        this.offlineHsiAvailable = offlineHsiAvailable;
    }

    public void setOnlineUsbAvailable(boolean onlineUsbAvailable) {
        this.onlineUsbAvailable = onlineUsbAvailable;
    }

    public void setOnlinePtiAvailable(boolean onlinePtiAvailable) {
        this.onlinePtiAvailable = onlinePtiAvailable;
    }

    public void setUsbswitchAvailable(boolean usbswitchAvailable) {
        this.usbswitchAvailable = usbswitchAvailable;
    }

    public void setCoredumpXsio(int coredumpXsio) {
        this.coredumpXsio = coredumpXsio;
    }

    public void setOfflineUsbXsio(int offlineUsbXsio) {
        this.offlineUsbXsio = offlineUsbXsio;
    }

    public void setOfflineHsiXsio(int offlineHsiXsio) {
        this.offlineHsiXsio = offlineHsiXsio;
    }

    public void setOnlineUsbXsio(int onlineUsbXsio) {
        this.onlineUsbXsio = onlineUsbXsio;
    }

    public void setOnlinePtiXsio(int onlinePtiXsio) {
        this.onlinePtiXsio = onlinePtiXsio;
    }

    public void setSmallLogSizeEmmc(String smallLogSizeEmmc) {
        this.smallLogSizeEmmc = smallLogSizeEmmc;
    }

    public void setLargeLogSizeEmmc(String largeLogSizeEmmc) {
        this.largeLogSizeEmmc = largeLogSizeEmmc;
    }

    public void setSmallLogSizeSdcard(String smallLogSizeSdcard) {
        this.smallLogSizeSdcard = smallLogSizeSdcard;
    }

    public void setLargeLogSizeSdcard(String largeLogSizeSdcard) {
        this.largeLogSizeSdcard = largeLogSizeSdcard;
    }

    public void setSmallLogNumEmmc(String smallLogNumEmmc) {
        this.smallLogNumEmmc = smallLogNumEmmc;
    }

    public void setLargeLogNumEmmc(String largeLogNumEmmc) {
        this.largeLogNumEmmc = largeLogNumEmmc;
    }

    public void setSmallLogNumSdcard(String smallLogNumSdcard) {
        this.smallLogNumSdcard = smallLogNumSdcard;
    }

    public void setLargeLogNumSdcard(String largeLogNumSdcard) {
        this.largeLogNumSdcard = largeLogNumSdcard;
    }

    public void setInputOfflineUsb(String inputOfflineUsb) {
        this.inputOfflineUsb = inputOfflineUsb;
    }

    public void setInputOfflineHsi(String inputOfflineHsi) {
        this.inputOfflineHsi = inputOfflineHsi;
    }

    public void setInputOnlineUsb(String inputOnlineUsb) {
        this.inputOnlineUsb = inputOnlineUsb;
    }

    public void setInputOnlinePti(String inputOnlinePti) {
        this.inputOnlinePti = inputOnlinePti;
    }
}
