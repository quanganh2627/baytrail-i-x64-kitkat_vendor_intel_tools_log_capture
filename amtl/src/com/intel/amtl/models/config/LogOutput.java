/* Android AMTL
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
 * Author: Erwan Bracq <erwan.bracq@intel.com>
 * Author: Morgane Butscher <morganex.butscher@intel.com>
 */

package com.intel.amtl.models.config;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.Switch;

import java.util.Hashtable;
import java.util.Collection;
import java.util.Iterator;


public class LogOutput {

    private  final String TAG = "AMTL";
    private  final String MODULE = "LogOutput";

    private String name = null;
    private String xsioValue = null;
    private String buttonColor = null;
    private String mtsInput = null;
    private String mtsOutput = null;
    private String mtsOutputType = null;
    private String mtsRotateSize = null;
    private String mtsRotateNum = null;
    private String mtsMode = null;
    private String oct = null;
    private String pti1 = null;
    private String pti2 = null;
    private Hashtable<String, Master> masterList = null;
    private Switch confSwitch = null;

    public LogOutput() {
        this.masterList = new Hashtable<String, Master>();
    }

    public LogOutput(String name, String xsioValue, String buttonColor, String mtsInput,
            String mtsOutput, String mtsOutputType, String mtsRotateNum, String mtsRotateSize,
            String mtsMode, String oct, String pti1, String pti2) {
        this.setName(name);
        this.setXsio(xsioValue);
        this.setButtonColor(buttonColor);
        this.setMtsInput(mtsInput);
        this.setMtsOutput(mtsOutput);
        this.setMtsRotateNum(mtsRotateNum);
        this.setMtsOutputType(mtsOutputType);
        this.setMtsRotateSize(mtsRotateSize);
        this.setMtsMode(mtsMode);
        this.masterList = new Hashtable<String, Master>();
        this.setOct(oct);
        this.setPti1(pti1);
        this.setPti2(pti2);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setXsio(String xsioValue) {
        this.xsioValue = xsioValue;
    }

    public void setButtonColor(String buttonColor) {
        this.buttonColor = buttonColor;
    }

    public void setMtsInput(String mtsInput) {
        this.mtsInput = mtsInput;
    }

    public void setMtsOutput(String mtsOutput) {
        this.mtsOutput = mtsOutput;
    }

    public void setMtsOutputType(String mtsOutputType) {
        this.mtsOutputType = mtsOutputType;
    }

    public void setMtsRotateSize(String mtsRotateSize) {
        this.mtsRotateSize = mtsRotateSize;
    }

    public void setMtsRotateNum(String mtsRotateNum) {
        this.mtsRotateNum = mtsRotateNum;
    }

    public void setMtsMode(String mtsMode) {
        this.mtsMode = mtsMode;
    }

    public void setOct(String oct) {
        this.oct = oct;
    }

    public void setPti1(String pti1) {
        this.pti1 = pti1;
    }

    public void setPti2(String pti2) {
        this.pti2 = pti2;
    }

    public String getName() {
        return this.name;
    }

    public String getXsio() {
        return this.xsioValue;
    }

    public String getButtonColor() {
        return this.buttonColor;
    }

    public String getMtsInput() {
        return this.mtsInput;
    }

    public String getMtsOutput() {
        return this.mtsOutput;
    }

    public String getMtsOutputType() {
        return this.mtsOutputType;
    }

    public String getMtsRotateSize() {
        return this.mtsRotateSize;
    }

    public String getMtsRotateNum() {
        return this.mtsRotateNum;
    }

    public String getMtsMode() {
        return this.mtsMode;
    }

    public String getOct() {
        return this.oct;
    }

    public String getPti1() {
        return this.pti1;
    }

    public String getPti2() {
        return this.pti2;
    }

    public Hashtable<String, Master> getMasterList() {
        return this.masterList;
    }

    public void setMasterList(Hashtable<String, Master> masterList) {
        this.masterList = masterList;
    }

    public Master getMasterFromList(String key) {
        return (Master)this.masterList.get(key);
    }

    public void addMasterToList(String key, Master master) {
        this.masterList.put(key, master);
    }

    public String concatMasterPort() {
        String ret = "";

        Collection<Master> c = this.masterList.values();
        Iterator<Master> it = c.iterator();

        ret += "\"";
        while (it.hasNext()) {
            Master master = it.next();
            ret += master.getName();
            ret += "=";
            ret += master.getDefaultPort();

            if (it.hasNext()) {
                ret += ";";
            }
        }
        if (ret.endsWith(";")) {
            ret = ret.substring(0, ret.length() - 1);
        }

        ret += "\"";
        if (ret.equals("\"\"")) {
            return "";
        }
        return ret;
    }

    public String concatMasterOption() {
        String ret = "";

        Collection<Master> c = this.masterList.values();
        Iterator<Master> it = c.iterator();

        ret += "\"";
        while (it.hasNext()) {
            Master master = it.next();
            if (master.getDefaultConf() != null
                  && !master.getDefaultConf().equals("")) {
                ret += master.getName();
                ret += "=";
                ret += master.getDefaultConf();
                if (it.hasNext()) {
                    ret += ";";
                }
            }
        }
        if (ret.endsWith(";")) {
            ret = ret.substring(0, ret.length() - 1);
        }

        ret += "\"";
        if (ret.equals("\"\"")) {
            return "";
        }

        return ret;
        }

    public void removeMasterFromList(String key) {
        this.masterList.remove(key);
    }

    public Switch setConfigSwitch(LinearLayout ll, int index, Context ctx, View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
             LinearLayout.LayoutParams.MATCH_PARENT,
             LinearLayout.LayoutParams.MATCH_PARENT);
        params.setMargins(0, 40, 0, 0);
        confSwitch = new Switch(ctx);
        confSwitch.setId(index);
        int id = confSwitch.getId();
        confSwitch.setText(this.getName());
        confSwitch.setTextOff("OFF");
        confSwitch.setTextOn("ON");
        confSwitch.setEnabled(false);
        confSwitch.setTextColor(Color.parseColor(this.buttonColor));
        confSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        ll.addView(confSwitch, params);
        Switch configSwitch = ((Switch) view.findViewById(id));
        return configSwitch;
    }

    public int getSwitchId() {
        return this.confSwitch.getId();
    }

    public Switch getConfSwitch() {
        return this.confSwitch;
    }

    public void printToLog() {
        Collection<Master> c = this.masterList.values();
        Iterator<Master> it = c.iterator();
        Log.d(TAG, MODULE + ": name = " + this.name + ", value = " + this.xsioValue
                + ", color = " + this.buttonColor + ", mts_input = " + this.mtsInput
                + ", mts_output = " + this.mtsOutput
                + ", mts_output_type = " + this.mtsOutputType
                + ", mts_rotate_num = " + this.mtsRotateNum
                + ", mts_rotate_size = " + this.mtsRotateSize
                + ", mts_mode = " + this.mtsMode
                + ", oct = " + this.oct + ", pti1 = " + this.pti1 + ", pti2 = " + this.pti2 + ".");
        while(it.hasNext()) {
            Master master = it.next();
            master.printToLog();
        }
    }
}