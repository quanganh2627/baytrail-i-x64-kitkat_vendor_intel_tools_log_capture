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

package com.intel.amtl.models.config;

public class OctModemConf extends ModemConf {

    public OctModemConf(LogOutput config) {
        super(config);
    }

    public OctModemConf(String xsio, String trace, String xsystrace, String flcmd, String octMode) {
        super(xsio, trace, xsystrace, flcmd, octMode);
    }

    @Override
    public boolean confTraceEnabled() {
        return !getOctMode().equals("0");
    }

    @Override
    public void activateConf(boolean activate) {}
}
