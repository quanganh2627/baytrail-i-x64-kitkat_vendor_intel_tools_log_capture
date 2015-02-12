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
 * @file fsutils.c
 * @brief File containing functions for basic operations on files.
 *
 * This file contains the functions for every basic operations on files such as
 * reading, writing, copying, pattern search, deletion, mode change...etc.
 */

#include "fsutils.h"
#include "privconfig.h"
#include "crashutils.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>
#include <errno.h>
#include <stdio.h>
#include <fcntl.h>

#include <cutils/log.h>
#include <sys/statfs.h>

#ifndef __TEST__
#include <private/android_filesystem_config.h>
#endif

long current_sd_size_limit = LONG_MAX;

/* No header in bionic... */
ssize_t sendfile(int out_fd, int in_fd, off_t *offset, size_t count);

static int checkMemAvailable(const char* path, unsigned int req )
{
    struct statfs st;
    unsigned long long reserved;

    if (statfs(path, &st) < 0) {
        LOGE("%s: warn: statfs failed on %s, err:%s!!!", __FUNCTION__, path, strerror(errno));
        return 0;
    }

    reserved = st.f_blocks / 100 * req;
    if( st.f_bavail < reserved) {
        LOGE("%s: warn: no space avail in %s,  only %llu of %llu free!", __FUNCTION__, path, st.f_bavail, st.f_blocks);
        return -1;
    }

   return 0;
}


int readline(int fd, char buffer[MAXLINESIZE]) {
    int size = 0, res;
    char *pbuffer = &buffer[0];

    /* Read the file until end of line or file */
    while ((res = read(fd, pbuffer, 1)) == 1 && size < MAXLINESIZE-1) {
        if (pbuffer[0] == '\n') {
            buffer[++size] = 0;
            return size;
        }
        pbuffer++;
        size++;
    }

    /* Check the last read result */
    if (res < 0) {
        /* ernno is checked in the upper layer as we could
           print the filename here */
        return res;
    }
    /* last line */
    buffer[size] = 0;
    return size;
}

int freadline(FILE *fd, char buffer[MAXLINESIZE]) {
    int size = 0, res;
    char *pbuffer = &buffer[0];

    /* Read the file until end of line or file */
    while ((res = fread(pbuffer, 1 , 1, fd)) == 1 && size < MAXLINESIZE-1) {
        if (pbuffer[0] == '\n') {
            buffer[++size] = 0;
            return size;
        }
        pbuffer++;
        size++;
    }

    /* Check the last read result */
    if (res < 0) {
        /* ernno is checked in the upper layer as we could
           print the filename here */
        return res;
    }
    /* last line */
    buffer[size] = 0;
    return size;
}

int find_oneofstrings_in_file(char *filename, char **keywords, int nbkeywords) {

    char buffer[MAXLINESIZE];
    int fd, linesize, idx;

    if (!keywords || !filename || !nbkeywords)
        return -EINVAL;

    fd = open(filename, O_RDONLY);
    if(fd <= 0) return -errno;

    while((linesize = readline(fd, buffer)) > 0) {
        /* Remove the trailing '\n' if it's there */
        if (buffer[linesize-1] == '\n') {
            linesize--;
            buffer[linesize] = 0;
        }

        /* Check the keywords */
        for (idx = 0 ; idx < nbkeywords ; idx++) {
            if ( strstr(buffer, keywords[idx]) ) {
                close(fd);
                return 1;
            }
        }
    }
    close(fd);
    return 0;
}

/**
 * Finds if file defined by filename contains given keyword and
 * if the matching line also ends with given tails (if provided).
 * It returns 1 if a match is found. 0 otherwise.
 * This function does not work on virtual files such as files located
 * in /proc directory.
 */
int find_str_in_standard_file(char *filename, char *keyword, char *tail) {
    char buffer[MAXLINESIZE];
    int fd, linesize;
    int taillen;

    if (keyword == NULL || filename == NULL)
        return -EINVAL;
    fd = open(filename, O_RDONLY);
    if(fd <= 0)
        return -errno;

    /* Check the tail length once and for all */
    taillen = (tail ? strlen(tail) : 0);

    while((linesize = readline(fd, buffer)) > 0) {
        /* Remove the trailing '\n' if it's there */
        if (buffer[linesize-1] == '\n') {
            linesize--;
            buffer[linesize] = 0;
        }

        /* Check the keyword */
        if ( !strstr(buffer, keyword) ) continue;

        /* Check the tail? */
        if (!tail) break;

        /* a tail but the line is too short, continue... */
        if (taillen > linesize) continue;

        /* Do the tail's check*/
        if ( !strncmp(&buffer[linesize - taillen], tail, taillen) ) break;
    }

    close(fd);
    return (linesize > 0 ? 1 : 0);
}

