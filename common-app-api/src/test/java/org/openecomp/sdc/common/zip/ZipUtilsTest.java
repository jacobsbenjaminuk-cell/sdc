/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2019 Nordix Foundation
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.openecomp.sdc.common.zip.exception.ZipException;
import org.openecomp.sdc.common.zip.exception.ZipSlipException;

class ZipUtilsTest {

    private static final String ZIP_SLIP_LINUX_ZIP = "zip-slip/zip-slip-linux.zip";
    private static final String ZIP_SLIP_WINDOWS_ZIP = "zip-slip/zip-slip-windows.zip";
    private static final ClassLoader CLASS_LOADER = ZipUtilsTest.class.getClassLoader();

    @Test
    void testZipSlipInRead() {
        final byte[] windowsZipBytes;
        final byte[] linuxZipBytes;
        try {
            final InputStream linuxZipAsStream = CLASS_LOADER.getResourceAsStream(ZIP_SLIP_LINUX_ZIP);
            final InputStream windowsZipAsStream = CLASS_LOADER.getResourceAsStream(ZIP_SLIP_WINDOWS_ZIP);
            if (linuxZipAsStream == null || windowsZipAsStream == null) {
                fail("Could not load the zip slip files");
            }
            linuxZipBytes = IOUtils.toByteArray(linuxZipAsStream);
            windowsZipBytes = IOUtils.toByteArray(windowsZipAsStream);
        } catch (final IOException e) {
            fail("Could not load the required zip slip files", e);
            return;
        }

        try {
            ZipUtils.readZip(linuxZipBytes, true);
            fail("Zip slip should be detected");
        } catch (final ZipException ex) {
            assertThat("Expected ZipSlipException", ex, is(instanceOf(ZipSlipException.class)));
        }

        try {
            ZipUtils.readZip(windowsZipBytes, true);
            fail("Zip slip should be detected");
        } catch (final ZipException ex) {
            assertThat("Expected ZipSlipException", ex, is(instanceOf(ZipSlipException.class)));
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testZipSlipInUnzipLinux() throws IOException {
        final Path tempDirectoryLinux = Files.createTempDirectory("zipSlipLinux" + System.currentTimeMillis());
        try {
            final Path linuxZipPath;
            try {
                linuxZipPath = Paths.get(CLASS_LOADER.getResource(ZIP_SLIP_LINUX_ZIP).toURI());
            } catch (final URISyntaxException e) {
                fail("Could not load the required zip slip files", e);
                return;
            }

            try {
                ZipUtils.unzip(linuxZipPath, tempDirectoryLinux);
                fail("Zip slip should be detected");
            } catch (final ZipException ex) {
                assertThat("At least one of the zip files should throw ZipSlipException",
                    ex, is(instanceOf(ZipSlipException.class)));
            }
        } finally {
            FileUtils.deleteDirectory(tempDirectoryLinux.toFile());
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testZipSlipInUnzipWindows() throws IOException {
        final Path tempDirectoryWindows = Files.createTempDirectory("zipSlipWindows" + System.currentTimeMillis());
        try {
            final Path windowsZipPath;
            try {
                windowsZipPath = Paths.get(CLASS_LOADER.getResource(ZIP_SLIP_WINDOWS_ZIP).toURI());
            } catch (final URISyntaxException e) {
                fail("Could not load the required zip slip files", e);
                return;
            }

            try {
                ZipUtils.unzip(windowsZipPath, tempDirectoryWindows);
                fail("Zip slip should be detected");
            } catch (final ZipException ex) {
                assertThat("At least one of the zip files should throw ZipSlipException",
                    ex, is(instanceOf(ZipSlipException.class)));
            }
        } finally {
            FileUtils.deleteDirectory(tempDirectoryWindows.toFile());
        }
    }

    @Test
    void testUnzipAndZip() throws IOException, ZipException {
        final Path unzipTempPath = Files.createTempDirectory("testUnzip").toRealPath();
        final Path zipTempPath = Files.createTempDirectory("testZip").toRealPath();
        final Path testZipPath;
        try {
            try {
                testZipPath = Paths.get(CLASS_LOADER.getResource("zip/extract-test.zip").toURI());
                ZipUtils.unzip(testZipPath, unzipTempPath);
            } catch (final URISyntaxException e) {
                fail("Could not load the required zip file", e);
                return;
            }

            final Set<Path> expectedPaths = new HashSet<>();
            expectedPaths.add(Paths.get(unzipTempPath.toString(),"rootFile1.txt"));
            expectedPaths.add(Paths.get(unzipTempPath.toString(),"rootFileNoExtension"));
            expectedPaths.add(Paths.get(unzipTempPath.toString(),"EmptyFolder"));
            expectedPaths.add(Paths.get(unzipTempPath.toString(), "SingleLvlFolder"));
            expectedPaths.add(Paths.get(unzipTempPath.toString(), "SingleLvlFolder", "singleLvlFolderFile.txt"));
            expectedPaths.add(Paths.get(unzipTempPath.toString(), "SingleLvlFolder", "singleLvlFolderFileNoExtension"));
            expectedPaths.add(Paths.get(unzipTempPath.toString(), "TwoLvlFolder"));
            expectedPaths.add(Paths.get(unzipTempPath.toString(), "TwoLvlFolder", "twoLvlFolderFile.txt"));
            expectedPaths.add(Paths.get(unzipTempPath.toString(), "TwoLvlFolder", "twoLvlFolderFileNoExtension"));
            expectedPaths.add(Paths.get(unzipTempPath.toString(), "TwoLvlFolder", "SingleLvlFolder"));
            expectedPaths.add(Paths.get(unzipTempPath.toString(), "TwoLvlFolder", "SingleLvlFolder", "singleLvlFolderFile.txt"));
            expectedPaths.add(Paths.get(unzipTempPath.toString(), "TwoLvlFolder", "SingleLvlFolder", "singleLvlFolderFileNoExtension"));

            final AtomicLong actualPathCount = new AtomicLong(0);
            try (final Stream<Path> stream = Files.walk(unzipTempPath)) {
                stream.filter(path -> !unzipTempPath.equals(path)).forEach(actualPath -> {
                    actualPathCount.getAndIncrement();
                    assertThat("Unzipped file should be in the expected list", actualPath, isIn(expectedPaths));
                });
            }
            assertThat("The number of unzipped files should be as expected", actualPathCount.get(), is((long) expectedPaths.size()));
            final Path zipFilePath = zipTempPath.resolve("testzip.zip");
            ZipUtils.createZipFromPath(unzipTempPath, zipFilePath);
            final Map<String, byte[]> fileMap = ZipUtils.readZip(zipFilePath.toFile(), true);
            //matching the folder pattern of the readZip
            final Set<String> expectedPathStringSet = expectedPaths.stream()
                .map(path -> {
                    final Path relativePath = unzipTempPath.relativize(path);
                    return path.toFile().isDirectory() ? relativePath.toString() + File.separator : relativePath.toString();
                }).collect(Collectors.toSet());
            assertThat("The number of zipped files should be as expected", fileMap, aMapWithSize(expectedPathStringSet.size()));
            fileMap.keySet().forEach(s -> {
                assertThat("File in zip package should be in the expected list", s, isIn(expectedPathStringSet));
            });
        } finally {
            FileUtils.deleteDirectory(unzipTempPath.toFile());
            FileUtils.deleteDirectory(zipTempPath.toFile());
        }
    }

    @AfterEach
    void clearLimitProperties() {
        System.clearProperty("sdc.zip.read.maxEntries");
        System.clearProperty("sdc.zip.read.maxEntrySize");
        System.clearProperty("sdc.zip.read.maxTotalSize");
        System.clearProperty("sdc.zip.read.maxCompressedSize");
        System.clearProperty("sdc.zip.read.maxCompressionRatio");
    }

    private static byte[] buildZip(final Map<String, byte[]> entries) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (final ZipOutputStream zipOutputStream = new ZipOutputStream(output)) {
            for (final Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue());
                zipOutputStream.closeEntry();
            }
        }
        return output.toByteArray();
    }

    @Test
    void testReadZipRejectsTooManyEntries() throws IOException {
        System.setProperty("sdc.zip.read.maxEntries", "1");
        final Map<String, byte[]> entries = new HashMap<>();
        entries.put("file1.txt", "content1".getBytes());
        entries.put("file2.txt", "content2".getBytes());
        assertThrows(ZipException.class, () -> ZipUtils.readZip(buildZip(entries), false));
    }

    @Test
    void testReadZipRejectsEntryAboveMaxEntrySize() throws IOException {
        System.setProperty("sdc.zip.read.maxEntrySize", "10");
        final Map<String, byte[]> entries = new HashMap<>();
        entries.put("file1.txt", "a content bigger than ten bytes".getBytes());
        assertThrows(ZipException.class, () -> ZipUtils.readZip(buildZip(entries), false));
    }

    @Test
    void testReadZipRejectsTotalSizeAboveMaxTotalSize() throws IOException {
        System.setProperty("sdc.zip.read.maxTotalSize", "20");
        final Map<String, byte[]> entries = new HashMap<>();
        entries.put("file1.txt", "0123456789".getBytes());
        entries.put("file2.txt", "0123456789A".getBytes());
        assertThrows(ZipException.class, () -> ZipUtils.readZip(buildZip(entries), false));
    }

    @Test
    void testReadZipRejectsArchiveAboveMaxCompressedSize() throws IOException {
        System.setProperty("sdc.zip.read.maxCompressedSize", "10");
        final Map<String, byte[]> entries = new HashMap<>();
        entries.put("file1.txt", "content1".getBytes());
        assertThrows(ZipException.class, () -> ZipUtils.readZip(buildZip(entries), false));
    }

    @Test
    void testReadZipRejectsZipBombByCompressionRatio() throws IOException {
        System.setProperty("sdc.zip.read.maxCompressionRatio", "200");
        final Map<String, byte[]> entries = new HashMap<>();
        entries.put("bomb.bin", new byte[4 * 1024 * 1024]);
        assertThrows(ZipException.class, () -> ZipUtils.readZip(buildZip(entries), false));
    }

    @Test
    void testReadZipAcceptsHighlyCompressibleSmallArchive() throws IOException, ZipException {
        //below the ratio check threshold of 1 MiB inflated, high ratios are allowed
        final Map<String, byte[]> entries = new HashMap<>();
        entries.put("zeros.bin", new byte[512 * 1024]);
        final Map<String, byte[]> fileMap = ZipUtils.readZip(buildZip(entries), false);
        assertThat("Entry should be read", fileMap, aMapWithSize(1));
    }

    @Test
    void testReadZipFromInputStreamAppliesLimits() throws IOException {
        System.setProperty("sdc.zip.read.maxEntries", "1");
        final Map<String, byte[]> entries = new HashMap<>();
        entries.put("file1.txt", "content1".getBytes());
        entries.put("file2.txt", "content2".getBytes());
        assertThrows(ZipException.class, () -> ZipUtils.readZip(new ByteArrayInputStream(buildZip(entries)), false));
    }

    @Test
    void testReadZipFromInputStreamRejectsOversizedCompressedStream() throws IOException {
        System.setProperty("sdc.zip.read.maxCompressedSize", "10");
        final Map<String, byte[]> entries = new HashMap<>();
        entries.put("file1.txt", "content1".getBytes());
        assertThrows(ZipException.class, () -> ZipUtils.readZip(new ByteArrayInputStream(buildZip(entries)), false));
    }

    @Test
    void testReadZipFromInputStreamReadsRegularZip() throws IOException, ZipException {
        final Map<String, byte[]> entries = new HashMap<>();
        entries.put("file1.txt", "content1".getBytes());
        final Map<String, byte[]> fileMap = ZipUtils.readZip(new ByteArrayInputStream(buildZip(entries)), false);
        assertThat("Entry should be read", fileMap, aMapWithSize(1));
        assertThat("Entry content should match", fileMap.get("file1.txt"), is("content1".getBytes()));
    }

    @Test
    void testReadZipFromInputStreamFailsOnReadError() throws IOException {
        //a mid-stream IO failure must propagate instead of silently returning a partial map
        final Map<String, byte[]> entries = new HashMap<>();
        entries.put("file1.txt", "content1".getBytes());
        entries.put("file2.txt", "content2".getBytes());
        final byte[] zipBytes = buildZip(entries);
        final InputStream failingStream = new InputStream() {
            private final ByteArrayInputStream delegate = new ByteArrayInputStream(zipBytes);
            private int readSoFar;

            @Override
            public int read() throws IOException {
                if (++readSoFar > zipBytes.length / 2) {
                    throw new IOException("simulated stream failure");
                }
                return delegate.read();
            }
        };
        assertThrows(ZipException.class, () -> ZipUtils.readZip(failingStream, false));
    }

    @Test
    void testReadZipFromInputStreamAcceptsMixedContentArchive() throws IOException, ZipException {
        //a highly compressible entry followed by incompressible data must not trip the ratio check mid-read
        final Map<String, byte[]> entries = new HashMap<>();
        entries.put("zeros.bin", new byte[2 * 1024 * 1024]);
        final byte[] randomContent = new byte[2 * 1024 * 1024];
        new java.util.Random(42).nextBytes(randomContent);
        entries.put("data.bin", randomContent);
        final byte[] zipBytes = buildZip(entries);
        final Map<String, byte[]> fileMap = ZipUtils.readZip(new ByteArrayInputStream(zipBytes), false);
        assertThat("Both entries should be read", fileMap, aMapWithSize(2));
    }

}
