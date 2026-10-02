/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2019 Nordix Foundation
 *  Modifications Copyright (C) 2021 Nordix Foundation.
 *  ================================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 *  ============LICENSE_END=========================================================
 */
package org.openecomp.sdc.common.zip;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.openecomp.sdc.common.zip.exception.ZipException;
import org.openecomp.sdc.common.zip.exception.ZipSlipException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles zip operations.
 *
 * <p>Decompression of untrusted archives is bounded to protect against zip bombs. The limits can be tuned with
 * the following system properties: {@value #MAX_ENTRIES_PROPERTY} (maximum number of entries),
 * {@value #MAX_ENTRY_SIZE_PROPERTY} (maximum uncompressed size of a single entry, in bytes),
 * {@value #MAX_TOTAL_SIZE_PROPERTY} (maximum total uncompressed size of all entries, in bytes),
 * {@value #MAX_COMPRESSED_SIZE_PROPERTY} (maximum compressed size of the archive, in bytes) and
 * {@value #MAX_COMPRESSION_RATIO_PROPERTY} (maximum allowed ratio between uncompressed and compressed sizes).
 */
public class ZipUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipUtils.class);
    private static final String COULD_NOT_OBTAIN_CANONICAL_PATH = "Could not obtain canonical path of: '%s'";
    private static final String MAX_ENTRIES_PROPERTY = "sdc.zip.read.maxEntries";
    private static final String MAX_ENTRY_SIZE_PROPERTY = "sdc.zip.read.maxEntrySize";
    private static final String MAX_TOTAL_SIZE_PROPERTY = "sdc.zip.read.maxTotalSize";
    private static final String MAX_COMPRESSED_SIZE_PROPERTY = "sdc.zip.read.maxCompressedSize";
    private static final String MAX_COMPRESSION_RATIO_PROPERTY = "sdc.zip.read.maxCompressionRatio";
    private static final int DEFAULT_MAX_ENTRIES = 10_000;
    private static final long DEFAULT_MAX_ENTRY_SIZE = 1024L * 1024 * 1024;
    private static final long DEFAULT_MAX_TOTAL_SIZE = 2L * 1024 * 1024 * 1024;
    private static final long DEFAULT_MAX_COMPRESSED_SIZE = 1024L * 1024 * 1024;
    private static final double DEFAULT_MAX_COMPRESSION_RATIO = 200d;
    private static final long MIN_INFLATED_SIZE_FOR_RATIO_CHECK = 1024L * 1024;
    private static final int BUFFER_SIZE = 8192;

    private ZipUtils() {
    }

    private static int getMaxEntries() {
        return Integer.getInteger(MAX_ENTRIES_PROPERTY, DEFAULT_MAX_ENTRIES);
    }

    private static long getMaxEntrySize() {
        return Long.getLong(MAX_ENTRY_SIZE_PROPERTY, DEFAULT_MAX_ENTRY_SIZE);
    }

    private static long getMaxTotalSize() {
        return Long.getLong(MAX_TOTAL_SIZE_PROPERTY, DEFAULT_MAX_TOTAL_SIZE);
    }

    /**
     * Maximum compressed size accepted when reading a zip archive, in bytes. The same bound should be applied to the
     * raw upload streams that feed {@link #readZip}.
     *
     * @return the maximum compressed size in bytes
     */
    public static long getMaxCompressedSize() {
        return Long.getLong(MAX_COMPRESSED_SIZE_PROPERTY, DEFAULT_MAX_COMPRESSED_SIZE);
    }

    private static double getMaxCompressionRatio() {
        return Double.parseDouble(System.getProperty(MAX_COMPRESSION_RATIO_PROPERTY, String.valueOf(DEFAULT_MAX_COMPRESSION_RATIO)));
    }

    private static void checkCompressedSize(final long compressedSize) throws ZipException {
        final long maxCompressedSize = getMaxCompressedSize();
        if (compressedSize > maxCompressedSize) {
            throw new ZipException(
                String.format("The compressed size of %d bytes exceeds the maximum allowed size of %d bytes", compressedSize, maxCompressedSize));
        }
    }

    /**
     * Checks if the path is a zip slip attempt calling the {@link #checkForZipSlipInRead(Path)} method.
     *
     * @param zipEntry the zip entry
     * @throws ZipSlipException when a zip slip attempt is detected
     */
    public static void checkForZipSlipInRead(final ZipEntry zipEntry) throws ZipSlipException {
        checkForZipSlipInRead(Paths.get(zipEntry.getName()));
    }

    /**
     * Checks if the path is a zip slip attempt when you don't have a destination folder eg in memory reading or zip creation.
     *
     * @param filePath the file path
     * @throws ZipSlipException when a zip slip attempt is detected
     */
    public static void checkForZipSlipInRead(final Path filePath) throws ZipSlipException {
        validateAndReturnPath(filePath);
        if (filePath.toString().contains("../") || filePath.toString().contains("..\\")) {
            throw new ZipSlipException(filePath.toString());
        }
    }

    private static Path validateAndReturnPath(final Path filePath) throws ZipSlipException {
        final File file = filePath.toFile();
        String canonicalPath;
        try {
            canonicalPath = file.getCanonicalPath();
        } catch (final IOException ex) {
            throw new ZipSlipException(String.format(COULD_NOT_OBTAIN_CANONICAL_PATH, file.getPath()), ex);
        }
        if (canonicalPath != null && !canonicalPath.equals(file.getAbsolutePath())) {
            throw new ZipSlipException(filePath.toString());
        }
        return Path.of(canonicalPath);
    }

    /**
     * Checks if the zip entry is a zip slip attempt based on the destination directory.
     *
     * @param zipEntry            the zip entry
     * @param targetDirectoryPath the target extraction folder
     * @throws ZipException when the zip slip was detected as a {@link ZipSlipException}. Also when there was a problem getting the canonical paths
     *                      from the zip entry or target directory.
     */
    public static void checkForZipSlipInExtraction(final ZipEntry zipEntry, final Path targetDirectoryPath) throws ZipException {
        final File targetDirectoryAsFile = targetDirectoryPath.toFile();
        final File targetFile = new File(targetDirectoryAsFile, zipEntry.getName());
        final String targetDirectoryCanonicalPath;
        try {
            targetDirectoryCanonicalPath = targetDirectoryAsFile.getCanonicalPath();
        } catch (final IOException e) {
            throw new ZipException(String.format(COULD_NOT_OBTAIN_CANONICAL_PATH, targetDirectoryAsFile.getAbsolutePath()), e);
        }
        final String targetFileCanonicalPath;
        try {
            targetFileCanonicalPath = targetFile.getCanonicalPath();
        } catch (final IOException e) {
            throw new ZipException(String.format(COULD_NOT_OBTAIN_CANONICAL_PATH, targetFile.getAbsolutePath()), e);
        }
        if (!targetFileCanonicalPath.startsWith(targetDirectoryCanonicalPath + File.separator)) {
            throw new ZipSlipException(zipEntry.getName());
        }
    }

    /**
     * Creates a ZipInputStream from a byte array.
     *
     * @param zipFileBytes the zip byte array
     * @return the created ZipInputStream.
     */
    private static ZipInputStream getInputStreamFromBytes(final byte[] zipFileBytes) {
        return new ZipInputStream(new ByteArrayInputStream(zipFileBytes));
    }

    /**
     * Reads a zip file into memory. Parses the zipFile in byte array and calls {@link #readZip(byte[], boolean)}.
     *
     * @param zipFile                 the zip file to read
     * @param hasToIncludeDirectories includes or not the directories found during the zip reading
     * @return a Map representing a pair of file path and file byte array
     * @throws ZipException when there was a problem during the reading process
     */
    public static Map<String, byte[]> readZip(final File zipFile, final boolean hasToIncludeDirectories) throws ZipException {
        try {
            checkCompressedSize(Files.size(zipFile.toPath()));
            return readZip(Files.readAllBytes(zipFile.toPath()), hasToIncludeDirectories);
        } catch (final IOException e) {
            throw new ZipException(String.format("Could not read the zip file '%s'", zipFile.getName()), e);
        }
    }

    /**
     * Reads a zip file to a in memory structure formed by the file path and its bytes. The structure can contains only files or files and
     * directories. If configured to include directories, only empty directories and directories that contains files will be included. The full
     * directory tree will not be generated, eg:
     * <pre>
     * \
     * \..\Directory
     * \..\..\ChildDirectory
     * \..\..\..\aFile.txt
     * \..\..\EmptyChildDirectory
     * </pre>
     * The return will include "Directory\ChildDirectory\aFile.txt" and "Directory\EmptyChildDirectory" but not "Directory" or the root.
     *
     * @param zipFileBytes            the zip file byte array to read
     * @param hasToIncludeDirectories includes or not the directories found during the zip reading.
     * @return a Map representing a pair of file path and file byte array
     * @throws ZipException when there was a problem during the reading process
     */
    public static Map<String, byte[]> readZip(final byte[] zipFileBytes, final boolean hasToIncludeDirectories) throws ZipException {
        checkCompressedSize(zipFileBytes.length);
        final Map<String, byte[]> filePathAndByteMap = new HashMap<>();
        try (final ZipInputStream inputZipStream = ZipUtils.getInputStreamFromBytes(zipFileBytes)) {
            readZipEntries(inputZipStream, () -> zipFileBytes.length, filePathAndByteMap, hasToIncludeDirectories);
        } catch (final IOException e) {
            throw new ZipException("Could not read the zip content", e);
        }
        return filePathAndByteMap;
    }

    /**
     * Reads a zip stream to a in memory structure formed by the file path and its bytes, following the same rules of
     * {@link #readZip(byte[], boolean)}. The compressed stream is also bounded: reading aborts once it exceeds
     * {@link #getMaxCompressedSize()}. The given stream is closed before this method returns.
     *
     * @param zipInputStream          the zip stream to read
     * @param hasToIncludeDirectories includes or not the directories found during the zip reading.
     * @return a Map representing a pair of file path and file byte array
     * @throws ZipException when there was a problem during the reading process
     */
    public static Map<String, byte[]> readZip(final InputStream zipInputStream, final boolean hasToIncludeDirectories) throws ZipException {
        final Map<String, byte[]> filePathAndByteMap = new HashMap<>();
        final LimitedInputStream limitedInputStream = new LimitedInputStream(zipInputStream, getMaxCompressedSize());
        try (final ZipInputStream inputZipStream = new ZipInputStream(limitedInputStream)) {
            readZipEntries(inputZipStream, limitedInputStream::getCount, filePathAndByteMap, hasToIncludeDirectories);
        } catch (final IOException e) {
            if (limitedInputStream.isLimitExceeded()) {
                throw new ZipException(
                    String.format("The compressed stream exceeds the maximum allowed size of %d bytes", limitedInputStream.getMaxBytes()), e);
            }
            throw new ZipException("Could not read the zip content", e);
        }
        return filePathAndByteMap;
    }

    private static void readZipEntries(final ZipInputStream inputZipStream, final LongSupplier compressedSizeSupplier,
                                       final Map<String, byte[]> filePathAndByteMap, final boolean hasToIncludeDirectories)
        throws IOException, ZipException {
        final int maxEntries = getMaxEntries();
        final long maxEntrySize = getMaxEntrySize();
        final long maxTotalSize = getMaxTotalSize();
        final double maxCompressionRatio = getMaxCompressionRatio();
        int entryCount = 0;
        long totalInflatedSize = 0;
        ZipEntry zipEntry;
        while ((zipEntry = inputZipStream.getNextEntry()) != null) {
            if (++entryCount > maxEntries) {
                throw new ZipException(String.format("The zip has more than the maximum allowed number of %d entries", maxEntries));
            }
            checkForZipSlipInRead(zipEntry);
            final long entryLimit = Math.min(maxEntrySize, maxTotalSize - totalInflatedSize);
            if (zipEntry.getSize() > entryLimit) {
                throw new ZipException(String.format("The entry '%s' uncompressed size of %d bytes exceeds the maximum allowed size of %d bytes",
                    zipEntry.getName(), zipEntry.getSize(), entryLimit));
            }
            final byte[] entryBytes = getBytes(inputZipStream, zipEntry.getName(), entryLimit);
            totalInflatedSize += entryBytes.length;
            filePathAndByteMap.putAll(processZipEntryInRead(zipEntry, entryBytes, hasToIncludeDirectories));
        }
        if (totalInflatedSize > maxTotalSize) {
            throw new ZipException(String.format("The zip uncompressed size exceeds the maximum allowed size of %d bytes", maxTotalSize));
        }
        if (totalInflatedSize >= MIN_INFLATED_SIZE_FOR_RATIO_CHECK
            && totalInflatedSize > maxCompressionRatio * compressedSizeSupplier.getAsLong()) {
            throw new ZipException(String.format("The zip compression ratio exceeds the maximum allowed of %s", maxCompressionRatio));
        }
    }

    private static Map<String, byte[]> processZipEntryInRead(final ZipEntry zipEntry, final byte[] inputStreamBytes,
                                                             final boolean hasToIncludeDirectories) throws ZipException {
        final Map<String, byte[]> filePathAndByteMap = new HashMap<>();
        checkForZipSlipInRead(zipEntry);
        if (zipEntry.isDirectory()) {
            if (hasToIncludeDirectories) {
                filePathAndByteMap.put(normalizeFolder(zipEntry.getName()), null);
            }
            return filePathAndByteMap;
        }
        if (hasToIncludeDirectories) {
            final Path parentFolderPath = Paths.get(zipEntry.getName()).getParent();
            if (parentFolderPath != null) {
                filePathAndByteMap.putIfAbsent(normalizeFolder(parentFolderPath.toString()), null);
            }
        }
        filePathAndByteMap.put(zipEntry.getName(), inputStreamBytes);
        return filePathAndByteMap;
    }

    /**
     * Adds a {@link File#separator} at the end of the folder path if not present.
     *
     * @param folderPath the folder to normalize
     * @return the normalized folder
     */
    private static String normalizeFolder(final String folderPath) {
        final StringBuilder normalizedFolderBuilder = new StringBuilder(folderPath);
        if (!folderPath.endsWith(File.separator)) {
            normalizedFolderBuilder.append(File.separator);
        }
        return normalizedFolderBuilder.toString();
    }

    /**
     * Converts the current entry of a ZipInputStream to a byte array, aborting once the uncompressed size of the entry
     * exceeds {@code maxEntrySize}.
     *
     * @param inputZipStream the zip input stream
     * @param entryName      the name of the entry being read
     * @param maxEntrySize   the maximum allowed uncompressed size of the entry, in bytes
     * @return the byte array representing the input stream
     * @throws ZipException when there was a problem parsing the input zip stream or the entry exceeds the size limit
     */
    private static byte[] getBytes(final ZipInputStream inputZipStream, final String entryName, final long maxEntrySize) throws ZipException {
        try {
            final ByteArrayOutputStream entryContent = new ByteArrayOutputStream();
            final byte[] buffer = new byte[BUFFER_SIZE];
            long entrySize = 0;
            int bytesRead;
            while ((bytesRead = inputZipStream.read(buffer)) != -1) {
                entrySize += bytesRead;
                if (entrySize > maxEntrySize) {
                    throw new ZipException(String.format("The entry '%s' exceeds the maximum allowed uncompressed size of %d bytes",
                        entryName, maxEntrySize));
                }
                entryContent.write(buffer, 0, bytesRead);
            }
            return entryContent.toByteArray();
        } catch (final IOException e) {
            throw new ZipException("Could not read bytes from file", e);
        }
    }

    /**
     * Counts the bytes read from an input stream and fails once the configured maximum is exceeded.
     */
    private static final class LimitedInputStream extends FilterInputStream {

        private final long maxBytes;
        private long count;
        private boolean limitExceeded;

        private LimitedInputStream(final InputStream inputStream, final long maxBytes) {
            super(inputStream);
            this.maxBytes = maxBytes;
        }

        private long getCount() {
            return count;
        }

        private long getMaxBytes() {
            return maxBytes;
        }

        private boolean isLimitExceeded() {
            return limitExceeded;
        }

        @Override
        public int read() throws IOException {
            final int value = super.read();
            if (value != -1) {
                count++;
                checkLimit();
            }
            return value;
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) throws IOException {
            final int bytesRead = super.read(buffer, offset, length);
            if (bytesRead > 0) {
                count += bytesRead;
                checkLimit();
            }
            return bytesRead;
        }

        private void checkLimit() throws IOException {
            if (count > maxBytes) {
                limitExceeded = true;
                throw new IOException(String.format("The stream exceeds the maximum allowed size of %d bytes", maxBytes));
            }
        }
    }

    /**
     * Unzips a zip file into an output folder.
     *
     * @param zipFilePath  the zip file path
     * @param outputFolder the output folder path
     * @throws ZipException when there was a problem during the unzip process
     */
    public static void unzip(final Path zipFilePath, final Path outputFolder) throws ZipException {
        if (zipFilePath == null || outputFolder == null) {
            return;
        }
        createDirectoryIfNotExists(outputFolder);
        final File zipFile = zipFilePath.toFile();
        checkCompressedSize(zipFile.length());
        try (final FileInputStream fileInputStream = new FileInputStream(zipFile); final ZipInputStream stream = new ZipInputStream(
            fileInputStream)) {
            ZipEntry zipEntry;
            final int maxEntries = getMaxEntries();
            final long maxEntrySize = getMaxEntrySize();
            final long maxTotalSize = getMaxTotalSize();
            int entryCount = 0;
            long totalInflatedSize = 0;
            while ((zipEntry = stream.getNextEntry()) != null) {
                if (++entryCount > maxEntries) {
                    throw new ZipException(String.format("The zip has more than the maximum allowed number of %d entries", maxEntries));
                }
                checkForZipSlipInExtraction(zipEntry, outputFolder);
                final String fileName = zipEntry.getName();
                final Path fileToWritePath = validateAndReturnPath(Paths.get(outputFolder.toString(), fileName));
                if (zipEntry.isDirectory()) {
                    createDirectoryIfNotExists(fileToWritePath);
                } else {
                    final long entryLimit = Math.min(maxEntrySize, maxTotalSize - totalInflatedSize);
                    if (zipEntry.getSize() > entryLimit) {
                        throw new ZipException(String.format("The entry '%s' uncompressed size of %d bytes exceeds the maximum allowed size of %d bytes",
                            zipEntry.getName(), zipEntry.getSize(), entryLimit));
                    }
                    totalInflatedSize += writeFile(stream, fileToWritePath, entryLimit);
                }
            }
            if (totalInflatedSize > maxTotalSize) {
                throw new ZipException(String.format("The zip uncompressed size exceeds the maximum allowed size of %d bytes", maxTotalSize));
            }
            if (totalInflatedSize >= MIN_INFLATED_SIZE_FOR_RATIO_CHECK
                && totalInflatedSize > getMaxCompressionRatio() * zipFile.length()) {
                throw new ZipException(
                    String.format("The zip compression ratio exceeds the maximum allowed of %s", getMaxCompressionRatio()));
            }
        } catch (final FileNotFoundException e) {
            throw new ZipException(String.format("Could not find file: '%s'", zipFile.getAbsolutePath()), e);
        } catch (final IOException e) {
            throw new ZipException(String.format("An unexpected error occurred trying to unzip '%s'", zipFile.getAbsolutePath()), e);
        }
    }

    /**
     * Writes a file from a zipInputStream to a path, bounded to {@code maxEntrySize} uncompressed bytes. Creates the
     * file parent directories if they don't exist.
     *
     * @param zipInputStream  the zip input stream
     * @param fileToWritePath the file path to write
     * @param maxEntrySize    the maximum allowed uncompressed size of the entry, in bytes
     * @return the number of uncompressed bytes written
     * @throws ZipException when there was a problem during the file creation or the entry exceeds the size limit
     */
    private static long writeFile(final ZipInputStream zipInputStream, final Path fileToWritePath, final long maxEntrySize) throws ZipException {
        final Path parentFolderPath = fileToWritePath.getParent();
        if (parentFolderPath != null) {
            try {
                Files.createDirectories(validateAndReturnPath(parentFolderPath));
            } catch (final IOException e) {
                throw new ZipException(String.format("Could not create parent directories of '%s'", fileToWritePath), e);
            }
        }
        long bytesWritten = 0;
        try (final FileOutputStream outputStream = new FileOutputStream(fileToWritePath.toFile())) {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                bytesWritten += bytesRead;
                if (bytesWritten > maxEntrySize) {
                    throw new ZipException(String.format("The entry '%s' exceeds the maximum allowed uncompressed size of %d bytes",
                        fileToWritePath, maxEntrySize));
                }
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (final FileNotFoundException e) {
            throw new ZipException(String.format("Could not find file '%s'", fileToWritePath), e);
        } catch (final IOException e) {
            throw new ZipException(String.format("An unexpected error has occurred while writing file '%s'", fileToWritePath), e);
        }
        return bytesWritten;
    }

    /**
     * Creates the path directories if the provided path does not exists.
     *
     * @param path the path to create directories
     * @throws ZipException when there was a problem to create the directories
     */
    private static void createDirectoryIfNotExists(final Path path) throws ZipException {
        if (path.toFile().exists()) {
            return;
        }
        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            throw new ZipException(String.format("Could not create directories for path '%s'", path), e);
        }
    }

    /**
     * Zips a directory and its children content.
     *
     * @param fromPath      the directory path to zip
     * @param toZipFilePath the path to the zip file that will be created
     * @throws ZipException when there was a problem during the zip process
     */
    public static void createZipFromPath(final Path fromPath, final Path toZipFilePath) throws ZipException {
        final Path createdZipFilePath;
        try {
            createdZipFilePath = Files.createFile(toZipFilePath);
        } catch (final IOException e) {
            throw new ZipException(String.format("Could not create file '%s'", toZipFilePath), e);
        }
        try (final FileOutputStream fileOutputStream = new FileOutputStream(
            createdZipFilePath.toFile()); final BufferedOutputStream bos = new BufferedOutputStream(
            fileOutputStream); final ZipOutputStream zipOut = new ZipOutputStream(bos); final Stream<Path> walkStream = Files.walk(fromPath)) {
            final Set<Path> allFilesSet = walkStream.collect(Collectors.toSet());
            for (final Path path : allFilesSet) {
                checkForZipSlipInRead(path);
                if (path.equals(fromPath)) {
                    continue;
                }
                final Path relativePath = fromPath.relativize(path);
                final File file = path.toFile();
                if (file.isDirectory()) {
                    zipOut.putNextEntry(new ZipEntry(relativePath + File.separator));
                } else {
                    zipOut.putNextEntry(new ZipEntry(relativePath.toString()));
                    zipOut.write(Files.readAllBytes(path));
                }
                zipOut.closeEntry();
            }
        } catch (final FileNotFoundException e) {
            throw new ZipException(String.format("Could not create file '%s'", toZipFilePath), e);
        } catch (final IOException e) {
            throw new ZipException("An error has occurred while creating the zip package", e);
        }
    }
}
