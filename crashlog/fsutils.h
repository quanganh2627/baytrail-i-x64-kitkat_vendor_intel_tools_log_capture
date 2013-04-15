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

/* Modes used for get_sdcard_paths */
enum {
    MODE_CRASH = 0,
    MODE_CRASH_NOSD,
    MODE_STATS,
    MODE_APLOGS,
    MODE_BZ
};

#define CACHE_TAIL      0
#define CACHE_START     1

int cache_file(char *filename, char **records, int maxrecords, int cachemode);

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

int find_matching_file(char *dir_to_search, char *pattern, char *filename_found);
int get_value_in_file(char *file, char *keyword, char *value, unsigned int sizemax);
int find_str_in_file(char *file, char *keyword, char *tail);
int find_oneofstrings_in_file(char *file, char **keywords, int nbkeywords);
int readline(int fd, char buffer[MAXLINESIZE]);
int freadline(FILE *fd, char buffer[MAXLINESIZE]);
int append_file(char *filename, char *text);
int overwrite_file(char *filename, char *value);

int do_chmod(char *path, char *mode);
int do_chown(char *file, char *uid, char *gid);
int do_copy_tail(char *src, char *dest, int limit);
int do_copy(char *src, char *dest, int limit);
int rmfr(char *path);

#endif /* __FSUTILS_H__ */