/**
 * Finds if file defined by filename contains given keyword and
 * if the matching line also ends with given tails (if provided).
 * It returns 1 if a match is found. 0 otherwise.
 */
int find_str_in_file(char *filename, char *keyword, char *tail)
{
    char buffer[4 * KB];
    int rc = 0;
    FILE *fd1;
    struct stat info;
    int buflen;

    if (keyword == NULL || filename == NULL)
        return -EINVAL;

    if (stat(filename, &info) < 0) {
        LOGE("%s: can not open file: %s - error is %s.\n", __FUNCTION__, filename, strerror(errno) );
        return -errno;
    }
    fd1 = fopen(filename, "r");
    if(fd1 == NULL) {
        LOGE("%s : can not open file: %s - error is %s.\n", __FUNCTION__, filename, strerror(errno) );
        return -errno;
    }
    while(!feof(fd1)){
        if (fgets(buffer, sizeof(buffer), fd1) != NULL){
            /* Check the keyword */
            if (keyword && strstr(buffer,keyword)){
                /* Check the tail? */
                if (!tail){
                    rc = 1;
                    break;
                } else{
                    /* Do the tail's check*/
                    int buflen = strlen(buffer);
                    int str2len = strlen(tail);
                    if ((buflen > str2len) && (!strncmp(&(buffer[buflen-str2len-1]), tail, strlen(tail)))){
                        rc = 1;
                        break;
                    }
                }
            }
        }
    }
    fclose(fd1);
    return rc;
}

int get_value_in_file(char *file, char *keyword, char *value, unsigned int sizemax)
{
    char buffer[MAXLINESIZE];
    int keylen;
    FILE *fd;

    if ( !file || keyword == NULL || !value || !sizemax )
        return -EINVAL;

    if ( !file_exists(file) )
        return -ENOENT;


    if ( (fd = fopen(file,"r")) == NULL)
        return -errno;

    /* Once and for all */
    keylen = strlen(keyword);

    while( !feof(fd) ){
        if (fgets(buffer, sizeof(buffer), fd) != NULL) {
            if ( strstr(buffer, keyword) ) {
                unsigned int buflen = strlen(buffer);
                if( buffer[buflen-1] == '\n')
                    buffer[--buflen]= '\0';
                int size = buflen - keylen;
                if (size > (int)sizemax) {
                    /* return bad argument...*/
                    LOGE("%s: %s found but buffer provided of %d bytes is too short to handle \"%s\"\n",
                        __FUNCTION__, keyword, sizemax, buffer);
                    fclose(fd);
                    return -EINVAL;
                }
                strncpy(value, &buffer[keylen], size);
                fclose(fd);
                return 0;
            }
        }
    }

    /* Did not find the keyword... */
    fclose(fd);
    return -1;
}

int get_sdcard_paths(int mode) {
    char value[PROPERTY_VALUE_MAX];
    DIR *d;

    CRASH_DIR = EMMC_CRASH_DIR;
    STATS_DIR = EMMC_STATS_DIR;
    APLOGS_DIR = EMMC_APLOGS_DIR;
    BZ_DIR = EMMC_BZ_DIR;

#ifndef FULL_REPORT
    return 0;
#else

    property_get(PROP_CRASH_MODE, value, "");
    if ((!strncmp(value, "lowmemory", 9)) || (mode == CRASH_MODE_NOSD) || !sdcard_allowed())
        return 0;

    errno = 0;
    if (!file_exists(SDCARD_LOGS_DIR))
        mkdir(SDCARD_LOGS_DIR, 0777);

    /* check whether there's extra available space for new logs in the SD card */
    if (checkMemAvailable(SDCARD_LOGS_DIR, SDCARD_MINIMUN_FREEMEM_PERCENT) < 0) {
        return 0;
     }

    if ( (d = opendir(SDCARD_LOGS_DIR)) != NULL ){
        CRASH_DIR = SDCARD_CRASH_DIR;
        STATS_DIR = SDCARD_STATS_DIR;
        APLOGS_DIR = SDCARD_APLOGS_DIR;
        BZ_DIR = SDCARD_BZ_DIR;
        closedir(d);
    }
    return -errno;
#endif
}

