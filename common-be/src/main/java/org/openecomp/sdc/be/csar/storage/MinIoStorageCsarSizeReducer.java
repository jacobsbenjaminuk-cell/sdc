/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2021 Nordix Foundation
 *  ================================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 *  ============LICENSE_END=========================================================
 */

package org.openecomp.sdc.be.csar.storage;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.openecomp.sdc.be.csar.storage.exception.CsarSizeReducerException;
import org.openecomp.sdc.common.CommonConfigurationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinIoStorageCsarSizeReducer implements PackageSizeReducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinIoStorageCsarSizeReducer.class);
    private static final Set<String> ALLOWED_SIGNATURE_EXTENSIONS = Set.of("cms");
    private static final Set<String> ALLOWED_CERTIFICATE_EXTENSIONS = Set.of("cert", "crt");
    private static final String CSAR_EXTENSION = "csar";
    private static final String UNEXPECTED_PROBLEM_HAPPENED_WHILE_READING_THE_CSAR = "An unexpected problem happened while reading the CSAR '%s'";
    private static final String EXTERNAL_CSAR_STORE = "externalCsarStore";
    private static final int DEFAULT_MAX_UNCOMPRESSED_SIZE = 1073741824;
    private static final int BUFFER_SIZE = 8192;

    @Getter
    private final AtomicBoolean reduced = new AtomicBoolean(false);

    private final CsarPackageReducerConfiguration configuration;

    public MinIoStorageCsarSizeReducer() {
        this.configuration = readPackageReducerConfiguration();
    }

    MinIoStorageCsarSizeReducer(final CsarPackageReducerConfiguration configuration) {
        this.configuration = configuration;
    }

    private CsarPackageReducerConfiguration readPackageReducerConfiguration() {
        final var commonConfigurationManager = CommonConfigurationManager.getInstance();
        final List<String> foldersToStrip = commonConfigurationManager.getConfigValue(EXTERNAL_CSAR_STORE, "foldersToStrip", new ArrayList<>());
        final int sizeLimit = commonConfigurationManager.getConfigValue(EXTERNAL_CSAR_STORE, "sizeLimit", 1000000);
        final int thresholdEntries = commonConfigurationManager.getConfigValue(EXTERNAL_CSAR_STORE, "thresholdEntries", 10000);
        final Number maxUncompressedSize =
            commonConfigurationManager.getConfigValue(EXTERNAL_CSAR_STORE, "maxUncompressedSize", DEFAULT_MAX_UNCOMPRESSED_SIZE);
        LOGGER.info("Folders to strip: '{}'", String.join(", ", foldersToStrip));
        final Set<Path> foldersToStripPathSet = foldersToStrip.stream().map(Path::of).collect(Collectors.toSet());
        return new CsarPackageReducerConfiguration(foldersToStripPathSet, sizeLimit, thresholdEntries, maxUncompressedSize.longValue());
    }

    @Override
    public byte[] reduce(final Path csarPackagePath) {
        final var uncompressedBytesBudget = new AtomicLong(configuration.getMaxUncompressedSize());
        if (hasSignedPackageStructure(csarPackagePath)) {
            return reduce(csarPackagePath, this::signedZipProcessingConsumer, uncompressedBytesBudget);
        } else {
            return reduce(csarPackagePath, this::unsignedZipProcessingConsumer, uncompressedBytesBudget);
        }
    }

    private byte[] reduce(final Path csarPackagePath, final ZipProcessFunction zipProcessingFunction, final AtomicLong uncompressedBytesBudget) {
        final var reducedCsarPath = Path.of(csarPackagePath + "." + UUID.randomUUID());

        try (final var zf = new ZipFile(csarPackagePath.toString());
            final var zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(reducedCsarPath)))) {
            zf.entries().asIterator()
                .forEachRemaining(zipProcessingFunction.getProcessZipConsumer(csarPackagePath, zf, zos, uncompressedBytesBudget));
        } catch (final IOException | RuntimeException ex1) {
            rollback(reducedCsarPath);
            if (ex1 instanceof CsarSizeReducerException) {
                throw (CsarSizeReducerException) ex1;
            }
            LOGGER.error("Could not read ZIP stream '{}'", csarPackagePath, ex1);
            final var errorMsg = String.format(UNEXPECTED_PROBLEM_HAPPENED_WHILE_READING_THE_CSAR, csarPackagePath);
            throw new CsarSizeReducerException(errorMsg, ex1);
        }
        final byte[] reducedCsarBytes;
        try {
            if (reduced.get()) {
                reducedCsarBytes = Files.readAllBytes(reducedCsarPath);
            } else {
                reducedCsarBytes = Files.readAllBytes(csarPackagePath);
            }
        } catch (final IOException e) {
            LOGGER.error("Could not read bytes of file '{}'", csarPackagePath, e);
            final var errorMsg = String.format("Could not read bytes of file '%s'", csarPackagePath);
            throw new CsarSizeReducerException(errorMsg, e);
        }
        try {
            Files.delete(reducedCsarPath);
        } catch (final IOException e) {
            LOGGER.error("Could not delete temporary file '{}'", reducedCsarPath, e);
            final var errorMsg = String.format("Could not delete temporary file '%s'", reducedCsarPath);
            throw new CsarSizeReducerException(errorMsg, e);
        }

        return reducedCsarBytes;
    }

    private Consumer<ZipEntry> signedZipProcessingConsumer(final Path csarPackagePath, final ZipFile zf, final ZipOutputStream zos,
                                                           final AtomicLong uncompressedBytesBudget) {
        final var thresholdEntries = configuration.getThresholdEntries();
        final var totalEntryArchive = new AtomicInteger(0);
        return zipEntry -> {
            final var entryName = zipEntry.getName();
            try {
                if (totalEntryArchive.getAndIncrement() > thresholdEntries) {
                    LOGGER.warn("too many entries in this archive, can lead to inodes exhaustion of the system");
                    // too many entries in this archive, can lead to inodes exhaustion of the system
                    final var errorMsg = String.format("Failed to extract '%s' from zip '%s'", entryName, csarPackagePath);
                    throw new CsarSizeReducerException(errorMsg);
                }
                zos.putNextEntry(new ZipEntry(entryName));
                if (!zipEntry.isDirectory()) {
                    if (entryName.toLowerCase().endsWith(CSAR_EXTENSION)) {
                        final var internalCsarExtractPath = Path.of(csarPackagePath + "." + UUID.randomUUID());
                        try {
                            try (final InputStream entryInputStream = zf.getInputStream(zipEntry);
                                final OutputStream extractOutputStream = Files.newOutputStream(internalCsarExtractPath)) {
                                copyAtMost(entryInputStream, extractOutputStream, Long.MAX_VALUE, uncompressedBytesBudget, csarPackagePath);
                            }
                            zos.write(reduce(internalCsarExtractPath, this::unsignedZipProcessingConsumer, uncompressedBytesBudget));
                        } finally {
                            Files.deleteIfExists(internalCsarExtractPath);
                        }
                    } else {
                        try (final InputStream entryInputStream = zf.getInputStream(zipEntry)) {
                            copyAtMost(entryInputStream, zos, Long.MAX_VALUE, uncompressedBytesBudget, csarPackagePath);
                        }
                    }
                }
                zos.closeEntry();
            } catch (final IOException ei) {
                LOGGER.error("Failed to extract '{}' from zip '{}'", entryName, csarPackagePath, ei);
                final var errorMsg = String.format("Failed to extract '%s' from zip '%s'", entryName, csarPackagePath);
                throw new CsarSizeReducerException(errorMsg, ei);
            }
        };
    }

    private Consumer<ZipEntry> unsignedZipProcessingConsumer(final Path csarPackagePath, final ZipFile zf, final ZipOutputStream zos,
                                                             final AtomicLong uncompressedBytesBudget) {
        final var thresholdEntries = configuration.getThresholdEntries();
        final var totalEntryArchive = new AtomicInteger(0);
        return zipEntry -> {
            final var entryName = zipEntry.getName();
            if (totalEntryArchive.getAndIncrement() > thresholdEntries) {
                LOGGER.warn("too many entries in this archive, can lead to inodes exhaustion of the system");
                // too many entries in this archive, can lead to inodes exhaustion of the system
                final var errorMsg = String.format("Failed to extract '%s' from zip '%s'", entryName, csarPackagePath);
                throw new CsarSizeReducerException(errorMsg);
            }
            try {
                zos.putNextEntry(new ZipEntry(entryName));
                if (!zipEntry.isDirectory()) {
                    if (isCandidateToRemove(zipEntry)) {
                        // replace with EMPTY string to avoid package description inconsistency/validation errors
                        zos.write("".getBytes());
                        reduced.set(true);
                    } else {
                        final var entryBytes = new ByteArrayOutputStream();
                        final boolean isWithinSizeLimit;
                        try (final InputStream entryInputStream = zf.getInputStream(zipEntry)) {
                            isWithinSizeLimit = copyAtMost(entryInputStream, entryBytes, configuration.getSizeLimit(), uncompressedBytesBudget,
                                csarPackagePath);
                        }
                        if (isWithinSizeLimit) {
                            entryBytes.writeTo(zos);
                        } else {
                            zos.write("".getBytes());
                            reduced.set(true);
                        }
                    }
                }
                zos.closeEntry();
            } catch (final IOException ei) {
                LOGGER.error("Failed to extract '{}' from zip '{}'", entryName, csarPackagePath, ei);
                final var errorMsg = String.format("Failed to extract '%s' from zip '%s'", entryName, csarPackagePath);
                throw new CsarSizeReducerException(errorMsg, ei);
            }
        };
    }

    /**
     * Copies at most {@code limit} bytes, charging every byte read to {@code uncompressedBytesBudget}.
     *
     * @return {@code true} if the whole stream was copied, {@code false} if it holds more than {@code limit} bytes
     * @throws CsarSizeReducerException if the budget is exhausted
     */
    private boolean copyAtMost(final InputStream inputStream, final OutputStream outputStream, final long limit,
                               final AtomicLong uncompressedBytesBudget, final Path csarPackagePath) throws IOException {
        final var buffer = new byte[BUFFER_SIZE];
        long copied = 0;
        int read;
        while ((read = inputStream.read(buffer, 0, nextReadLength(buffer.length, limit - copied))) != -1) {
            if (uncompressedBytesBudget.addAndGet(-read) < 0) {
                final var errorMsg = String.format("The uncompressed content of the CSAR '%s' exceeds the limit of %d bytes", csarPackagePath,
                    configuration.getMaxUncompressedSize());
                LOGGER.warn(errorMsg);
                throw new CsarSizeReducerException(errorMsg);
            }
            copied += read;
            if (copied > limit) {
                return false;
            }
            outputStream.write(buffer, 0, read);
        }
        return true;
    }

    private static int nextReadLength(final int bufferLength, final long bytesLeftWithinLimit) {
        return bytesLeftWithinLimit < bufferLength ? (int) bytesLeftWithinLimit + 1 : bufferLength;
    }

    private void rollback(final Path reducedCsarPath) {
        if (Files.exists(reducedCsarPath)) {
            try {
                Files.delete(reducedCsarPath);
            } catch (final Exception e) {
                LOGGER.warn("Could not delete temporary file '{}'", reducedCsarPath, e);
            }
        }
    }

    private boolean isCandidateToRemove(final ZipEntry zipEntry) {
        final String zipEntryName = zipEntry.getName();
        return configuration.getFoldersToStrip().stream().anyMatch(Path.of(zipEntryName)::startsWith)
            || zipEntry.getSize() > configuration.getSizeLimit();
    }

    private boolean hasSignedPackageStructure(final Path csarPackagePath) {
        final List<Path> packagePathList;
        try (final var zf = new ZipFile(csarPackagePath.toString())) {
            packagePathList = zf.stream()
                .filter(zipEntry -> !zipEntry.isDirectory())
                .map(ZipEntry::getName).map(Path::of)
                .collect(Collectors.toList());
        } catch (final IOException e) {
            LOGGER.error("Failed to read ZipFile '{}'", csarPackagePath, e);
            final var errorMsg = String.format(UNEXPECTED_PROBLEM_HAPPENED_WHILE_READING_THE_CSAR, csarPackagePath);
            throw new CsarSizeReducerException(errorMsg, e);
        }

        if (CollectionUtils.isEmpty(packagePathList)) {
            return false;
        }
        final int numberOfFiles = packagePathList.size();
        if (numberOfFiles == 2) {
            return hasOneInternalPackageFile(packagePathList) && hasOneSignatureFile(packagePathList);
        }
        if (numberOfFiles == 3) {
            return hasOneInternalPackageFile(packagePathList) && hasOneSignatureFile(packagePathList) && hasOneCertificateFile(packagePathList);
        }
        return false;
    }

    private boolean hasOneInternalPackageFile(final List<Path> packagePathList) {
        return packagePathList.parallelStream()
            .map(Path::toString)
            .map(FilenameUtils::getExtension)
            .map(String::toLowerCase)
            .filter(extension -> extension.endsWith(CSAR_EXTENSION)).count() == 1;
    }

    private boolean hasOneSignatureFile(final List<Path> packagePathList) {
        return packagePathList.parallelStream()
            .map(Path::toString)
            .map(FilenameUtils::getExtension)
            .map(String::toLowerCase)
            .filter(ALLOWED_SIGNATURE_EXTENSIONS::contains).count() == 1;
    }

    private boolean hasOneCertificateFile(final List<Path> packagePathList) {
        return packagePathList.parallelStream()
            .map(Path::toString)
            .map(FilenameUtils::getExtension)
            .map(String::toLowerCase)
            .filter(ALLOWED_CERTIFICATE_EXTENSIONS::contains).count() == 1;
    }

    @FunctionalInterface
    private interface ZipProcessFunction {

        Consumer<ZipEntry> getProcessZipConsumer(Path csarPackagePath, ZipFile zf, ZipOutputStream zos, AtomicLong uncompressedBytesBudget);
    }

}
