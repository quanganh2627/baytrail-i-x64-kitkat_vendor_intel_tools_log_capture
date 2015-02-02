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
 * Author: Nicolae Natea <nicolaex.natea@intel.com>
 */

package com.intel.amtl.common.modem;

import com.intel.amtl.common.models.config.Master;

import java.util.ArrayList;

public interface CommandParser {
    public String parseXsioResponse(String xsio);
    public String parseMasterResponse(String xsystrace, String master);
    public ArrayList<Master> parseXsystraceResponse(String xsystrace,
            ArrayList<Master> masterArray);
    public String parseTraceResponse(String trace);
    public String parseOct(String xsystrace);
}