int find_new_crashlog_dir(int mode) {
    char path[PATHMAX];
    unsigned int res, current;
    FILE *fd;
    char *dir;

    get_sdcard_paths(mode);

    switch(mode) {
        case MODE_CRASH:
        case MODE_CRASH_NOSD:
            snprintf(path, sizeof(path), CRASH_CURRENT_LOG);
            dir = CRASH_DIR;
            break;
        case MODE_APLOGS:
            snprintf(path, sizeof(path), APLOGS_CURRENT_LOG);
            dir = APLOGS_DIR;
            break;
        case MODE_BZ:
            snprintf(path, sizeof(path), BZ_CURRENT_LOG);
            dir = BZ_DIR;
            break;
        case MODE_STATS:
            snprintf(path, sizeof(path), STATS_CURRENT_LOG);
            dir = STATS_DIR;
            break;
        case MODE_KDUMP:
            snprintf(path, sizeof(path), CRASH_CURRENT_LOG);
            dir = KDUMP_CRASH_DIR;
            break;
        default:
            LOGE("%s: Invalid mode %d\n", __FUNCTION__, mode);
            return -1;
    }

    /* check whether there's extra available space for new logs */
    if (!strncmp(dir, LOGS_DIR, strlen(LOGS_DIR))) {
        if( checkMemAvailable(LOGS_DIR, EMMC_MINIMUN_FREEMEM_PERCENT) < 0){
            return -1;
         }
    }

    if ( (fd = fopen(path, "r")) != NULL ) {
        res = fscanf(fd, "%4d", &current);
        if (res != 1) {
            /* Set it to 0 by default */
            current = 0;
        }
        fclose(fd);
    } else if (errno == ENOENT) {
        LOGE("%s: File %s does not exist, fall back to folder 0.\n", __FUNCTION__, path);
        current = 0;
    } else {
        LOGE("%s: Cannot open file %s - error is %s.\n", __FUNCTION__, path, strerror(errno));
        raise_infoerror(ERROREVENT, CRASHLOG_ERROR_PATH);
        return -1;
    }
    /* Open it in write mode now to create and write the new current */
    if ( (fd = fopen(path, "w")) == NULL ){
        LOGE("%s: Cannot open the file %s in write mode\n", __FUNCTION__, path);
        raise_infoerror(ERROREVENT, CRASHLOG_ERROR_PATH);
        return -1;
    }
    fprintf(fd, "%4d", ((current+1) % gmaxfiles));

    errno = 0;
    if (fclose(fd)!=0) {
        /* File closure could failed in some cases (full partition)*/
        LOGE("%s: fclose on %s failed - error is %s", __FUNCTION__, path, strerror(errno));
    }

    snprintf(path, sizeof(path), "%s%d", dir, current);
    /* Call rmfr which will fail if the path does not exist
     * but doesn't matter as we create it afterwards
     */
    rmfr(path);

    /* Create a fresh directory */
    if (mkdir(path, 0777) == -1) {
        LOGE("%s: Cannot create dir %s\n", __FUNCTION__, path);
        raise_infoerror(ERROREVENT, CRASHLOG_ERROR_PATH);
        return -errno;
    }

    if (!strstr(path, "sdcard"))
        do_chown(path, PERM_USER, PERM_GROUP);

    return current;
}

int find_matching_file(char *dir_to_search, char *pattern, char *filename_found)
{
    DIR *d;
    struct dirent* de;
    int result = 0;

    if (dir_to_search == NULL) return -EINVAL;
    if (pattern == NULL) return -EINVAL;
    if (filename_found == NULL) return -EINVAL;

    d = opendir(dir_to_search);
    if (d == NULL) return -errno;
    while ((de = readdir(d))) {
        const char *name = de->d_name;
        if (strstr(name, pattern)){
            sprintf(filename_found, "%s", name);
            result = 1;
        }
    }
    closedir(d);
    return result;
}

int rmfr(char *path) {
    return rmfr_specific(path, 1);
}

int rmfr_specific(char *path, int remove_dir) {
    DIR *d;
    struct dirent *de;
    char fsentry[PATHMAX];
    int subres = 0;
    unsigned char isFile = 0x8;

    /* Check for a simple file or link first */
    if ( !unlink(path) ) return 0;

    /* Failed; if the error was not EISDIR or ENOENT,
     * no need to pursue...
     */
    if ( errno != EISDIR && errno != ENOENT ) return errno;

    /* path is a directory, remove all the contents recursively
     * before deleting it
     */
    d = opendir(path);
    if ( d == NULL ) {
        return errno;
    }
    while ((de = readdir(d)) != NULL) {
        if (!strcmp(de->d_name, ".") || !strcmp(de->d_name, ".."))
            continue;
        /* Remove file in every case and remove directory if required */
        if ( de->d_type == isFile || (de->d_type != isFile && remove_dir) ) {
            snprintf(fsentry, sizeof(fsentry), "%s/%s", path, de->d_name);
            if ( unlink(path) ) {
                if (errno == EISDIR)
                    subres = rmfr(fsentry);
                if (subres) return subres;
            }
        }
    }
    closedir(d);
    if (remove_dir)
        /* Finally delete the empty directory */
        return rmdir(path);
    else
        return 0;
}


