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
 * @file fw_update.c
 * @brief File containing the function to process the firmware update status.
 */

#include <stdlib.h>
#include <stdio.h>
#include <fcntl.h>
#include "crashutils.h"

#define FW_UPDATE_ERROR "FW_UPDATE_ERROR_errorevent"
#define FW_UPDATE_NO_STATUS "FW_UPDATE_NO_STATUS"
#define NBR_REASON 5
#define PREFIX "Failures detected during firmware update:"
#define MAX_MSG_STATUS  39
#define MAX_REASON_SIZE  22

/**
 * @brief generate fw update crash event
 *
 * By reading the OSNIB fw update status set by the IA FW, the OS can check
 * if problems have occured during the update, and then report the reasons
 * in a crashfile.
 * It also reports if the fw status was succeffully cleared in case of a
 * non zero status.
 *
 * @param files
 */
int crashlog_check_fw_update_status(void)
{
    const char *BIT_TO_REASON[NBR_REASON] = {
                        "Failed",
                        "Capsule format problem",
                        "Problem with IAFW",
                        "Problem with PDR",
                        "Problem with SecFW" };
    // msg_status reports the failure trying to clear fw update status
    char msg_status[MAX_MSG_STATUS+1] = "Successfully cleared fw status";
    // msg_reason reports the reasons for the detected errors
    char msg_reason[256];
    char *end;
    int fw_update_status;
    int ret = 0;
    unsigned int i;
    FILE *filed;

    // if the sys_fs entry does not exist or fails to read, then exit
    filed = fopen(FW_UPDATE_STATUS_PATH, "r");
    if (filed == NULL){
        LOGE("cannot open file: %s\n", FW_UPDATE_STATUS_PATH);
        return -1;
    }

    /*
     * Log a CRASH event into /logs/history_event file along with the
     * path to the crashfile that is filled later on with the details.
     */
    errno = 0;

    /* update status is within 0..31 range + a new line */
    fgets(msg_reason, 4, filed);
    fclose(filed);
    fw_update_status = strtoul(msg_reason, &end, 10);
    if (*end != '\n' || errno == ERANGE) {
        LOGE("failed to parse firmware update value %s %d\n", msg_reason, fw_update_status);
        raise_infoerror(ERROREVENT, FW_UPDATE_NO_STATUS);
        return -1;
    }
    // if fw_update_status = 0 => no error so nothing to process
    if (!fw_update_status)
        return 0;

    // else let's log the encountered errors
    strncpy(msg_reason, "\0", 1);
    for (i = 0 ; i < NBR_REASON ; i++)
        if (fw_update_status & (1 << i)) {
            strncat(msg_reason, BIT_TO_REASON[i], MAX_REASON_SIZE);
            strncat(msg_reason, ", ", 2);
        }

    // As the fw update status was not zero => clear it
    int fd = open(FW_UPDATE_STATUS_PATH, O_WRONLY);
    if (fd == -1) {
        LOGE("failed to open file %s to clear the FW update status flag\n",
              FW_UPDATE_STATUS_PATH);
        strncpy(msg_status, "Cannot open sys_fs to clear fw status", MAX_MSG_STATUS);
    } else if (write(fd, msg_reason, 1) == -1) {
        LOGE("failed to write file %s to clear the FW update status flag\n",
              FW_UPDATE_STATUS_PATH);
        strncpy(msg_status, "Cannot write sys_fs to clear fw status", MAX_MSG_STATUS);
        ret = -1;
    }
    close(fd);

    // data0=PREFIX, data1=msg_reason, data2=msg_status
    create_infoevent(FW_UPDATE_ERROR, (char*) PREFIX, msg_reason, msg_status);

    return ret;
}

