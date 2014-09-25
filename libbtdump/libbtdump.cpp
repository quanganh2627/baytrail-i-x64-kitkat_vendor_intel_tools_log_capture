/* Copyright (C) Intel 2014
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

/*
 *Use "indent -npro -kr -i8 -ts8 -sob -l80 -ss -ncs -cp1 " for
 * indentation, (Lindent)
 */

#include "inc/libbtdump.h"

#include <stdlib.h>
#include <limits.h>
#include <dirent.h>
#include <unistd.h>
#include <time.h>
#include <sys/types.h>
#include <sys/ptrace.h>

#include <backtrace/Backtrace.h>
#include <UniquePtr.h>

#define WAIT_RETRY_MAX    10
#define WAIT_RETRY_DELAY  1000

#define MAX_KSTACK_DUMP   512
#define TEMP_BUFF_MAX     128

static int wait_for_stop(pid_t tid)
{
	siginfo_t si;
	int count = 0;
	while (TEMP_FAILURE_RETRY(ptrace(PTRACE_GETSIGINFO, tid, 0, &si)) < 0
	       && errno == ESRCH) {
		if (count > WAIT_RETRY_MAX) {
			return -1;
		}
		usleep(WAIT_RETRY_DELAY);
		count++;
	}
	return 0;
}

static void dump_kernel_stack(int pid, int tid, FILE * output)
{
	char path[PATH_MAX], buffer[MAX_KSTACK_DUMP];
	int data_len;
	FILE *fp;
	snprintf(path, sizeof(path), "/proc/%d/task/%d/stack", pid, tid);
	if ((fp = fopen(path, "r"))) {
		data_len = fread(buffer, 1, MAX_KSTACK_DUMP, fp);
		fwrite(buffer, 1, data_len, output);
		fclose(fp);
	}
}

static void dump_thread_header(int pid, int tid, FILE * output)
{
	char path[PATH_MAX], threadname[TEMP_BUFF_MAX] = { 0, };
	int len;
	FILE *fp;

	snprintf(path, sizeof(path), "/proc/%d/task/%d/comm", pid, tid);
	if ((fp = fopen(path, "r"))) {
		len = fread(threadname, 1, TEMP_BUFF_MAX, fp);
		if (len && threadname[len - 1] == '\n')
			threadname[len - 1] = 0;
		fclose(fp);
	}

	fprintf(output, "\n-- \"%s\" sysTid=%d --\n",
		threadname[0] ? threadname : "<unknown>", tid);
}

static void dump_file_header(FILE * output)
{
	fprintf(output, "*************************************************\n");
	fprintf(output, "* Warning: Kernenlspace stacks are dumped before*\n");
	fprintf(output, "*          attaching to the thread              *\n");
	fprintf(output, "*************************************************\n");
	fprintf(output, "\n");
}

extern "C" int bt_thread(int pid, int tid, FILE * output)
{
	if (!output)
		return -EINVAL;

	if (pid <= 0 || tid <= 0) {
		fprintf(output, "Error: Invalid pid(%d), tid(%d)\n", pid, tid);
		return -EINVAL;
	}

	if (tid == gettid() || pid == getpid()) {
		fprintf(output, "++++ CURRENT PID or TID ++++\n");
		return -EINVAL;
	}

	dump_thread_header(pid, tid, output);
	dump_kernel_stack(pid, tid, output);

	if (ptrace(PTRACE_ATTACH, tid, 0, 0) < 0) {
		fprintf(output, "Info: No userspace stack for %d : %s\n", tid,
			strerror(errno));
		return -EACCES;
	}

	if(wait_for_stop(tid)) {
		fprintf(output, "Attach timeout\n");
		return -EACCES;
	}

	fprintf(output, "userspace\n");
	UniquePtr < Backtrace >
	    backtrace(Backtrace::Create(tid, BACKTRACE_CURRENT_THREAD));

	if (backtrace->Unwind(0)) {
		for (size_t i = 0; i < backtrace.get()->NumFrames(); i++) {
			fprintf(output, "%s\n",
				backtrace->FormatFrameData(i).c_str());
		}
	}

	if (ptrace(PTRACE_DETACH, tid, 0, 0) != 0) {
		fprintf(output, "Error: ptrace detach from %d failed: %s\n",
			tid, strerror(errno));
	}

	return 0;
}

extern "C" int bt_process(int pid, FILE * output)
{
	time_t t = time(NULL);
	struct tm tm;
	localtime_r(&t, &tm);
	char path_buffer[PATH_MAX];
	char temp_buffer[TEMP_BUFF_MAX];
	FILE *fp;
	DIR *d;

	if (!output)
		return -EINVAL;

	if (pid <= 0) {
		fprintf(output, "Error: Invalid pid(%d)\n", pid);
		return -EINVAL;
	}

	if (pid == getpid()) {
		fprintf(output, "++++ CURRENT PID ++++\n");
		return -EINVAL;
	}

	snprintf(path_buffer, sizeof(path_buffer), "/proc/%d", pid);
	d = opendir(path_buffer);

	if (!d) {
		fprintf(output, "Error: Invalid pid, cannot access /proc/%d\n",
			pid);
		return -EACCES;
	} else {
		closedir(d);
	}

	strftime(temp_buffer, TEMP_BUFF_MAX, "%F %T", &tm);
	fprintf(output, "----- pid %d at %s -----\n", pid, temp_buffer);

	temp_buffer[0] = 0;
	snprintf(path_buffer, PATH_MAX, "/proc/%d/cmdline", pid);
	if ((fp = fopen(path_buffer, "r"))) {
		fgets(temp_buffer, TEMP_BUFF_MAX, fp);
		temp_buffer[TEMP_BUFF_MAX - 1] = 0;
		fclose(fp);
	}
	if (temp_buffer[0]) {
		fprintf(output, "Cmd line: %s\n", temp_buffer);
	}

	snprintf(path_buffer, PATH_MAX, "/proc/%d/task", pid);
	d = opendir(path_buffer);

	if (d != NULL) {
		struct dirent *de = NULL;
		while ((de = readdir(d)) != NULL) {
			if (!strcmp(de->d_name, ".")
			    || !strcmp(de->d_name, "..")) {
				continue;
			}

			char *end;
			pid_t new_tid = strtoul(de->d_name, &end, 10);
			if (*end) {
				continue;
			}

			bt_thread(pid, new_tid, output);
		}
		closedir(d);
	}
	fprintf(output, "----- end %d -----\n\n", pid);
	return 0;
}

extern "C" int bt_all(FILE * output)
{
	DIR *dp;
	struct dirent *d_entry;
	int pid = -1;

	if (!output)
		return -EINVAL;

	dump_file_header(output);

	dp = opendir("/proc/");

	if (!dp)
		return -EACCES;
	else
		while ((d_entry = readdir(dp))) {
			if (d_entry->d_name[0] > '0'
			    && d_entry->d_name[0] <= '9') {
				pid = atoi(d_entry->d_name);
				bt_process(pid, output);
			}
		}
	closedir(dp);
	return 0;
}