#ifndef USE_SYSTEM_CMDS
static mode_t get_mode(const char *s)
{
    mode_t mode = 0;
    while (*s) {
        if (*s >= '0' && *s <= '7') {
            mode = (mode << 3) | (*s - '0');
        } else {
            return -1;
        }
        s++;
    }
    return mode;
}
#endif

int do_chmod(char *path, char *mode)
{
#ifdef USE_SYSTEM_CMDS
    char *cmd[PATHMAX + 20];
    cmd[0] = 0;
    snprintf(cmd, sizeof(cmd)-1, "/system/bin/chmod %s %s", mode, path);
    if (cmd[0] == 0 || system(cmd) == -1 ) {
            return errno;
    }
    return 0;
#else
    mode_t mod = get_mode(mode);
    if (chmod(path, mod) < 0) {
        return errno;
    }
    return 0;
#endif
}

#ifndef TEST_USER
static unsigned int android_name_to_id(const char *name, int *res)
{
    const struct android_id_info *info = android_ids;
    unsigned int n;
    *res = 0;

    for (n = 0; n < android_id_count; n++) {
        if (!strcmp(info[n].name, name))
            return info[n].aid;
    }

    *res = EINVAL;
    return -1U;
}
#endif

static unsigned int decode_uid(const char *s, int *res)
{
    *res = 0;

    if (!s || *s == '\0') {
        *res = -EINVAL;
        return -1;
    }
#ifndef TEST_USER
    unsigned int v;
    if (isalpha(s[0]))
        return android_name_to_id(s, res);

    errno = 0;
    v = (unsigned int)strtoul(s, 0, 0);
    if (errno) {
        *res = -errno;
        return -1U;
    }
    return v;
#else
    /* HACK for testing only */
    return TEST_USER;
#endif
}

int do_chown(const char *file, char *uid, char *gid)
{
    unsigned int duid, dgid;
    int result = 0;

    if (file == NULL) return -ENOENT;

    if (strstr(file, SDCARD_CRASH_DIR))
        return 0;

    duid = decode_uid(uid, &result);
    if ( result ) return result;

    dgid = decode_uid(gid, &result);
    if ( result ) return result;

    if ( chown(file, duid, dgid) )
        return -errno;

    return 0;
}

ssize_t do_read(int fd, void *buf, size_t len)
{
    ssize_t nr;
    while (1) {
        nr = read(fd, buf, len);
        if ((nr < 0) && (errno == EAGAIN || errno == EINTR))
            continue;
        return nr;
    }
}

ssize_t do_write(int fd, const void *buf, size_t len)
{
    ssize_t nr;
    while (1) {
        nr = write(fd, buf, len);
        if ((nr < 0) && (errno == EAGAIN || errno == EINTR))
            continue;
        return nr;
    }
}

int do_copy_eof(const char *src, const char *des)
{
    int buflen;
    char buffer[CPBUFFERSIZE];
    int rc = 0;
    int fd1 = -1, fd2 = -1;
    struct stat info;
    int r_count, w_count;

    if (src == NULL || des == NULL) return -EINVAL;

    if (stat(src, &info) < 0) {
        LOGE("%s: can not open file: %s\n", __FUNCTION__, src);
        return -errno;
    }

    if ( ( fd1 = open(src, O_RDONLY) ) < 0 ) {
        return -errno;
    }

    if ( ( fd2 = open(des, O_WRONLY | O_CREAT | O_TRUNC, 0660) ) < 0) {
        LOGE("%s: can not open file: %s\n", __FUNCTION__, des);
        close(fd1);
        return -errno;
    }

    while (1) {
        r_count = do_read(fd1, buffer, CPBUFFERSIZE);
        if (r_count < 0) {
            LOGE("%s: read failed, err:%s", __FUNCTION__, strerror(errno));
            goto out_err;
        }
        if (r_count == 0)
            break;

        w_count = do_write(fd2, buffer, r_count);
        if (w_count < 0) {
            LOGE("%s: write failed, err:%s", __FUNCTION__, strerror(errno));
            goto out_err;
        }
        if (r_count != w_count) {
            LOGE("%s: write failed, r_count:%d w_count:%d",
                 __FUNCTION__, r_count, w_count);
            goto out_err;
        }
    }

    rc = 0;
    goto out;
out_err:
    rc = -1;
out:
    if (fd1 >= 0)
        close(fd1);
    if (fd2 >= 0)
        close(fd2);

    do_chown(des, PERM_USER, PERM_GROUP);
    return rc;
}

