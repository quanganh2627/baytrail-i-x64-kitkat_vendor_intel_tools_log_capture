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

#ifndef MMGR_SOURCE_H_INCLUDED
#define MMGR_SOURCE_H_INCLUDED

#include <stdlib.h>
#include "mmgr_cli.h"

#define MMGRMAXDATA 512

struct mmgr_data {
    char string[20]; //main string representing mmgr data content
    int  extra_int; // optional integer data (mailly used for error code)
    char extra_string[MMGRMAXDATA]; // optional string that could be used for any purpose
};

int mmgr_get_fd();
void init_mmgr_cli_source(void);
void close_mmgr_cli_source(void);

#endif