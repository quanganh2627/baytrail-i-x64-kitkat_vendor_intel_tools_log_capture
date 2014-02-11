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
 * @file kct_netlink.c
 * @brief File containing functions for getting Kernel events.
 */

#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>
#include <poll.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <ctype.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <resolv.h>
#include <linux/netlink.h>
#include <linux/kct.h>

#include <cutils/properties.h>
#include "privconfig.h"
#include "fsutils.h"
#include "crashutils.h"
#include "kct_netlink.h"

#define PROP_PREFIX "dev.log"
#define BINARY_SUFFIX ".bin"
#define CTM_MAX_NL_MSG 4096

int sock_nl_fd = -1;

static const char *suffixes[] = {
        [CT_EV_STAT]    = "_trigger",
        [CT_EV_INFO]    = "_infoevent",
        [CT_EV_ERROR]   = "_errorevent",
        /* Temporary implementation: Kernel CRASH events are handled as
         * if they were Kernel ERROR events
         * [CT_EV_CRASH]   = "_crashdata",
         */
        [CT_EV_CRASH]   = "_errorevent",
        [CT_EV_LAST]    = "_ignored"
};

static int netlink_sendto_kct(int fd, int type, const void *data,
        unsigned int size);
static int netlink_init(void);
static struct kct_packet *netlink_get_packet(int fd);
static void handle_event(struct ct_event *ev);
static void process_netlink_msg(struct ct_event *ev);
static int dump_binary_attchmts_in_file(struct ct_event* ev, char* file_path);
static int dump_data_in_file(struct ct_event* ev, char* file_path);
static void convert_name_to_upper_case(char * name);

void kct_netlink_init_comm(void) {

    unsigned int connect_try = KCT_MAX_CONNECT_TRY;

    while (connect_try-- != 0) {
        /* Try to connect */
        if ((sock_nl_fd = netlink_init()) != -1)
            break;

        LOGE("%s: Delaying kct_netlink_init_comm\n", __FUNCTION__);

        /* Wait */
        sleep(KCT_CONNECT_RETRY_TIME_S);
    }
}

int kct_netlink_get_fd() {

    return sock_nl_fd;
}

void kct_netlink_handle_msg(void) {

    struct kct_packet *msg;

    msg = netlink_get_packet(sock_nl_fd);
    if (msg == NULL) {
        LOGE("Could not receive kernel packet: %s", strerror(errno));
        return;
    }

    handle_event(&msg->event);
    free(msg);
}

static int netlink_sendto_kct(int fd, int type, const void *data,
        unsigned int size) {

    struct sockaddr_nl addr;
    static int sequence = 0;
    struct kct_packet req;
    int retval;

    if (NLMSG_SPACE(size) > CTM_MAX_NL_MSG) {
        errno = EMSGSIZE;
        return -1;
    }

    /* when about to overflow, start over */
    if (++sequence < 0)
        sequence = 1;

    memset(&req, 0, sizeof(req));
    req.nlh.nlmsg_len = NLMSG_SPACE(size);
    req.nlh.nlmsg_type = type;
    req.nlh.nlmsg_flags = NLM_F_REQUEST;
    req.nlh.nlmsg_seq = sequence;

    if (size && data)
        memcpy(NLMSG_DATA(&req.nlh), data, size);

    memset(&addr, 0, sizeof(addr));
    addr.nl_family = AF_NETLINK;

    do {
        retval = sendto(fd, &req, req.nlh.nlmsg_len, 0,
                (struct sockaddr*)&addr, sizeof(addr));
    } while ((retval < 0) && (errno == EINTR));

    if (retval < 0)
        return -1;

    return sequence;
}

static int netlink_init(void) {

    int fd;

    if ((fd = socket(PF_NETLINK, SOCK_RAW, NETLINK_CRASHTOOL)) < 0) {
        ALOGE("socket: %s", strerror(errno));
        return -1;
    }

    if (fcntl(fd, F_SETFD, FD_CLOEXEC) == -1) {
        ALOGE("fcntl: %s", strerror(errno));
        close(fd);
        return -1;
    }

    if (netlink_sendto_kct(fd, KCT_SET_PID, NULL, 0) < 0) {
        ALOGE("ctm_nl_sendto_kct : %s", strerror(errno));
        close(fd);
        return -1;
    }

    LOGD("%s: Netlink intialization succeed.\n", __FUNCTION__);

    return fd;
}

