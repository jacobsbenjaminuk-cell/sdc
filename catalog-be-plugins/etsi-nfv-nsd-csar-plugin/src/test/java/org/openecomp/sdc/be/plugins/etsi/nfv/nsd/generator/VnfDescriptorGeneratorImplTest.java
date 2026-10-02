
/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2020 Nordix Foundation
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
package org.openecomp.sdc.be.plugins.etsi.nfv.nsd.generator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.openecomp.core.utilities.file.FileContentHandler;
import org.openecomp.sdc.be.model.ArtifactDefinition;
import org.openecomp.sdc.be.plugins.etsi.nfv.nsd.exception.VnfDescriptorException;
import org.openecomp.sdc.be.plugins.etsi.nfv.nsd.model.VnfDescriptor;

class VnfDescriptorGeneratorImplTest {

    private final VnfDescriptorGeneratorImpl vnfDescriptorGenerator = new VnfDescriptorGeneratorImpl();
    private final Path testResourcesPath = Paths.get("src", "test", "resources", "vnf-onboarded-csar");

    @Test
    void testGenerate() throws IOException, VnfDescriptorException {
        final byte[] onboardedPackage = getResourceAsByteArray("TestVnf.csar");
        final ArtifactDefinition artifactDefinition = new ArtifactDefinition();
        artifactDefinition.setPayload(onboardedPackage);
        artifactDefinition.setArtifactName("vnf-onboarded-csar.csar");
        final String vnfDescriptorName = "vnf-onboarded-csar";
        final VnfDescriptor vnfDescriptor = vnfDescriptorGenerator.generate(vnfDescriptorName, artifactDefinition).orElse(null);
        final String expectedNodeType = "org.onap.resource.testVnf";
        final String expectedVnfdFileName = "test_vnfd.yaml";
        assertThat("Vnf Descriptor should be present", vnfDescriptor, is(notNullValue()));
        assertThat("Vnf Descriptor should have the expected name", vnfDescriptor.getName(), is(vnfDescriptorName));
        assertThat("Vnf Descriptor should have the expected node type", vnfDescriptor.getNodeType(), is(expectedNodeType));
        assertThat("Vnf Descriptor should have the expected vnfd file name", vnfDescriptor.getVnfdFileName(), is(expectedVnfdFileName));
        assertThat("Vnf Descriptor should contain the expected definition files count", vnfDescriptor.getDefinitionFiles().size(), is(2));
        assertTrue("Vnf Descriptor should contain the expected definition entries", vnfDescriptor.getDefinitionFiles().keySet().contains("Definitions/test_vnfd.yaml"));
        assertTrue("Vnf Descriptor should contain the expected definition entries", vnfDescriptor.getDefinitionFiles().keySet().contains("Definitions/etsi_nfv_sol001_vnfd_2_5_1_types.yaml"));
        
        final String vnfdContents = new String(vnfDescriptor.getDefinitionFiles().get("Definitions/test_vnfd.yaml"), StandardCharsets.UTF_8);
        assertFalse(vnfdContents.contains("interfaces:"));
    }
    

    @Test
    void testReadDefinitionFilesKeepsOnlyMetaAndYamlEntries() throws IOException {
        final FileContentHandler fileContentHandler =
            VnfDescriptorGeneratorImpl.readDefinitionFilesFromZip(new ByteArrayInputStream(getResourceAsByteArray("TestVnf.csar")));
        assertThat(fileContentHandler.getFileList().size(), is(3));
        assertTrue(fileContentHandler.containsFile("TOSCA-Metadata/TOSCA.meta"));
        assertTrue(fileContentHandler.containsFile("Definitions/test_vnfd.yaml"));
        assertTrue(fileContentHandler.containsFile("Definitions/etsi_nfv_sol001_vnfd_2_5_1_types.yaml"));
        assertFalse(fileContentHandler.containsFile("Files/images/docker.tar"));
    }

    @Test
    void testGenerateRejectsOversizedDefinitionFile() throws IOException {
        final byte[] zip = createZip(new String[]{"Definitions/huge.yaml"}, VnfDescriptorGeneratorImpl.MAX_DEFINITION_FILE_SIZE + 1);
        assertRejectedAsZipException(zip);
    }

    @Test
    void testGenerateRejectsHighlyCompressedPackage() throws IOException {
        final byte[] zip = createZip(new String[]{"Files/bomb.bin"}, VnfDescriptorGeneratorImpl.COMPRESSION_RATIO_CHECK_THRESHOLD * 2);
        assertRejectedAsZipException(zip);
    }

    @Test
    void testGenerateRejectsTooManyEntries() throws IOException {
        final String[] entryNames = new String[VnfDescriptorGeneratorImpl.MAX_ZIP_ENTRIES + 1];
        for (int i = 0; i < entryNames.length; i++) {
            entryNames[i] = "Files/file" + i + ".txt";
        }
        final byte[] zip = createZip(entryNames, 0);
        assertRejectedAsZipException(zip);
    }

    @Test
    void testGenerateAcceptsValidPackageBuiltByTestHelper() throws IOException, VnfDescriptorException {
        final byte[] zip = createZip(new String[]{"Files/small.bin"}, 1024);
        assertTrue(vnfDescriptorGenerator.generate("vnf", createCsarArtifact(zip)).isPresent());
    }

    private void assertRejectedAsZipException(final byte[] zip) {
        final VnfDescriptorException exception =
            assertThrows(VnfDescriptorException.class, () -> vnfDescriptorGenerator.generate("vnf", createCsarArtifact(zip)));
        assertThat(exception.getCause() instanceof ZipException, is(true));
    }

    private ArtifactDefinition createCsarArtifact(final byte[] payload) {
        final ArtifactDefinition artifactDefinition = new ArtifactDefinition();
        artifactDefinition.setPayload(payload);
        artifactDefinition.setArtifactName("package.csar");
        return artifactDefinition;
    }

    private byte[] createZip(final String[] entryNames, final long entrySize) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final byte[] zeros = new byte[64 * 1024];
        try (final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("TOSCA-Metadata/TOSCA.meta"));
            zipOutputStream.write("Entry-Definitions: Definitions/vnfd.yaml\n".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("Definitions/vnfd.yaml"));
            zipOutputStream.write("node_types:\n  org.onap.resource.Vnf:\n    derived_from: tosca.nodes.nfv.VNF\n".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            for (final String entryName : entryNames) {
                zipOutputStream.putNextEntry(new ZipEntry(entryName));
                long remaining = entrySize;
                while (remaining > 0) {
                    final int length = (int) Math.min(zeros.length, remaining);
                    zipOutputStream.write(zeros, 0, length);
                    remaining -= length;
                }
                zipOutputStream.closeEntry();
            }
        }
        return byteArrayOutputStream.toByteArray();
    }

    private byte[] getResourceAsByteArray(final String filename) throws IOException {
        try (final InputStream inputStream = readFileAsStream(filename)) {
            return IOUtils.toByteArray(inputStream);
        } catch (final IOException ex) {
            throw new IOException(String.format("Could not read the file \"%s\"", filename), ex);
        }
    }

    private FileInputStream readFileAsStream(final String fileName) throws FileNotFoundException {
        final Path path = Paths.get(testResourcesPath.toString(), fileName);
        return new FileInputStream(path.toFile());
    }
}
