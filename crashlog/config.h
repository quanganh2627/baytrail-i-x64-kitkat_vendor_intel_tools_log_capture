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
 * @file config.h
 * @brief File containing definitions of functions used to handle config(s)
 * for crashlogd.
 *
 * This file contains definitions of functions used to load and handles config(s)
 * for crashlogd.
 * A config is loaded from a *.conf file and is made of sections and each section
 * composed of a list of key-value couples.
 * A config is then used to configure crashlogd behavior.
 * One or several configs can be loaded.
 */

typedef char * pchar;
typedef struct kv * pkv;
typedef struct section * psection;
typedef struct config_handle * pconfig_handle;


struct kv {
    pchar key;
    pchar value;
    pkv   next;
};

struct section {
    pchar   name;    /* section name eg [config] for "config" section */
    pkv   kvlist;  /* list of key value (kv) pairs */
    psection next;
};

struct config_handle {
    psection first;
    psection current;
};

/*
* Name          : get_value
* Description   : This function get value depending of the section
* Parameters    :
*   pchar section     -> pchar section corresponds to the section of the key searched
*   pchar name        -> pchar name corresponds to the name of the key searched
*   pconfig_handle  conf_handle -> handle where is stored the configuration
*/
pchar get_value (pchar section, pchar name, pconfig_handle  conf_handle);

/*
* Name          : get_value_def
* Description   : This function get value depending of the section  and return default value if not found
* Parameters    :
*   pchar section     -> pchar section corresponds to the section of the key searched
*   pchar name        -> pchar name corresponds to the name of the key searched
*   pchar def_value         -> pchar def_value corresponds to default value to return
*   pconfig_handle  conf_handle -> handle where is stored the configuration
*/
pchar get_value_def (pchar section, pchar name, pchar def_value, pconfig_handle  conf_handle);

/*
* Name          : sk_exists
* Description   : This function check if section/key exists (and update current section if found)
* Parameters    :
*   pchar section     -> pchar section corresponds to the section searched
*   pchar name        -> pchar corresponds to the key searched
*   pconfig_handle  conf_handle -> handle where is stored the configuration
*/
int sk_exists(pchar section,pchar name,pconfig_handle  conf_handle);

/*
* Name          : init_config_file
* Description   : This function load a configuration in a handle
* Parameters    :
*   pchar filename     -> pchar filename corresponds to the file to load as current config_file
*   pconfig_handle  conf_handle -> handle where is stored the configuration
*/
int init_config_file(pchar  filename, pconfig_handle  conf_handle);

/*
* Name          : free_config
* Description   : This function free the config file section handle
* Parameters    :
*   pconfig_handle  conf_handle -> handle to free
*/
void free_config_file(pconfig_handle  conf_handle);

/*
* Name          : get_first_section_name
* Description   : This function return the first section corresponding to the specified base_section
* Parameters    :
*   pchar base_section -> pattern searched
*   pconfig_handle  conf_handle -> handle to search section
*/
pchar get_first_section_name (pchar base_section, pconfig_handle  conf_handle);

/*
* Name          : get_next_section_name
* Description   : This function return the next section corresponding to the specified base_section
* Parameters    :
*   pchar base_section -> pattern searched
*   pconfig_handle  conf_handle -> handle to search section
*/
pchar get_next_section_name (pchar base_section, pconfig_handle  conf_handle);


