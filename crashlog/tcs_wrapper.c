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
#include "crashutils.h"

#include "privconfig.h"
#include "tcs.h"

#include <stdlib.h>

/**
 * get the number of modems reported by tcs
 * @return negative if fails
 */
int get_modem_count() {
    tcs_handle_t *h = NULL;
    tcs_cfg_t *tcs_cfg = NULL;
    int ret = -1;

    h = tcs_init();
    if (!h) {
        LOGE("%s: Could not initialize Telephony Configuration Selector.\n",
             __FUNCTION__);
        return ret;
    }

    tcs_cfg = tcs_get_config(h);
    if (!tcs_cfg) {
        LOGE("%s: Could not retrieve configuration.\n", __FUNCTION__);
        goto out;
    }

    ret = tcs_cfg->nb > MAX_MMGR_INST ? MAX_MMGR_INST : tcs_cfg->nb;

  out:
    tcs_dispose(h);
    return ret;
}

int get_modem_name(char *modem_name, unsigned int modem_id) {
    tcs_handle_t *h = NULL;
    tcs_cfg_t *tcs_cfg = NULL;
    int ret = -1;

    h = tcs_init();
    if (!h) {
        LOGE("%s: Could not initialize Telephony Configuration Selector.\n",
             __FUNCTION__);
        return ret;
    }

    tcs_cfg = tcs_get_config(h);
    if (!tcs_cfg) {
        LOGE("%s: Could not retrieve configuration.\n", __FUNCTION__);
        goto out;
    }

    if (modem_id < tcs_cfg->nb) {
        if (tcs_cfg->mdm) {
            strncpy(modem_name, tcs_cfg->mdm[modem_id].core.name,
                    PROPERTY_VALUE_MAX);
            ret = 0;
        } else {
            LOGE("%s: Wrong TCS value", __FUNCTION__);
        }
    } else if (tcs_cfg->nb == 0) {
        LOGD("%s: no modem", __FUNCTION__);
        modem_name[0] = '\0';
    } else {
        LOGE("%s: Modem index %d out of bounds", __FUNCTION__, modem_id);
    }

  out:
    tcs_dispose(h);
    return ret;
}
