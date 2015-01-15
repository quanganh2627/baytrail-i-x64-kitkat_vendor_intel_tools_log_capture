/* Android AMTL
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

package com.intel.amtl.models.config;

import android.app.Activity;
import android.util.Log;

import com.intel.amtl.config_parser.ConfigParser;
import com.intel.amtl.gui.UIHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.xmlpull.v1.XmlPullParserException;

public class ExpertConfig {

    private final String TAG = "AMTL";
    private final String MODULE = "ExpertConfig";

    private String confPath = "/etc/telephony/amtl_default_expert.cfg";
    private boolean configSet = false;
    private Activity activity = null;
    private File chosenFile = null;
    private ConfigParser modemConfParser = null;
    private ModemConf selectedConf = null;

    public ExpertConfig() {
        this.modemConfParser = new ConfigParser();
    }

    public ExpertConfig(Activity activity) {
        this.modemConfParser = new ConfigParser();
        this.activity = activity;
    }

    public String getPath() {
        return this.confPath;
    }

    public void setPath(String path) {
        this.confPath = path;
    }

    public boolean isConfigSet() {
        return this.configSet;
    }

    public void setConfigSet(boolean confSet) {
        this.configSet = confSet;
    }

    public ModemConf getExpertConf() {
        return this.selectedConf;
    }

    public void setExpertConf(ModemConf conf) {
        this.selectedConf = conf;
    }

    public String displayConf() {
        String message = "";
        if (this.selectedConf != null) {
            message = selectedConf.getXsio();
            message += "\r\n";
            message += selectedConf.getXsystrace();
            message += "\r\n";
            message += selectedConf.getTrace();
        }
        return message;
    }

    public void validateFile() {
        this.chosenFile = this.checkConfPath(this.confPath);
        if (this.chosenFile != null && !this.chosenFile.isDirectory()) {
            this.selectedConf = this.applyConf(this.chosenFile);
        }
    }

    private File checkConfPath(String path) {
        File ret = null;
        File file = new File(path);
        if (file.exists()) {
            ret = file;
            Log.d(TAG, MODULE + ": file: " + path + " has been chosen");
        } else {
            if (this.activity != null) {
                UIHelper.okDialog(activity, "Error", "file: " + path + " has not been found");
            }
            Log.e(TAG, MODULE + ": file: " + path + " has not been found");
        }
        return ret;
    }

    private ModemConf applyConf(File conf) {
        ModemConf ret = null;
        FileInputStream fin = null;
        if (conf != null) {
            try {
                fin = new FileInputStream(conf);
                if (fin != null) {
                    ret = modemConfParser.parseShortConfig(fin);
                    this.setConfigSet(true);
                }
            } catch (FileNotFoundException ex) {
                Log.e(TAG, MODULE + ": file " + conf + " has not been found");
                if (this.activity != null) {
                    UIHelper.okDialog(this.activity, "Error", ex.toString());
                }
            } catch (XmlPullParserException ex) {
                Log.e(TAG, MODULE + ": an issue occured during parsing " + ex);
                if (this.activity != null) {
                    UIHelper.okDialog(this.activity, "Error", ex.toString());
                }
            } catch (IOException ex) {
                Log.e(TAG, MODULE + ": an issue occured during parsing " + ex);
                if (this.activity != null) {
                    UIHelper.okDialog(this.activity, "Error", ex.toString());
                }
            } finally {
                if (fin != null) {
                    try {
                        fin.close();
                    } catch (IOException ex) {
                        Log.e(TAG, MODULE + ": Error during close " + ex);
                    }
                }
            }
        }
        return ret;
    }
}
