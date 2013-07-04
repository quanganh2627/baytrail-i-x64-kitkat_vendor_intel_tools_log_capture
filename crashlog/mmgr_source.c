/* * Copyright (C) Intel 2010
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

#include "mmgr_source.h"
#define LOG_TAG "MMGRSOURCE"
#include <cutils/log.h>
#define MMGRMAX 512

mmgr_cli_handle_t *mmgr_hdl = NULL;
static int mmgr_monitor_fd[2];

static void mdm_sendack(e_mmgr_requests_t id_request)
{
    if (mmgr_hdl) {
        mmgr_cli_requests_t request;
        request.id = id_request;
        if (mmgr_cli_send_msg(mmgr_hdl, &request) != E_ERR_CLI_SUCCEED) {
            LOGE("Could not send ACK for event %d to MMGR", id_request);
        }
    } else {
        LOGE("No MMGR handle to send ACK for event %d to MMGR", id_request);
    }
}

int mdm_SHUTDOWN(mmgr_cli_event_t *ev)
{
    LOGD("Received E_MMGR_NOTIFY_MODEM_SHUTDOWN");
    mdm_sendack(E_MMGR_ACK_MODEM_SHUTDOWN);
    struct mmgr_data cur_data;
    strncpy(cur_data.string,"MMODEMOFF\0",sizeof(cur_data.string));
    write(mmgr_monitor_fd[1], &cur_data, sizeof( struct mmgr_data));
    return 0;
}

int mdm_REBOOT(mmgr_cli_event_t *ev)
{
    LOGD("Received E_MMGR_NOTIFY_PLATFORM_REBOOT");
    struct mmgr_data cur_data;
    strncpy(cur_data.string,"MSHUTDOWN\0",sizeof(cur_data.string));
    write(mmgr_monitor_fd[1], &cur_data, sizeof( struct mmgr_data));
    return 0;
}


int mdm_OUT_OF_SERVICE(mmgr_cli_event_t *ev)
{
    LOGD("Received E_MMGR_EVENT_MODEM_OUT_OF_SERVICE");
    struct mmgr_data cur_data;
    strncpy(cur_data.string,"MOUTOFSERVICE\0",sizeof(cur_data.string));
    write(mmgr_monitor_fd[1], &cur_data, sizeof( struct mmgr_data));
    return 0;
}

int mdm_MRESET(mmgr_cli_event_t *ev)
{
    LOGD("Received E_MMGR_NOTIFY_SELF_RESET");
    struct mmgr_data cur_data;
    strncpy(cur_data.string,"MRESET\0",sizeof(cur_data.string));
    write(mmgr_monitor_fd[1], &cur_data, sizeof( struct mmgr_data));
    return 0;
}

int mdm_CORE_DUMP(mmgr_cli_event_t *ev)
{
    LOGD("Received E_MMGR_NOTIFY_CORE_DUMP_COMPLETE");
    struct mmgr_data cur_data;
    strncpy(cur_data.string,"MPANIC\0",sizeof(cur_data.string));
    int copy_cd = 0;

    mmgr_cli_core_dump_t *cd = NULL;
    cd = (mmgr_cli_core_dump_t *)ev->data;
    if (cd == NULL) {
        LOGE("mdm_CORE_DUMP : empty data");
        return -1;
    }

    switch (cd->state) {
    //ENUM WILL be UPDATED
    case E_CD_FAILED:
        LOGW("core dump not retrived");
        // use -1 value to indicate a CD failed
        cur_data.extra_int = -1;
        break;
    case E_CD_FAILED_WITH_PANIC_ID:
        LOGW("FAILED_WITH_PANIC_ID");
        LOGD("panic id: %d", cd->panic_id);
        cur_data.extra_int = cd->panic_id;
        break;
    case E_CD_SUCCEED_WITHOUT_PANIC_ID:
        LOGD("No panic id");
        // use -2 value to indicate an empty panic ID to crashlogd
        cur_data.extra_int = -2;
        copy_cd = 1;
        break;
    case E_CD_SUCCEED:
        LOGD("panic id: %d", cd->panic_id);
        cur_data.extra_int = cd->panic_id;
        copy_cd = 1;
        break;
    default:
        LOGE("Unknown core dump state");
        // use -3 value to indicate an unknown state to crashlogd
         cur_data.extra_int = -3;
        break;
    }
    if ((cd->len < MMGRMAXDATA) && (copy_cd == 1) ) {
        strncpy(cur_data.extra_string,cd->path,cd->len);
        cur_data.extra_string[cd->len] = '\0';
        LOGD("core dump path: %s", cur_data.extra_string);
    }else{
        LOGE("mdm_CORE_DUMP length error : %d", cd->len);
        cur_data.extra_string[0] = '\0';
    }

    write(mmgr_monitor_fd[1], &cur_data, sizeof( struct mmgr_data));
    return 0;
}

int mdm_AP_RESET(mmgr_cli_event_t *ev)
{
    LOGD("Received E_MMGR_NOTIFY_AP_RESET");
    struct mmgr_data cur_data;
    mmgr_cli_ap_reset_t *ap = NULL;
    strncpy(cur_data.string,"APIMR\0",sizeof(cur_data.string));
    ap = (mmgr_cli_ap_reset_t *)ev->data;
    if (ap == NULL) {
        LOGE("mdm_AP_RESET : empty data");
        cur_data.extra_string[0] = '\0';
    }else{
        LOGD("AP reset asked by: %s (len: %d)", ap->name, ap->len);
        if (ap->len < MMGRMAXDATA){
            strncpy(cur_data.extra_string,ap->name,ap->len);
            cur_data.extra_string[ap->len] = '\0';
        }else{
            LOGE("mdm_AP_RESET length error : %d", ap->len);
            snprintf(cur_data.extra_string,sizeof(cur_data.extra_string),"length error %d", ap->len);
        }
    }
    write(mmgr_monitor_fd[1], &cur_data, sizeof( struct mmgr_data));
    return 0;
}

int mdm_TEL_ERROR(mmgr_cli_event_t *ev)
{
    LOGD("Received E_MMGR_NOTIFY_ERROR");
    struct mmgr_data cur_data;
    strncpy(cur_data.string,"TELEPHONY\0",sizeof(cur_data.string));
    mmgr_cli_error_t * err = (mmgr_cli_error_t *)ev->data;
    if (err == NULL) {
        LOGE(" mmgr_cli_error_t empty data");
        strcpy(cur_data.extra_string,"\0");
    }else{
        cur_data.extra_int = err->id;
        if (err->len < MMGRMAXDATA){
            strncpy(cur_data.extra_string,err->reason,err->len);
            cur_data.extra_string[err->len] = '\0';
            LOGD("error {id:%d reason:\"%s\" len:%d}", err->id, cur_data.extra_string, err->len);
        }else{
            LOGE("mdm_TEL_ERROR length error : %d", err->len);
            snprintf(cur_data.extra_string,sizeof(cur_data.extra_string),"length error %d", err->len);
        }
    }
    write(mmgr_monitor_fd[1],  &cur_data, sizeof( struct mmgr_data));
    return 0;
}


int mmgr_get_fd()
{
    return mmgr_monitor_fd[0];
}

void init_mmgr_cli_source(void){
    LOGD("init_mmgr_cli_source");
    if (mmgr_hdl){
        close_mmgr_cli_source();
    }
    mmgr_hdl = NULL;
    mmgr_cli_create_handle(&mmgr_hdl, "crashlogd", NULL);
    mmgr_cli_subscribe_event(mmgr_hdl, mdm_SHUTDOWN, E_MMGR_NOTIFY_MODEM_SHUTDOWN);
    mmgr_cli_subscribe_event(mmgr_hdl, mdm_REBOOT, E_MMGR_NOTIFY_PLATFORM_REBOOT);
    mmgr_cli_subscribe_event(mmgr_hdl, mdm_OUT_OF_SERVICE, E_MMGR_EVENT_MODEM_OUT_OF_SERVICE);
    mmgr_cli_subscribe_event(mmgr_hdl, mdm_MRESET, E_MMGR_NOTIFY_SELF_RESET);
    mmgr_cli_subscribe_event(mmgr_hdl, mdm_CORE_DUMP, E_MMGR_NOTIFY_CORE_DUMP_COMPLETE);
    mmgr_cli_subscribe_event(mmgr_hdl, mdm_AP_RESET, E_MMGR_NOTIFY_AP_RESET);
    mmgr_cli_subscribe_event(mmgr_hdl, mdm_TEL_ERROR, E_MMGR_NOTIFY_ERROR);
    mmgr_cli_connect(mmgr_hdl);
    // pipe init
    pipe(mmgr_monitor_fd);
}

void close_mmgr_cli_source(void){
    mmgr_cli_disconnect(mmgr_hdl);
    mmgr_cli_delete_handle(mmgr_hdl);
}