static struct kct_packet *netlink_get_packet(int fd) {

    struct kct_packet *pkt;
    struct sockaddr_nl nladdr;
    char buf[sizeof(*pkt)];
    socklen_t nladdrlen;
    ssize_t len;

    assert(fd >= 0);

    nladdrlen = sizeof(nladdr);

    /* MSG_PEEK let the pkt in queue; MSG_TRUNK return full pkt length */
    len = recvfrom(fd, buf, sizeof(buf), MSG_PEEK|MSG_TRUNC,
            (struct sockaddr*)&nladdr, &nladdrlen);
    if ((size_t)len < sizeof(buf)) {
        if (len >= 0) {
            /* pop invalid packet from queue so we don't retrieve it later */
            recvfrom(fd, buf, sizeof(buf), MSG_TRUNC,
                    (struct sockaddr*)&nladdr, &nladdrlen);
            errno = EBADE;
        }
        return NULL;
    }

    LOGI("Packet received of size: %d\n", (int)len);

    pkt = malloc(len);
    if (pkt == NULL)
        return NULL;

    /* read full pkt now */
    len = recvfrom(fd, pkt, len, 0, (struct sockaddr*)&nladdr, &nladdrlen);
    if (len > 0)
        /* unless someone else read from the socket, len != 0 */
        if (NLMSG_OK(&pkt->nlh, (size_t)len))
            return pkt;

    free(pkt);
    errno = EBADE;

    return NULL;
}

static void handle_event(struct ct_event *ev) {

    char submitter[PROPERTY_KEY_MAX];
    char propval[PROPERTY_VALUE_MAX];

    if (ev->type >= CT_EV_LAST) {
        LOGE("Unkwon event type '%d', discarding", ev->type);
        return;
    }

    snprintf(submitter, sizeof(submitter), "%s.%s",
            PROP_PREFIX, ev->submitter_name);

    /*
     * to reduce confusion:
     * property can be either ON/OFF for a given submitter.
     * if it's ON, we want event not to be filtered
     * if it's OFF, we want event to be filtered
     * event should be flagged to be manage by this property.
     */
    if (ev->flags & EV_FLAGS_PRIORITY_LOW) {
        if (!property_get(submitter, propval, NULL))
            return;
        if (strcmp(propval, "ON"))
            return;
    }

    /* Process Kernel message */
    process_netlink_msg(ev);
}

static void process_netlink_msg(struct ct_event *ev) {

    char destination[PATHMAX];
    char name[MAX_SB_N+MAX_EV_N+2];
    char name_event[20];
    e_dir_mode_t mode;
    char *dir_mode;
    int dir;
    char *key;

    /* Temporary implementation: Crashlog handles Kernel CRASH events
     * as if they were Kernel ERROR events
     */
    switch (ev->type) {
    case CT_EV_STAT:
        mode = MODE_STATS;
        dir_mode = STATS_DIR;
        snprintf(name_event, sizeof(name_event), "%s", STATSEVENT);
        break;
    case CT_EV_INFO:
        mode = MODE_STATS;
        dir_mode = STATS_DIR;
        snprintf(name_event, sizeof(name_event), "%s", INFOEVENT);
        break;
    case CT_EV_ERROR:
    case CT_EV_CRASH:
        mode = MODE_STATS;
        dir_mode = STATS_DIR;
        snprintf(name_event, sizeof(name_event), "%s", ERROREVENT);
        break;
    case CT_EV_LAST:
    default:
        LOGE("%s: unknown event type\n", __FUNCTION__);
        return;
    }

    /* Compute name */
    snprintf(name, sizeof(name), "%s_%s", ev->submitter_name, ev->ev_name);
    /* Convert lower-case name into upper-case name */
    convert_name_to_upper_case(name);

    dir = find_new_crashlog_dir(mode);
    if (dir < 0) {
        LOGE("%s: Cannot get a valid new crash directory...\n", __FUNCTION__);
        key = raise_event(name_event, name, NULL, NULL);
        LOGE("%-8s%-22s%-20s%s\n", name_event, key,
                get_current_time_long(0), name);
        free(key);
        return;
    }

    if (ev->attchmt_size) {
        /* copy binary data into file */
        snprintf(destination, sizeof(destination), "%s%d/%s_%s%s",
                dir_mode, dir,
                ev->submitter_name, ev->ev_name, BINARY_SUFFIX);

        dump_binary_attchmts_in_file(ev, destination);
    }

    snprintf(destination, sizeof(destination), "%s%d/%s_%s%s",
            dir_mode, dir,
            ev->submitter_name, ev->ev_name, suffixes[ev->type]);

    /*
     * Here we copy only DATA{0,1,2} in the trig file, because crashtool
     * does not understand any other types. We attach others types in the
     * data file thanks to the function dump_binary_attchmts_in_file();
     */
    dump_data_in_file(ev, destination);

    snprintf(destination, sizeof(destination), "%s%d/", dir_mode, dir);
    key = raise_event(name_event, name, NULL, destination);
    LOGE("%-8s%-22s%-20s%s %s\n", name_event, key,
            get_current_time_long(0), name, destination);
    free(key);
}

