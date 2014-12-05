#!/system/bin/sh
#
#
# Copyright (C) Intel 2014
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

start_log() {
    local logprop=$1
    local logservice=$2

    #need to check vold status for encryption case
    vold=$(getprop vold.post_fs_data_done)
    if [ "$vold" != "1" ]; then
        return
    fi
    logstatus=$(getprop $logprop)
    if [ "$logstatus" = "1" ]; then
        start $logservice
        exit
    fi
}

start_log persist.service.aplogfs.enable ap_logfs
start_log persist.service.apklogfs.enable apk_logfs