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
 * @file fsutils.h
 * @brief File containing functions for basic operations on files.
 *
 * This file contains the functions for every basic operations on files such as
 * reading, writing, copying, pattern search, deletion, mode change...etc.
 */

#ifndef __FSUTILS_H__
#define __FSUTILS_H__

#include <cutils/properties.h>

#include <privconfig.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <stdio.h>

//ARGS for thread creation and copy_dir
struct arg_copy {
    int time_val;
    char orig[PATHMAX];
    char dest[PATHMAX];
};

/* Modes used for get_sdcard_paths */
enum {
    MODE_CRASH = 0,
    MODE_CRASH_NOSD,
    MODE_STATS,
    MODE_APLOGS,
    MODE_BZ,
    MODE_KDUMP,
};

typedef enum e_aplog_file {
    APLOG,
    APLOG_BOOT,
} e_aplog_file_t;

/* Mode used to cache a file into a buffer*/
#define CACHE_TAIL      0
#define CACHE_START     1

/* returns a negative value on error or the number of lines read */
/*
* Name          : cache_file
* Description   : copy source file lines into buffer.
*                 returns a negative value on error or the number of lines read
*                 offset value is used to skip the file's first lines
*/
int cache_file(char *filename, char **records, int maxrecords, int cachemode, int offset);

static inline int file_exists(char *filename) {
    struct stat info;

    return (stat(filename, &info) == 0);
}

static inline int get_file_size(char *filename) {
    struct stat info;

    if (filename == NULL) return -ENOENT;

    if (stat(filename, &info) < 0) {
        return -errno;
    }

    return info.st_size;
}

int read_file_prop_uid(char* propsource, char *filename, char *uid, char* defaultvalue);
int find_new_crashlog_dir(int mode);
int get_sdcard_paths(int mode);
void do_log_copy(char *mode, int dir, const char* ts, int type);
long get_sd_size();
int sdcard_allowed();

int find_matching_file(char *dir_to_search, char *pattern, char *filename_found);
int get_value_in_file(char *file, char *keyword, char *value, unsigned int sizemax);
int find_str_in_file(char *filename, char *keyword, char *tail);
int find_str_in_standard_file(char *filename, char *keyword, char *tail);
int find_oneofstrings_in_file(char *file, char **keywords, int nbkeywords);
void flush_aplog(e_aplog_file_t file, const char *mode, int *dir, const char *ts);
int readline(int fd, char buffer[MAXLINESIZE]);
int freadline(FILE *fd, char buffer[MAXLINESIZE]);
int append_file(char *filename, char *text);
int overwrite_file(char *filename, char *value);

int do_chmod(char *path, char *mode);
int do_chown(const char *file, char *uid, char *gid);
int do_copy_eof(const char *src, const char *des);
int do_copy_tail(char *src, char *dest, int limit);
int do_copy(char *src, char *dest, int limit);
int do_mv(char *src, char *dest);
int rmfr(char *path);
int rmfr_specific(char *path, int remove_dir);

void copy_dir(void *arguments);
void update_logs_permission(void);

int str_simple_replace(char *str, char *search, char *replace);
int get_parent_dir( char * dir, char *parent_dir );

char *compute_bp_log(const char* ext_file);

#endif /* __FSUTILS_H__ */