static int dump_binary_attchmts_in_file(struct ct_event* ev, char* file_path) {

    struct ct_attchmt* at = NULL;
    char *b64encoded = NULL;
    FILE *file = NULL;
    int nr_binary = 0;

    LOGI("Creating %s\n", file_path);

    file = fopen(file_path, "w+");
    if (!file) {
        LOGE("can't open '%s' : %s\n", file_path, strerror(errno));
        return -1;
    }

    foreach_attchmt(ev, at) {
        switch (at->type) {
        case CT_ATTCHMT_BINARY:
            b64encoded = calloc(1, (at->size+2)*4/3);
            b64_ntop((u_char*)at->data, at->size,
                    b64encoded, (at->size+2)*4/3);
            fprintf(file, "BINARY%d=%s\n", nr_binary, b64encoded);
            ++nr_binary;
            free(b64encoded);
            break;
        case CT_ATTCHMT_DATA0:
        case CT_ATTCHMT_DATA1:
        case CT_ATTCHMT_DATA2:
	case CT_ATTCHMT_DATA3:
	case CT_ATTCHMT_DATA4:
	case CT_ATTCHMT_DATA5:
        /* Nothing to do */
            break;
        default:
            LOGE("Ignoring unknown attachment type: %d\n", at->type);
            break;
        }
    }

    fclose(file);

    /* No binary data in attachment. File shall be removed */
    if (!nr_binary)
        remove(file_path);

    return 0;
}

static int dump_data_in_file(struct ct_event* ev, char* file_path) {

    struct ct_attchmt* att = NULL;
    FILE *file = NULL;

    LOGI("Creating %s\n", file_path);

    file = fopen(file_path, "w+");
    if (!file) {
        LOGE("can't open '%s' : %s\n", file_path, strerror(errno));
        return -1;
    }

    foreach_attchmt(ev, att) {
        switch (att->type) {
        case CT_ATTCHMT_DATA0:
            fprintf(file, "DATA0=%s\n", att->data);
            break;
        case CT_ATTCHMT_DATA1:
            fprintf(file, "DATA1=%s\n", att->data);
            break;
        case CT_ATTCHMT_DATA2:
            fprintf(file, "DATA2=%s\n", att->data);
            break;
        case CT_ATTCHMT_DATA3:
            fprintf(file, "DATA3=%s\n", att->data);
            break;
        case CT_ATTCHMT_DATA4:
            fprintf(file, "DATA4=%s\n", att->data);
            break;
        case CT_ATTCHMT_DATA5:
            fprintf(file, "DATA5=%s\n", att->data);
            break;
        default:
            break;
        }
    }

    fclose(file);

    return 0;
}

static void convert_name_to_upper_case(char * name) {

    unsigned int i;

    for (i=0; i<strlen(name); i++) {
        name[i] = toupper(name[i]);
    }
}
