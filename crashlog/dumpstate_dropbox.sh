#!/system/bin/sh

# Do not allow bugreports on user builds unless USB debugging
# is enabled.
if [ "x$(getprop ro.build.type)" = "xuser" -a \
     "x$(getprop init.svc.adbd)" != "xrunning" ]; then
  exit 0
fi

storagePath=`getprop crashlogd.storage.path`
# Wait 5 seconds, monkey may run procrank and librank after ANR crash
sleep 5
timestamp=`date +'%Y-%m-%d-%H-%M-%S'`
dumpstate=$storagePath/dumpstate-$timestamp
dropbox=$storagePath/dropbox-$timestamp.txt

# run dumpstate and dropbox
/system/bin/dumpstate -o $dumpstate $@
# dumpstate cost about 30 seconds
sleep 35
/system/bin/dumpsys dropbox --print > $dropbox
sleep 1

# make files readable
if [ -f "/system/xbin/wc" ] ; then
  ispathinsdcard=`echo $storagePath | grep "mnt.*sdcard" | wc -l`
else
  ispathinsdcard=0
fi

if [ $ispathinsdcard == 0 ] ; then
  chown system.log $dumpstate.txt
  chown system.log $dropbox
else
  chown root.sdcard_rw $dumpstate.txt
  chown root.sdcard_rw $dropbox
fi
