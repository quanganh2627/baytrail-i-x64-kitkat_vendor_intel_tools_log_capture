/* Copyright (C) Intel 2013
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @file tcs_wrapper.c
 * @brief File containing all functions redirecting to calls to libtcs.
 * This file is required to avoid conflicts on typedef made in libtcs.
 * Do not include any other header files here with the exception of
 * those that are required for libtcs and/or configuration as some
 * conflicts may arise.
 *
 */
#include "tcs_wrapper.h"

#include "privconfig.h"
#include "tcs.h"

#include <stdlib.h>

/**
 * @brief Writes the modem version to the given buffer.
 *
 * @return int
 *  - < 0: if an error occured
 *  - >= 0: if the value could be retrieved successfully
 */
int get_modem_name(char* modem_name) {
    tcs_handle_t *h = NULL;
    tcs_cfg_t *tcs_cfg = NULL;

    h = tcs_init();
    if(!h) {
        LOGE("%s: Could not initialize Telephony Configuration Selector.\n", __FUNCTION__);
        return -1;
    }

    tcs_cfg = tcs_get_config(h);
    if(!tcs_cfg) {
        LOGE("%s: Could not retrieve configuration.\n", __FUNCTION__);
        tcs_dispose(h);
        return -1;
    }

    strncpy(modem_name, tcs_cfg->mdm_info.name, PROPERTY_VALUE_MAX);
    tcs_dispose(h);
    return 0;
}
