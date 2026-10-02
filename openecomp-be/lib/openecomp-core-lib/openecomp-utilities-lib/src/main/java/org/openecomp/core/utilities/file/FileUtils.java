/*
 * Copyright © 2016-2018 European Support Limited
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
 */
package org.openecomp.core.utilities.file;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.onap.sdc.tosca.services.YamlUtil;
import org.openecomp.core.utilities.json.JsonUtil;

/**
 * The type File utils.
 */
public class FileUtils {

    public static final String MAX_UPLOAD_SIZE_PROPERTY = "onboarding.upload.maxSize";
    public static final long DEFAULT_MAX_UPLOAD_SIZE = 100L * 1024 * 1024;

    /**
     * Gets the maximum size in bytes accepted for an uploaded file, configurable with the
     * {@value #MAX_UPLOAD_SIZE_PROPERTY} system property.
     *
     * @return the maximum upload size in bytes
     */
    public static long getMaxUploadSize() {
        return Long.getLong(MAX_UPLOAD_SIZE_PROPERTY, DEFAULT_MAX_UPLOAD_SIZE);
    }

    /**
     * Allows to consume an input stream open against a resource with a given file name.
     *
     * @param fileName the file name
     * @param function logic to be applied to the input stream
     */
    public static <T> T readViaInputStream(String fileName, Function<InputStream, T> function) {
        Objects.requireNonNull(fileName);
        // the leading slash doesn't make sense and doesn't work when used with a class loader
        URL resource = FileUtils.class.getClassLoader().getResource(fileName.startsWith("/") ? fileName.substring(1) : fileName);
        if (resource == null) {
            throw new IllegalArgumentException("Resource not found: " + fileName);
        }
        return readViaInputStream(resource, function);
    }