int do_copy_tail(char *src, char *dest, int limit) {
    int rc = 0;
    int fsrc = -1, fdest = -1;
    struct stat info;
    off_t offset = 0;

    if (src == NULL || dest == NULL) return -EINVAL;

    if (stat(src, &info) < 0) {
        return -errno;
    }

    if ( ( fsrc = open(src, O_RDONLY) ) < 0 ) {
        return -errno;
    }

    if ( ( fdest = open(dest, O_WRONLY | O_CREAT | O_TRUNC, 0660) ) < 0) {
        close(fsrc);
        return -errno;
    }

    if (limit == 0)
        limit = info.st_size;

    if (info.st_size > limit)
        offset = info.st_size - limit;

    rc = sendfile(fdest, fsrc, &offset, limit);

    close(fsrc);
    close(fdest);
    do_chown(dest, PERM_USER, PERM_GROUP);
    return rc;
}

int do_copy(char *src, char *dest, int limit) {
    int rc = 0;
    int fsrc = -1, fdest = -1;

    if (src == NULL || dest == NULL) return -EINVAL;

    if ( ( fsrc = open(src, O_RDONLY) ) < 0 ) {
        return -errno;
    }

    if ( ( fdest = open(dest, O_WRONLY | O_CREAT | O_TRUNC, 0660) ) < 0) {
        close(fsrc);
        return -errno;
    }

    if (limit == 0) {
        /* test this late as if limit is 0, the dest file shall be
         * empty
         */
        close(fsrc);
        close(fdest);
        do_chown(dest, PERM_USER, PERM_GROUP);
        return 0;
    }

    rc = sendfile(fdest, fsrc, NULL, limit);

    close(fsrc);
    close(fdest);
    do_chown(dest, PERM_USER, PERM_GROUP);
    return rc;
}

int do_mv(char *src, char *dest) {
    struct stat info;

    if (src == NULL || dest == NULL) return -EINVAL;

    if (stat(src, &info) < 0) {
        return -errno;
    }
    /* check if destination exists */
    if (stat(dest, &info)) {
        /* an error, unless the destination was missing */
        if (errno != ENOENT) {
            LOGE("%s: failed on '%s', err:%s", __FUNCTION__, dest, strerror(errno));
            return -1;
        }
    }
    /* attempt to move it */
    if (rename(src, dest)) {
        LOGE("%s: failed on '%s', err:%s\n",  __FUNCTION__, src, strerror(errno));
        return -1;
    }
    return 0;
}

void qs_swap(void *array, int *indexes, int first, int second) {
    int itmp;
    char *vtmp;
    char **carray = array;

    itmp = indexes[first];
    indexes[first] = indexes[second];
    indexes[second] = itmp;

    vtmp = carray[first];
    carray[first] = carray[second];
    carray[second] = vtmp;
}

int qs_partition(void *array, int *indexes, int start, int end, int pivot) {
    int j = start, i;

    qs_swap(array, indexes, pivot, end);
    for (i = start ; i < end ; i++) {
        if (indexes[i] <= indexes[end]) {
            qs_swap(array, indexes, i, j);
            j++;
        }
    }
    qs_swap(array, indexes, end, j);
    return j;
}

void __quicksort(void *array, int *indexes, int start, int end) {
    int pivot;
    if ( start < end ) {
        pivot = qs_partition(array, indexes, start, end, start);
        __quicksort(array, indexes, start, pivot-1);
        __quicksort(array, indexes, pivot+1, end);
    }
}

#ifdef __DEBUG_QS__
void dump_char_array(char **array, int size) {
    int idx;

    for (idx = 0 ; idx < size ; idx++)
        printf("line %d: %s", idx, array[idx]);
}

void dump_int_array(int *array, int size) {
    int idx;

    printf("[");
    for (idx = 0 ; idx < size - 1 ; idx++)
        printf("%d, ", array[idx]);
    printf("%d]\n", array[idx]);
}
#endif

