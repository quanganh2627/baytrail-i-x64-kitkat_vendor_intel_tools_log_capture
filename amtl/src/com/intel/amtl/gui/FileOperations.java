/* Android AMTL
 *
 * Copyright (C) Intel 2015
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Nicolae Natea <nicolaex.natea@intel.com>
 */

package com.intel.amtl.gui;

import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileOperations {

    private static final String TAG = "AMTL";
    private static final String MODULE = "FileOperations";
    public static String TEMP_OUTPUT_FOLDER = "/logs/";

    public static boolean pathExists(String path) {

        File file = new File(path);
        if (null == file || !file.exists()) {
            return false;
        }

        return true;
    }

    public static boolean removeFile(String path) {

        File file = new File(path);
        if (null == file) {
            return false;
        }

        if (!file.exists()) {
            return true;
        }

        if (!file.delete()) {
            return false;
        }
        return true;
    }

    public static void copy(String src, String dst) throws IOException {

        InputStream in = new FileInputStream(src);
        OutputStream out = null;
        byte[] buf = new byte[1024];
        int len;

          try {
            out = new FileOutputStream(dst);
            try {
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.flush();

            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    public static void copyFiles(String source, String destination, final String pattern)
            throws IOException {

        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File directory, String fileName) {
                return fileName.startsWith(pattern);
            }
        };
        File src = new File(source);
        File[] files = src.listFiles(filter);
        if (files != null) {
            for (File copy : files) {
                copy(source + copy.getName(), destination + copy.getName());
            }
        }
    }

    public static void removeFiles(String directory, final String pattern) {

        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File directory, String fileName) {
                return fileName.startsWith(pattern);
            }
        };
        File src = new File(directory);
        File[] files = src.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                removeFile(directory + file.getName());
            }
        }
    }

    public static String getTimeStampedPath(String path, String tag) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HHmmss");
        Date date = new Date();

        if (path == null || path.isEmpty()) {
            path = "";
        }

        if (tag == null || tag.isEmpty()) {
            tag = "";
        }

        path += "/" + tag + dateFormat.format(date);
        return path + "/";
    }

    public static boolean createPath(String path) throws IOException {
        File file = new File(path);

        if ((!file.exists()) && (!file.mkdirs())) {
            return false;
        }

        return true;
    }

    public static String createCrashFolder(String path) throws IOException {
        File file = new File(path);

        if ((!file.exists()) && (!file.mkdirs())) {
            return "";
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HHmmss");
        Date date = new Date();
        path += "/" + dateFormat.format(date);

        file = new File(path);

        if ((!file.exists()) && (!file.mkdirs())) {
            return "";
        }

        return path + "/";
    }

    public static boolean isSdCardAvailable() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    public static String getSDStoragePath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }
}
