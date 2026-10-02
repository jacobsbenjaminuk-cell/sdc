/*
 * -
 *  ============LICENSE_START=======================================================
 *  Copyright (C) 2021 Nordix Foundation.
 *  ================================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openecomp.sdc.be.csar.storage.exception.CsarSizeReducerException;
import org.openecomp.sdc.common.zip.ZipUtils;
import org.openecomp.sdc.common.zip.exception.ZipException;

class MinIoStorageCsarSizeReducerTest {

    @Mock
    private CsarPackageReducerConfiguration csarPackageReducerConfiguration;
    @InjectMocks
    private MinIoStorageCsarSizeReducer minIoStorageCsarSizeReducer;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dummyToReduce-3-files.zip", "dummyToReduce.csar", "dummyToNotReduce.csar", "dummyToReduce-2-files.zip"})
    void reduceByPathAndSizeTest(String fileName) throws ZipException {
        final var pathToReduce1 = Path.of("Files/images");
        final var pathToReduce2 = Path.of("Files/Scripts/my_script.sh");
        final var sizeLimit = 150000L;
        when(csarPackageReducerConfiguration.getSizeLimit()).thenReturn(sizeLimit);
        when(csarPackageReducerConfiguration.getFoldersToStrip()).thenReturn(Set.of(pathToReduce1, pathToReduce2));
        when(csarPackageReducerConfiguration.getThresholdEntries()).thenReturn(10000);
        when(csarPackageReducerConfiguration.getMaxUncompressedSize()).thenReturn(10_000_000L);

        final var csarPath = Path.of("src/test/resources/csarSizeReducer/" + fileName);

        final Map<String, byte[]> originalCsar = ZipUtils.readZip(csarPath.toFile(), false);

        final byte[] reduce = minIoStorageCsarSizeReducer.reduce(csarPath);

        final Map<String, byte[]> reducedCsar = ZipUtils.readZip(reduce, false);

        assertEquals(originalCsar.keySet().size(), reducedCsar.keySet().size(), "No file should be removed");
        for (final Entry<String, byte[]> originalEntry : originalCsar.entrySet()) {
            final var originalFilePath = originalEntry.getKey();
            final byte[] originalBytes = originalEntry.getValue();
            assertTrue(reducedCsar.containsKey(originalFilePath),
                String.format("No file should be removed, but it is missing original file '%s'", originalFilePath));

            final String extention = fileName.substring(fileName.lastIndexOf('.') + 1);
            switch (extention.toLowerCase()) {
                case "zip":
                    verifyZIP(pathToReduce1, pathToReduce2, sizeLimit, reducedCsar, originalFilePath, originalBytes);
                    break;
                case "csar":
                    verifyCSAR(pathToReduce1, pathToReduce2, sizeLimit, reducedCsar, originalFilePath, originalBytes);
                    break;
                default:
                    fail("Unexpected file extention");
                    break;
            }
        }
    }

    @Test
    void entryLargerThanSizeLimitIsStrippedEvenWhenItsDeclaredSizeIsSmallTest() throws IOException, ZipException {
        mockConfiguration(1_000L, 10_000_000L);
        final var entries = new LinkedHashMap<String, byte[]>();
        entries.put("Definitions/main.yaml", "tosca_definitions_version: tosca_simple_yaml_1_3".getBytes(StandardCharsets.UTF_8));
        entries.put("Files/bomb.txt", new byte[1_000_000]);
        final Path csarPath = tempDir.resolve("declared-small.csar");
        Files.write(csarPath, setDeclaredUncompressedSize(createZip(entries), "Files/bomb.txt", 1));

        final Map<String, byte[]> reducedCsar = ZipUtils.readZip(minIoStorageCsarSizeReducer.reduce(csarPath), false);

        assertArrayEquals(entries.get("Definitions/main.yaml"), reducedCsar.get("Definitions/main.yaml"));
        assertArrayEquals(new byte[0], reducedCsar.get("Files/bomb.txt"));
        assertTrue(minIoStorageCsarSizeReducer.getReduced().get());
    }

    @Test
    void unsignedPackageExceedingMaxUncompressedSizeIsRejectedTest() throws IOException {
        mockConfiguration(1_000_000L, 2_000_000L);
        final var entries = new LinkedHashMap<String, byte[]>();
        for (int i = 0; i < 3; i++) {
            entries.put("Files/file" + i + ".txt", new byte[900_000]);
        }
        final Path csarPath = tempDir.resolve("unsigned-bomb.csar");
        Files.write(csarPath, createZip(entries));

        assertThrows(CsarSizeReducerException.class, () -> minIoStorageCsarSizeReducer.reduce(csarPath));
        assertOnlyFileLeftIs(csarPath);
    }

    @Test
    void signedPackageWithOversizedSignatureIsRejectedTest() throws IOException {
        mockConfiguration(1_000_000L, 2_000_000L);
        final var innerEntries = new LinkedHashMap<String, byte[]>();
        innerEntries.put("Definitions/main.yaml", "tosca_definitions_version: tosca_simple_yaml_1_3".getBytes(StandardCharsets.UTF_8));
        final var entries = new LinkedHashMap<String, byte[]>();
        entries.put("package.csar", createZip(innerEntries));
        entries.put("package.cms", new byte[5_000_000]);
        final Path csarPath = tempDir.resolve("signed-cms-bomb.zip");
        Files.write(csarPath, createZip(entries));

        assertThrows(CsarSizeReducerException.class, () -> minIoStorageCsarSizeReducer.reduce(csarPath));
        assertOnlyFileLeftIs(csarPath);
    }

    @Test
    void signedPackageWithOversizedNestedCsarIsRejectedTest() throws IOException {
        mockConfiguration(1_000_000L, 2_000_000L);
        final var innerEntries = new LinkedHashMap<String, byte[]>();
        innerEntries.put("Files/images/disk.img", new byte[5_000_000]);
        final var entries = new LinkedHashMap<String, byte[]>();
        entries.put("package.csar", createZip(innerEntries, ZipEntry.STORED));
        entries.put("package.cms", "signature".getBytes(StandardCharsets.UTF_8));
        final Path csarPath = tempDir.resolve("signed-csar-bomb.zip");
        Files.write(csarPath, createZip(entries));

        assertThrows(CsarSizeReducerException.class, () -> minIoStorageCsarSizeReducer.reduce(csarPath));
        assertOnlyFileLeftIs(csarPath);
    }

    private void mockConfiguration(final long sizeLimit, final long maxUncompressedSize) {
        when(csarPackageReducerConfiguration.getSizeLimit()).thenReturn(sizeLimit);
        when(csarPackageReducerConfiguration.getFoldersToStrip()).thenReturn(Set.of(Path.of("Files/images")));
        when(csarPackageReducerConfiguration.getThresholdEntries()).thenReturn(10000);
        when(csarPackageReducerConfiguration.getMaxUncompressedSize()).thenReturn(maxUncompressedSize);
    }

    private void assertOnlyFileLeftIs(final Path expectedFile) throws IOException {
        try (final var files = Files.list(tempDir)) {
            assertArrayEquals(new Object[]{expectedFile}, files.toArray(), "Temporary files should be deleted");
        }
    }

    private static byte[] createZip(final Map<String, byte[]> entries) throws IOException {
        return createZip(entries, ZipEntry.DEFLATED);
    }

    private static byte[] createZip(final Map<String, byte[]> entries, final int method) throws IOException {
        final var outputStream = new ByteArrayOutputStream();
        try (final var zos = new ZipOutputStream(outputStream)) {
            zos.setMethod(method);
            for (final Entry<String, byte[]> entry : entries.entrySet()) {
                final var zipEntry = new ZipEntry(entry.getKey());
                if (method == ZipEntry.STORED) {
                    final var crc = new CRC32();
                    crc.update(entry.getValue());
                    zipEntry.setSize(entry.getValue().length);
                    zipEntry.setCrc(crc.getValue());
                }
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }

    private static byte[] setDeclaredUncompressedSize(final byte[] zip, final String entryName, final int declaredSize) {
        final var buffer = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        for (int offset = 0; offset < zip.length - 46; offset++) {
            if (buffer.getInt(offset) == 0x02014b50) {
                final int nameLength = Short.toUnsignedInt(buffer.getShort(offset + 28));
                final var name = new String(zip, offset + 46, nameLength, StandardCharsets.UTF_8);
                if (name.equals(entryName)) {
                    buffer.putInt(offset + 24, declaredSize);
                    return zip;
                }
            }
        }
        throw new IllegalArgumentException("Central directory entry not found: " + entryName);
    }

    private void verifyCSAR(final Path pathToReduce1, final Path pathToReduce2, final long sizeLimit, final Map<String, byte[]> reducedCsar,
                            final String originalFilePath, final byte[] originalBytes) {
        if (originalFilePath.startsWith(pathToReduce1.toString()) || originalFilePath.startsWith(pathToReduce2.toString())
            || originalBytes.length > sizeLimit) {
            assertArrayEquals("".getBytes(StandardCharsets.UTF_8), reducedCsar.get(originalFilePath),
                String.format("File '%s' expected to be reduced to empty string", originalFilePath));
        } else {
            assertArrayEquals(originalBytes, reducedCsar.get(originalFilePath),
                String.format("File '%s' expected to be equal", originalFilePath));
        }
    }

    private void verifyZIP(final Path pathToReduce1, final Path pathToReduce2, final long sizeLimit, final Map<String, byte[]> reducedCsar,
                           final String originalFilePath, final byte[] originalBytes) {
        if (originalFilePath.startsWith(pathToReduce1.toString()) || originalFilePath.startsWith(pathToReduce2.toString())
            || originalBytes.length > sizeLimit) {
            assertArrayEquals("".getBytes(StandardCharsets.UTF_8), reducedCsar.get(originalFilePath),
                String.format("File '%s' expected to be reduced to empty string", originalFilePath));
        } else {
            if (originalFilePath.endsWith(".csar") && minIoStorageCsarSizeReducer.getReduced().get()) {
                assertNotEquals(originalBytes.length, reducedCsar.get(originalFilePath).length,
                    String.format("File '%s' expected to be NOT equal", originalFilePath));
            } else {
                assertArrayEquals(originalBytes, reducedCsar.get(originalFilePath),
                    String.format("File '%s' expected to be equal", originalFilePath));
            }
        }
    }
}