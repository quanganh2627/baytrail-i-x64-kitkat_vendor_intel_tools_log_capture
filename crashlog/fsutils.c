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
#ifndef __TEST__
#include <private/android_filesystem_config.h>
#endif

/* No header in bionic... */
ssize_t sendfile(int out_fd, int in_fd, off_t *offset, size_t count);

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

int find_str_in_file(char *filename, char *keyword, char *tail) {
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
        //printf("Compare the tail of %s to %s\n", buffer, tail);
        if ( !strncmp(&buffer[linesize - taillen], tail, taillen) ) break;
    }

    close(fd);
    return (linesize > 0 ? 1 : 0);
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
    if ((!strncmp(value, "lowmemory", 9)) || (mode == MODE_CRASH_NOSD))
        return 0;

    errno = 0;
    if (!file_exists(SDCARD_LOGS_DIR))
        mkdir(SDCARD_LOGS_DIR, 0777);

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
        default:
            LOGE("%s: Invalid mode %d\n", __FUNCTION__, mode);
            return -1;
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

int do_chown(char *file, char *uid, char *gid)
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

int read_file_prop_uid(char* propsource, char *filename, char *uid, char* defaultvalue) {
    FILE *fd;
    int res;

    if ( !propsource || !filename || !uid || !defaultvalue )
        return -EINVAL;

    /*
     * Get the property first as it seems to be the priority
     * in the original algorithm
     */
    res = property_get(propsource, uid, NULL);
    if (res > 0) return 0;

    fd = fopen(filename, "w+");
    if (fd == NULL) {
        if (res < 0) {
            strncpy(uid, defaultvalue, PROPERTY_VALUE_MAX);
        }
        return 0;
    }

    if (fscanf(fd, "%s", uid) == 1) {
        /* Got the value in the file */
        fclose(fd);
        return 0;
    }

    /* Didn't get the property, nor the file but the file exists
     * so, write the default value there... */
    strncpy(uid, defaultvalue, PROPERTY_VALUE_MAX);
    fprintf(fd, "%s", defaultvalue);
    do_chown(filename, PERM_USER, PERM_GROUP);
    fclose(fd);
    return 0;
}

#ifndef FULL_REPORT
static void flush_aplog()
{
    struct stat info;
    int status;

    if(stat(APLOG_FILE_0, &info) == 0){
        remove(APLOG_FILE_0);
    }
    status = system("/system/bin/logcat -b system -b main -b radio -b events -v threadtime -d -f /logs/aplog");
    if (status != 0)
        LOGE("dump logcat returns status: %d.\n", status);
    do_chown(APLOG_FILE_0, PERM_USER, PERM_GROUP);
}
#endif

void do_log_copy(char *mode, int dir, const char* timestamp, int type) {
    char destination[PATHMAX], *logfile0, *logfile1, *extension;
    struct stat info;
    char *dir_pattern = CRASH_DIR;

    switch (type) {
        case APLOG_TYPE:
        case APLOG_STATS_TYPE:
#ifndef FULL_REPORT
            flush_aplog();
#endif
            logfile0 = APLOG_FILE_0;
            logfile1 = APLOG_FILE_1;
            extension = "";
            if (type == APLOG_STATS_TYPE)
                dir_pattern = STATS_DIR;
            break;
        case BPLOG_TYPE:
            logfile0 = BPLOG_FILE_0;
            logfile1 = BPLOG_FILE_1;
            extension = ".istp";
            break;
        default:
            /* Ignore unknown type, just return */
            return;
    }
    if(stat(logfile0, &info) == 0) {
        snprintf(destination,sizeof(destination), "%s%d/%s_%s_%s%s", dir_pattern, dir, strrchr(logfile0,'/')+1, mode, timestamp, extension);
        do_copy_tail(logfile0, destination, MAXFILESIZE);
        if(info.st_size < 1*MB) {
            if(stat(logfile1, &info) == 0) {
                snprintf(destination,sizeof(destination), "%s%d/%s_%s_%s%s", dir_pattern, dir, strrchr(logfile1,'/')+1, mode, timestamp, extension);
                do_copy_tail(logfile1, destination, MAXFILESIZE);
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
