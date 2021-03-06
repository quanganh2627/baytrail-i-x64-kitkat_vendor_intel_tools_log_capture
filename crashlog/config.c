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
 * @file config.c
 * @brief File containing functions used to handle config(s) for crashlogd.
 *
 * This file contains the functions used to load and handles config(s)
 * for crashlogd.
 * A config is loaded from a *.conf file and is made of sections and each section
 * composed of a list of key-value couples.
 * A config is then used to configure crashlogd behavior.
 * One or several configs can be loaded.
 */

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>
#include "config.h"
#define LOG_TAG "CRASHCONFIG"
#include <cutils/log.h>

#define MAXLEN 127

/*
* Name          : config_trim
* Description   : This function remove tabs/spaces/lf/cr at end or start of the pchar
* Parameters    :
*   pchar s        -> pchar to trim
*/
static void config_trim(pchar s)
{
    /* start */
    size_t i=0,j;
    while(isspace(s[i])){
        i++;
    }
    if (i>0) {
        for( j=0; j < strlen(s);j++) {
            if ( j< strlen(s) - i ){
                s[j]=s[j+i];
            }else{
                //padding with \0 the end of string
                s[j]='\0';
            }
        }
    }

    /* end */
    i=strlen(s)-1;
    while(isspace(s[i])) {
        i--;
    }
    if ( i < (strlen(s)-1)) {
        s[i+1]='\0';
    }
}
/*
* Name          : add_section
* Description   : This function create a new section item
* Parameters    :
*   pchar config        -> pchar corresponds to the name of the new section
*/
static void add_section(pchar config, pconfig_handle  conf_handle) {
    psection newsect = malloc(sizeof(struct section));
    if(!newsect) {
        LOGE("%s:malloc failed\n", __FUNCTION__);
        return;
    }
    if (conf_handle->first == NULL){
    // start the chain off
        conf_handle->first = newsect;
    }else{
    // add on the end of the last section
        conf_handle->current->next = newsect;
     }
    conf_handle->current = newsect;
    newsect->name = malloc(strlen(config));
    if(!newsect->name) {
        if(newsect) {
            free(newsect);
        }
        LOGE("%s:malloc failed\n", __FUNCTION__);
        return;
    }
    strncpy(newsect->name,config+1,strlen(config)-1); /*+1 for removing [ char */
    newsect->name[strlen(config)-2]= '\0';
    newsect->kvlist = NULL;
    newsect->next   = NULL;
}

/*
* Name          : add_kv_pair
* Description   : This function create a new key/value item and add it to the list
* Parameters    :
*   pchar config        -> pchar corresponds to the line containg the key/value couple
*/
static int add_kv_pair(pchar config,pconfig_handle  conf_handle) {
    pchar key= NULL;
    pchar value = NULL;
    pkv   newkv = NULL;
    pkv   lastkv;
    size_t valuelen;
    size_t p=0;
    int iFound=-1;

    if (conf_handle->current==NULL){
    //Found key=value before a section was defined => line ignored
        return 0;
    }
    //searching "=" separator
    for (p=0; (p < strlen(config)) ;p++) {
        if (config[p]=='=' )
        {
            iFound=0;
            break;
        }
    }
    if (iFound==-1) {
        return 0; /*  No = in key = value => line ignored */
    }

    newkv = malloc(sizeof(struct kv));
    if(!newkv) {
        LOGE("%s: newkv malloc failed\n", __FUNCTION__);
        return 0;
    }
    key=malloc(p+1);
    if(!key) {
        if (newkv){
            free(newkv);
        }
        LOGE("%s: key malloc failed\n", __FUNCTION__);
        return 0;
    }
    strncpy(key,config,p);
    key[p]='\0';

    valuelen = strlen(config)-p-1;
    value= malloc(valuelen+1); /* add 1 for \0 */
    if(!value) {
        if (newkv){
            free(newkv);
        }
        if (key){
            free(key);
        }
        LOGE("%s: key value malloc failed\n", __FUNCTION__);
        return 0;
    }
    strncpy(value,config+p+1,valuelen );
    value[valuelen]='\0';

    newkv->key = key;
    newkv->value = value;
    newkv->next = NULL;

    if (conf_handle->current->kvlist == NULL){
    // no key/values in this section yet
        conf_handle->current->kvlist = newkv;
    }
    else {
        lastkv= conf_handle->current->kvlist;
        //iterate until the end
        while ((lastkv->next ) != NULL){
            lastkv=lastkv->next;
        }
        lastkv->next = newkv;
    }
    return 1;
}

/*
* Name          : generate_section_kv
* Description   : This function generate memory structure for section and key value
*                 depending of the string configline
* Parameters    :
*   pchar config_line        -> pchar corresponds to the configline to process
*/
static void generate_section_kv(pchar  config_line, pconfig_handle  conf_handle){
    size_t i;
    i=strlen(config_line)-1;
    if (strlen(config_line)== 0){
    // Ignore empty lines
        return;
    }
    if (config_line[0]==';'){
    // ignore  ; - they are comments
        return;
    }
    if (config_line[0]=='#'){
    // ignore  # - they are comments
        return;
    }
    if ((config_line[0]=='[') && (config_line[i] == ']')){
        add_section(config_line, conf_handle);
    }else{
        add_kv_pair(config_line, conf_handle);
    }
}




