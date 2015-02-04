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
 */

package com.intel.amtl.modem;

import com.intel.amtl.exceptions.ModemControlException;
import com.intel.amtl.models.config.ModemConf;

public class TraceLegacyController extends ModemController {

    public TraceLegacyController() throws ModemControlException {
        super();
    }

    @Override
    public boolean queryTraceState()throws ModemControlException {
        return checkAtTraceState().equals("1");
    }

    @Override
    public String switchOffTrace() throws ModemControlException {
        return sendAtCommand("AT+TRACE=0\r\n");
    }

    @Override
    public void switchTrace(ModemConf mdmConf) throws ModemControlException {
        sendAtCommand(mdmConf.getTrace());
        sendAtCommand(mdmConf.getXsystrace());
    }

    @Override
    public String checkAtTraceState() throws ModemControlException {
        return getCmdParser().parseTraceResponse(sendAtCommand("at+trace?\r\n"));
    }

    @Override
    public ModemConf getNoLoggingConf() {
        ModemConf noLoggingConf = ModemConf.getInstance("", "AT+TRACE=0\r\n", "AT+XSYSTRACE=0\r\n",
                "", "");
        noLoggingConf.setIndex(-1);

        return noLoggingConf;
    }
}