int quicksort(void *array, int dim, int pivot) {
    int *indexes, i, diff;

    indexes = (int*)malloc(dim*sizeof(int));
    if (indexes == NULL) return -ENOMEM;

    diff = dim - pivot;
    for (i = 0 ; i < dim ; i++) {
        if (i < pivot)
            indexes[i] = diff + i;
        else indexes[i] = i - pivot;
    }
#ifdef __DEBUG_QS__
    printf("Sort an array of %d items from pivot %d containing:\n", dim, pivot);
    dump_char_array((char**)array, dim);
    printf("Unsorted indexes: ");
    dump_int_array(indexes, dim);
#endif
    __quicksort(array, indexes, 0, dim-1);

#ifdef __DEBUG_QS__
    printf("Sorted into:\n");
    dump_char_array((char**)array, dim);
    printf("Sorted indexes: ");
    dump_int_array(indexes, dim);
#endif

    free(indexes);
    return 0;
}

/*
* Name          : cache_file
* Description   : copy source file lines into buffer.
*                 returns a negative value on error or the number of lines read and copied into the buffer.
*                 offset value is used to skip the file's first lines.
*/
int cache_file(char *filename, char **records, int maxrecords, int cachemode, int offset) {

    char curline[MAXLINESIZE];
    int fd, res = 0, index, line_idx = 0;

    if (cachemode != CACHE_START && cachemode != CACHE_TAIL) {
        return -EINVAL;
    }
    if ( !filename || !records || (offset < 0) || (offset >= maxrecords)) {
        return -EINVAL;
    }
    if (maxrecords == 0) return 0;

    if ( ( fd = open(filename, O_RDONLY) ) < 0) {
        return -errno;
    }
    /* Initialize the buffer to NULL pointers */
    for ( index = 0 ; index < maxrecords ; index++)
        records[index] = NULL;

    if (cachemode == CACHE_START) {
        for (index = 0 ; index < maxrecords ; index++) {
            if ( (res = readline(fd, curline)) < 0) {
                /* readline failed, cleanup and exit */
                goto do_cleanup;
            }
            /*Start to copy line in the buffer when line number is equal to offset value*/
            if ( line_idx >= offset) {
                if (res == 0) {
                    /* file terminated */
                    close(fd);
                    return (index - offset);
                }
                /* add a new line to our buffer */
                records[index - offset] = strdup(curline);
                if (records[index - offset] == NULL) {
                    res = -errno;
                    goto do_cleanup;
                }
            }
            line_idx++;
        }
        close(fd);
        return index;
    }

    if (cachemode == CACHE_TAIL) {
        int curindex = 0, count = 0;
        while((res = readline(fd, curline)) > 0) {
            /*Start to copy line when line number is equal to offset value*/
            if ( line_idx >= offset) {
                /* add a new file to our buffer */
                if (records[curindex] != NULL)
                    free(records[curindex]);
                records[curindex] = strdup(curline);
                if (records[curindex] == NULL) {
                    res = -errno;
                    goto do_cleanup;
                }
                curindex = (curindex + 1) % maxrecords;
                if (count < maxrecords)
                    count++;
            }
            line_idx++;
        }
        if ( res < 0) {
            /* readline failed, cleanup and exit */
            goto do_cleanup;
        }
        /* res == 0 => EOF */
        if (count == maxrecords && curindex != 0) {
            /* Reordering the buffer is necessary */
#ifdef __DEBUG_QS__
            printf("Reorder the buffer (count = %d, maxrecords = %d, curindex = %d)\n", count, maxrecords, curindex);
#endif
            if ((res = quicksort(records, count, curindex)) < 0) {
                LOGE("%s: Cannot sort the final array: %s\n", __FUNCTION__, strerror(-res));
                goto do_cleanup;
            }
        }
        close(fd);
        return count;
    }
do_cleanup:
    for ( index = 0 ; index < maxrecords ; index++)
        if (records[index] != NULL) {
            free(records[index]);
            records[index] = NULL;
        }
    close(fd);
    return res;
}

int append_file(char *filename, char *text) {

    int fd, res, len;

    if (!filename || !text)
        return -EINVAL;

    len = strlen(text);
    if (!len) return 0;

    if ( ( fd = open(filename, O_RDWR | O_APPEND) ) < 0) {
        return -errno;
    }

    res = write(fd, text, len);
    close(fd);
    return (res == -1 ? -errno : res);
}

int overwrite_file(char *filename, char *value) {
    FILE *fd;

    if ( !filename || !value )
        return -EINVAL;

    fd = fopen(filename, "w+");
    if (fd == NULL) return -errno;

    if (fprintf(fd, "%s", value) <= 0) return -errno;
    fclose(fd);
    return 0;
}