    /**
     * Allows to consume an input stream open against a resource with a given URL.
     *
     * @param urlFile  the url file
     * @param function logic to be applied to the input stream
     */
    public static <T> T readViaInputStream(URL urlFile, Function<InputStream, T> function) {
        Objects.requireNonNull(urlFile);
        try (InputStream is = urlFile.openStream()) {
            return function.apply(is);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Gets file input streams.
     *
     * @param fileName the file name
     * @return the file input streams
     */
    public static List<URL> getAllLocations(String fileName) {
        List<URL> urls = new LinkedList<>();
        Enumeration<URL> urlFiles;
        try {
            urlFiles = FileUtils.class.getClassLoader().getResources(fileName);
            while (urlFiles.hasMoreElements()) {
                urls.add(urlFiles.nextElement());
            }
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        return urls.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(urls);
    }

    /**
     * Convert to bytes byte [ ].
     *
     * @param object    the object
     * @param extension the extension
     * @return the byte [ ]
     */
    public static byte[] convertToBytes(Object object, FileExtension extension) {
        if (object != null) {
            if (extension.equals(FileExtension.YAML) || extension.equals(FileExtension.YML)) {
                return new YamlUtil().objectToYaml(object).getBytes();
            } else {
                return JsonUtil.object2Json(object).getBytes();
            }
        } else {
            return new byte[]{};
        }
    }

    /**
     * Convert to input stream input stream.
     *
     * @param object    the object
     * @param extension the extension
     * @return the input stream
     */
    public static InputStream convertToInputStream(Object object, FileExtension extension) {
        if (object != null) {
            byte[] content;
            if (extension.equals(FileExtension.YAML) || extension.equals(FileExtension.YML)) {
                content = new YamlUtil().objectToYaml(object).getBytes();
            } else {
                content = JsonUtil.object2Json(object).getBytes();
            }
            return new ByteArrayInputStream(content);
        } else {
            return null;
        }
    }

    /**
     * Load file to input stream input stream.
     *
     * @param fileName the file name
     * @return the input stream
     */
    public static InputStream loadFileToInputStream(String fileName) {
        URL urlFile = Thread.currentThread().getContextClassLoader().getResource(fileName);
        try {
            Enumeration<URL> en = Thread.currentThread().getContextClassLoader().getResources(fileName);
            while (en.hasMoreElements()) {
                urlFile = en.nextElement();
            }
        } catch (IOException | NullPointerException exception) {
            throw new RuntimeException(exception);
        }
        try {
            if (urlFile != null) {
                return urlFile.openStream();
            } else {
                throw new RuntimeException();
            }
        } catch (IOException | NullPointerException exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * To byte array byte [ ].
     *
     * @param input the input
     * @return the byte [ ]
     */
    public static byte[] toByteArray(InputStream input) {
        if (input == null) {
            return new byte[0];
        }
        try {
            return IOUtils.toByteArray(input);
        } catch (IOException exception) {
            throw new RuntimeException("error while converting input stream to byte array", exception);
        }
    }

    /**
     * Reads an input stream into a byte array, reading at most {@code maxSize + 1} bytes.
     *
     * @param input   the input stream
     * @param maxSize the maximum number of bytes allowed
     * @return the byte array
     * @throws FileSizeLimitExceededException if the stream holds more than {@code maxSize} bytes
     */
    public static byte[] toByteArray(InputStream input, long maxSize) {
        if (input == null) {
            return new byte[0];
        }
        final byte[] bytes;
        try {
            bytes = IOUtils.toByteArray(new BoundedInputStream(input, maxSize == Long.MAX_VALUE ? maxSize : maxSize + 1));
        } catch (IOException exception) {
            throw new RuntimeException("error while converting input stream to byte array", exception);
        }
        if (bytes.length > maxSize) {
            throw new FileSizeLimitExceededException(maxSize);
        }
        return bytes;
    }

    /**
     * Gets file without extention.
     *
     * @param fileName the file name
     * @return the file without extention
     */
    public static String getFileWithoutExtention(String fileName) {
        if (!fileName.contains(".")) {
            return fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    public static String getFileExtension(String filename) {
        return FilenameUtils.getExtension(filename);
    }

    public static String getNetworkPackageName(String filename) {
        String[] split = filename.split("\\.");
        String name = null;
        if (split.length > 1) {
            name = split[0];
        }
        return name;
    }

    /**
     * Gets file content map from zip.
     *
     * @param inputStream the zip data
     * @return the file content map from zip
     * @throws IOException when an error occurs while extracting zip files
     */
    public static FileContentHandler getFileContentMapFromZip(final InputStream inputStream) throws IOException {

        final var zipInputStream = new ZipInputStream(inputStream);
        ZipEntry zipEntry;
        final var fileContentHandler = new FileContentHandler();
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            final var entryName = zipEntry.getName();
            if (zipEntry.isDirectory()) {
                fileContentHandler.addFolder(entryName);
            } else {
                fileContentHandler.addFile(entryName, zipInputStream.readAllBytes());
            }

        }
        return fileContentHandler;
    }

    /**
     * Write files and folders map to disk in the given path
     *
     * @param fileContentHandler the file content handler
     * @param dir                the dir
     * @return a map containing file names and their absolute paths
     * @throws IOException the io exception
     */
    public static Map<String, String> writeFilesFromFileContentHandler(final FileContentHandler fileContentHandler, final Path dir)
        throws IOException {
        final File dirFile = dir.toFile();
        final Map<String, String> filePaths = new HashMap<>();
        File file;
        for (final String folderPath : fileContentHandler.getFolderList()) {
            file = new File(dirFile, folderPath);
            filePaths.put(folderPath, file.getAbsolutePath());
            if (!file.exists() && !file.mkdirs()) {
                throw new IOException("Could not create directory " + file.getAbsolutePath());
            }
        }
        for (final Map.Entry<String, byte[]> fileEntry : fileContentHandler.getFiles().entrySet()) {
            file = new File(dirFile, fileEntry.getKey());
            filePaths.put(fileEntry.getKey(), file.getAbsolutePath());
            final byte[] fileBytes = fileEntry.getValue();
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                throw new IOException("Could not create parent directory for " + file.getAbsolutePath());
            }
            try (final FileOutputStream fop = new FileOutputStream(file.getAbsolutePath());) {
                fop.write(fileBytes);
                fop.flush();
            }
        }
        return filePaths;
    }

    /**
     * Verify whether the provided extension is valid Yaml/Yml extension or not.
     *
     * @param fileExtension the file extension
     * @return the boolean
     */
    public static boolean isValidYamlExtension(String fileExtension) {
        return fileExtension.equalsIgnoreCase(FileExtension.YML.getDisplayName()) || fileExtension
            .equalsIgnoreCase(FileExtension.YAML.getDisplayName());
    }

    /**
     * The enum File extension.
     */
    public enum FileExtension {
        /**
         * Json file extension.
         */
        JSON("json"),
        /**
         * Yaml file extension.
         */
        YAML("yaml"),
        /**
         * Yml file extension.
         */
        YML("yml");
        private final String displayName;

        FileExtension(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Gets display name.
         *
         * @return the display name
         */
        public String getDisplayName() {
            return displayName;
        }
    }
}
