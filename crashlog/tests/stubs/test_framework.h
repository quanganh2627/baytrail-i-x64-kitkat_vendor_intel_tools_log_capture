#ifndef __TEST_FMWK_H__
#define __TEST_FMWK_H__

#include <stdlib.h>

char buildVersion[PROPERTY_VALUE_MAX] = "la tete a toto";
char uuid[256] = "la tete a titi";

char *CRASH_DIR = NULL;
char *STATS_DIR = NULL;
char *APLOGS_DIR = NULL;
char *BZ_DIR = NULL;
int gmaxfiles = MAX_DIRS;

#endif /* __TEST_FMWK_H__ */