int read_file_prop_uid(char* source, char *filename, char *uid, char* defaultvalue) {
    FILE *fd;
    char buffer[MAXLINESIZE] = {'\0'};
    char temp_uid[PROPERTY_VALUE_MAX];

    if ((source && filename && uid && defaultvalue) == 0)
        return -1;
    strncpy(uid, defaultvalue, PROPERTY_VALUE_MAX);
    fd = fopen(filename, "r");
    if (fd!=NULL){
        freadline(fd, buffer);
        strncpy(uid, buffer, PROPERTY_VALUE_MAX);
        fclose(fd);
    }

    if (property_get(source, temp_uid, "") <= 0) {
        LOGE("Property %s not readable\n", source);
        return -1;
    }
    strncpy(uid, temp_uid, PROPERTY_VALUE_MAX);
    if (strncmp(uid, buffer, PROPERTY_VALUE_MAX) != 0){
        /*need to reopen file in w mode and write the property in the file*/
        fd = fopen(filename, "w");
        if (fd!=NULL){
            fprintf(fd, "%s", uid);
            do_chown(filename, PERM_USER, PERM_GROUP);
            fclose(fd);
        }else{
            LOGE("Can't open file %s \n", filename);
            return -1;
        }
    }
    return 0;
}

#ifndef FULL_REPORT
void flush_aplog()
{
    struct stat info;
    int status;

    if(stat(APLOG_FILE_0, &info) == 0){
        remove(APLOG_FILE_0);
    }
    status = system("/system/bin/logcat -b system -b main -b radio -b events -v threadtime -d -f /data/logs/aplog");
    if (status != 0)
        LOGE("dump logcat returns status: %d.\n", status);
    do_chown(APLOG_FILE_0, PERM_USER, PERM_GROUP);
}
#endif

void do_log_copy(char *mode, int dir, const char* timestamp, int type) {
    char destination[PATHMAX], *logfile0, *logfile1, *extension;
    struct stat info;
    char *dir_pattern = CRASH_DIR;
    int limit = MAXFILESIZE;

    switch (type) {
        case APLOG_TYPE:
        case APLOG_STATS_TYPE:
        case KDUMP_TYPE:
#ifndef FULL_REPORT
            flush_aplog();
#endif
            logfile0 = APLOG_FILE_0;
            logfile1 = APLOG_FILE_1;
            extension = "";
            if (type == APLOG_STATS_TYPE)
                dir_pattern = STATS_DIR;
            else if (type == KDUMP_TYPE)
                dir_pattern = KDUMP_CRASH_DIR;
            break;
        case BPLOG_TYPE:
            logfile0 = BPLOG_FILE_0;
            logfile1 = BPLOG_FILE_1;
            extension = ".istp";
            limit = 0; /* no limit size for bplogs copy */
            break;
        case BPLOG_TYPE_OLD:
            logfile0 = BPLOG_FILE_1_OLD;
            logfile1 = BPLOG_FILE_2_OLD;
            extension = ".istp";
            /* limit size remains for old bplogs*/
            break;
        default:
            /* Ignore unknown type, just return */
            return;
    }
    if(stat(logfile0, &info) == 0) {
        snprintf(destination,sizeof(destination), "%s%d/%s_%s_%s%s", dir_pattern, dir, strrchr(logfile0,'/')+1, mode, timestamp, extension);
        do_copy_tail(logfile0, destination, limit);
        if(info.st_size < 1*MB) {
            if(stat(logfile1, &info) == 0) {
                snprintf(destination,sizeof(destination), "%s%d/%s_%s_%s%s", dir_pattern, dir, strrchr(logfile1,'/')+1, mode, timestamp, extension);
                do_copy_tail(logfile1, destination, limit);
            }
        }
#ifndef FULL_REPORT
        remove(APLOG_FILE_0);
#endif
    }
}

