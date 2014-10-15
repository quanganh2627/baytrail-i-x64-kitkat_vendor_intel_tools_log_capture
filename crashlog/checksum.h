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

/**
 * @file checksum.h
 * @brief File containing basic functions to calculate a checksum on a
 * buffer, directory or a file.
 */

#ifndef __CHECKSUM_H__
#define __CHECKSUM_H__
#include <openssl/sha.h>

#define CRASHLOG_CHECKSUM_SIZE SHA_DIGEST_LENGTH

/**
 * Computes a checksum on a buffer passed as parameter
 *
 * @param buffer indicates a buffer for which we want to compute the checksum
 * @param size in bytes of the buffer on which we wish to compute the checksum
 * @param result represents the buffer through which the computed checksum is returned
 * @return 0 on succes, -errno on errors
 */
int calculate_checksum_buffer(const char *buffer, ssize_t size, unsigned char *result);

/**
 * Computes a checksum on a directory passed as parameter
 *
 * @param path indicates the parent directory for which we want to compute the checksum
 * @param result represents the buffer through which the computed checksum is returned
 * @return 0 on succes, -1 otherwise
 */
int calculate_checksum_directory(const char *path, unsigned char *result);

/**
 * Computes a checksum on a file passed as parameter
 *
 * @param filename indicates the file for which we want to compute the checksum
 * @param result represents the buffer through which the computed checksum is returned
 * @return 0 on succes, -errno on errors
 */
int calculate_checksum_file(const char *filename, unsigned char *result);

#endif /* __CHECKSUM_H__ */