/*
* Name          : find_section
* Description   : This function searches through all sections for matching sectionname
* Parameters    :
*   pchar sectionname        -> pchar corresponds to the section searched
*/
static psection find_section(pchar section_name, pconfig_handle  conf_handle) {
    psection result = NULL;
    //to avoid useless search if already on the good section
    if (conf_handle->current){
        if (strcmp(conf_handle->current->name,section_name)==0){
            return conf_handle->current;
        }
    }
    conf_handle->current = conf_handle->first;
    while (conf_handle->current) {
        if (strcmp(conf_handle->current->name,section_name)==0){
            result=conf_handle->current;
            break;
        }
        conf_handle->current = conf_handle->current->next;
    }
    return result;
}


pchar get_value (pchar section, pchar name, pconfig_handle  conf_handle) {
    pchar result = NULL;
    config_trim(section);
    conf_handle->current = find_section(section,conf_handle);
    if (!conf_handle->current){
        //section not found
        return NULL;
    }
    pkv   currkv = conf_handle->current->kvlist;
    while (currkv) {
        if ((strcmp(name,currkv->key)== 0 ))
        {
            result = currkv->value;
            break;
        }
        currkv = currkv->next;
    }
    return result;
}


pchar get_value_def (pchar section, pchar name, pchar def_value, pconfig_handle  conf_handle) {
    pchar result = get_value(section,name,conf_handle);
    if (result){
        return result;
    }
    // returning def value
    return def_value;
}


int sk_exists(pchar section,pchar name, pconfig_handle  conf_handle) {
    int result = 0;
    config_trim(section);

    if (get_value(section,name,conf_handle)){
        result =1;
    }
    return result;
}


int init_config_file(pchar  filename, pconfig_handle  conf_handle) {
    LOGD("init_config_file start");
    FILE * f;
    char buff[MAXLEN];
    f = fopen(filename,"rt");
    if (f==NULL){
        //file could not be found
        conf_handle->first = NULL;
        conf_handle->current = NULL;
        return -1;
    }
    LOGD("file opened");
    if (conf_handle->first){
        LOGD("default free");
        //memory protection - clean previous config file if free has not been called
        free_config_file(conf_handle);
    }
    LOGD("before while");
    conf_handle->first = NULL;
    while (!feof(f)) {
        if (fgets(buff,MAXLEN-2,f)==NULL){
            break;
        }
        config_trim(buff);
        generate_section_kv(buff,conf_handle);
    }
    fclose(f);
    return 0;
}


void free_config_file(pconfig_handle  conf_handle)
{
    pkv   currentkv;
    pkv      nextkv;
    psection nextsection;
    psection local_current;
    local_current = conf_handle->first;
    while (local_current){
        currentkv = local_current->kvlist;
// Free up the list of kv pairs in current section
        while (currentkv){
            nextkv = currentkv->next;
            free(currentkv->value);
            free(currentkv->key);
            free(currentkv);
            currentkv = nextkv;
        }
// Now free current section
        nextsection = local_current->next;
        free(local_current->name);
        free(local_current);
        local_current = nextsection;
    }
    conf_handle->first=NULL;
    conf_handle->current=NULL;
}

/*
* Name          : find_base_section
* Description   : This function searches through all sections for matching sectionname
* Parameters    :
*   pchar sectionname        -> pchar corresponds to the section searched
*   int find_next            -> indicates if find should switch to next section first
*/
static psection find_base_section(pchar base_section, pconfig_handle  conf_handle, int find_next) {
    psection result = NULL;
    if ((find_next ==1)&&(conf_handle->current)){
        conf_handle->current = conf_handle->current->next;
    }
    //generate test pattern : expected pattern is [section_name "label"]
    char test_section[MAXLEN];
    snprintf(test_section,sizeof(test_section),"%s \"",base_section);
    while (conf_handle->current) {
        if (strncmp( conf_handle->current->name,test_section,strlen(test_section))==0){
            result=conf_handle->current;
            break;
        }
        conf_handle->current = conf_handle->current->next;
    }
    return result;
}

pchar get_first_section_name (pchar base_section, pconfig_handle  conf_handle) {
    pchar result = NULL;
    psection tmp_section;

    config_trim(base_section);
    conf_handle->current = conf_handle->first;
    tmp_section = find_base_section(base_section,conf_handle,0);
    if (tmp_section){
        result = tmp_section->name;
    }
    return result;
}

pchar get_next_section_name (pchar base_section, pconfig_handle  conf_handle) {
    pchar result = NULL;
    psection tmp_section;

    config_trim(base_section);
    tmp_section = find_base_section(base_section,conf_handle,1);
    if (tmp_section){
        result = tmp_section->name;
    }
    return result;
}