void copy_dir(void *arguments)
{
    struct arg_copy *args = (struct arg_copy *)arguments;
    DIR *d;
    struct dirent* de;
    char dir_src[PATHMAX] = { '\0', };
    char dir_des[PATHMAX] = { '\0', };
    int cp_time_val;
    //need a local copy at the beginning
    snprintf(dir_src, sizeof(dir_src), "%s", args->orig);
    snprintf(dir_des, sizeof(dir_des), "%s", args->dest);
    cp_time_val = args->time_val;
    //free parameter the sooner to avoid any possible leak
    free(args);
    d = opendir(dir_src);
    if(!d) {
        LOGE("%s: Can't open dir %s\n",__FUNCTION__, dir_src);
        return;
    }
    if (cp_time_val > 0){
        sleep(cp_time_val);
    }
    while ((de = readdir(d))) {
        //protection for . and .. "default folder"
        if (!strcmp(de->d_name, ".") || !strcmp(de->d_name, ".."))
          continue;
        const char *name = de->d_name;
        char src[PATHMAX] = { '\0', };
        char des[PATHMAX] = { '\0', };
        //TO DO : rework the "/" part
        snprintf(src, sizeof(src), "%s/%s", dir_src,name);
        snprintf(des, sizeof(des), "%s/%s", dir_des,name);
        int status = do_copy(src, des, 0);
        if (status != 0)
            LOGE("copy error for %s.\n",name);
    }
    closedir(d);
}

/*
* Name          : str_simple_replace
* Description   : replace the searched sequence by the replace sequence
*                   warning : it only works with sequence with the same size
* Parameters    :
*   char *str         -> full string to be processed
*   char *search      -> sequence to be replaced
*   char * replace    ->  string sequence used to replace searched sequence
* @return 0 on success, -1 on error or nothing to replace. */
int str_simple_replace(char *str, char *search, char *replace)
{
    char *f;
    //warning search and replace should have the same size
    if (strlen(search)!= strlen(replace)){
        LOGE("%s: str_simple_replace : size error", __FUNCTION__);
        return -1;
    }
    f = strstr(str, search);
    if (f != NULL) {
        strncpy(f,replace,strlen(replace));
        return 0;
    }else{
        //nothing to replace => error
        return -1;
    }
}

/**
 * @brief extracts the parent directory of the input dir.
 *
 * @param dir        : input directory
 * @param parent_dir : parent path of input directory
 * @return : 0 if succeeds. -1 if fails.
 */
int get_parent_dir( char * dir, char *parent_dir )
{
    char path[PATHMAX];
    char * ptr;

    if ( !dir || !parent_dir )
        return -EINVAL;

    strncpy(path, dir, PATHMAX-1);
	path[PATHMAX-1] = '\0';
    ptr = strrchr(path, '/');

    if ( !ptr || strlen(path) <= 1 )
        return -1;

    /* Manage case path terminates by '/' character */
    if ( *(ptr + 1) == '\0' ) {
        *(ptr) = '\0';
        ptr = strrchr(path, '/');
    }
	if(ptr){
        *(ptr + 1) = '\0';
	}
    if ( strlen(path) == 0 )
        return -1;

    strncpy( parent_dir, path, PATHMAX-1);
    return 0;
}

long get_sd_size()
{
    FILE *fd;
    long l_size=0;
    //first calculate SDSIZE folder with the result of a du command
    int status = system(SDSIZE_SYSTEM_CMD);
    if (status != 0){
        LOGE("get_sd_size status: %d.\n", status);
    }
    //then read the result
    fd = fopen(SDSIZE_CURRENT_LOG, "r");
    if (fd == NULL){
        LOGE("can not open file: %s\n", SDSIZE_CURRENT_LOG);
        //if size could not be calculated, we consider size at zero value
        return 0;
    }
    if (fscanf(fd, "%ld", &l_size)==EOF) {
        l_size = 0;
    }
    if (fd != NULL){
        fclose(fd);
    }
    return l_size;
}

int sdcard_allowed()
{
    /* Does current crashlog mode allow SDcard storage ?*/
    if ( !CRASHLOG_MODE_SD_STORAGE(g_crashlog_mode) ) {
        ALOGD("%s : Current crashlog mode is %s - SDCard storage disabled.\n", __FUNCTION__, CRASHLOG_MODE_NAME(g_crashlog_mode));
        return 0;
    }
    //now check remain size on SD
    if (get_sd_size() > current_sd_size_limit){
        LOGE("SD not allowed - current_sd_size_limit reached: %ld.\n", current_sd_size_limit);
        return 0;
    }else{
        return 1;
    }
}

/**
 * Updates rights of folders containing logs
 */
void update_logs_permission(void)
{
#ifdef FULL_REPORT
    char value[PROPERTY_VALUE_MAX] = "0";

    if (property_get(PROP_COREDUMP, value, "") <= 0) {
        LOGE("Property %s not readable - core dump capture is disabled\n", PROP_COREDUMP);
    } else if ( value[0] == '1' ) {
        LOGI("Folders /logs and /logs/core set to 0777\n");
        chmod(LOGS_DIR,0777);
        chmod(HISTORY_CORE_DIR,0777);
    }
#else
    return;
#endif
}
