/*-
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
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
 * ============LICENSE_END=========================================================
 */

package org.openecomp.sdc.common.http.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openecomp.sdc.common.http.config.ClientCertificate;

class HttpConnectionMngFactoryTlsTest {

    private static final String STORE_PASSWORD = "changeit";

    @TempDir
    static Path tempDir;

    private static Path serverKeyStore;
    private static Path trustStore;
    private static SSLServerSocket serverSocket;
    private static Thread serverThread;

    @BeforeAll
    static void startSelfSignedServer() throws Exception {
        serverKeyStore = tempDir.resolve("server.p12");
        trustStore = tempDir.resolve("trust.p12");
        Path cert = tempDir.resolve("server.cer");
        keytool("-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1",
            "-dname", "CN=untrusted.example", "-ext", "SAN=ip:127.0.0.1",
            "-storetype", "PKCS12", "-keystore", serverKeyStore.toString(),
            "-storepass", STORE_PASSWORD, "-keypass", STORE_PASSWORD);
        keytool("-exportcert", "-alias", "server", "-keystore", serverKeyStore.toString(),
            "-storepass", STORE_PASSWORD, "-file", cert.toString());
        keytool("-importcert", "-noprompt", "-alias", "server", "-file", cert.toString(),
            "-storetype", "PKCS12", "-keystore", trustStore.toString(), "-storepass", STORE_PASSWORD);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(serverKeyStore.toFile())) {
            keyStore.load(in, STORE_PASSWORD.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, STORE_PASSWORD.toCharArray());
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(kmf.getKeyManagers(), null, null);
        serverSocket = (SSLServerSocket) serverContext.getServerSocketFactory()
            .createServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        serverThread = new Thread(HttpConnectionMngFactoryTlsTest::serve);
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        serverSocket.close();
    }

    @Test
    void defaultManagerRejectsSelfSignedCertificate() {
        HttpClientConnectionManager manager = new HttpConnectionMngFactory().getOrCreate(null);
        assertThrows(SSLHandshakeException.class, () -> get(manager, "https://127.0.0.1:" + serverSocket.getLocalPort() + "/"));
    }

    @Test
    void clientCertificateManagerRejectsHostnameMismatch() {
        HttpClientConnectionManager manager = new HttpConnectionMngFactory().getOrCreate(clientCertificateTrustingServer());
        assertThrows(SSLPeerUnverifiedException.class, () -> get(manager, "https://localhost:" + serverSocket.getLocalPort() + "/"));
    }

    @Test
    void clientCertificateManagerAcceptsTrustedCertificateWithMatchingHost() throws IOException {
        HttpClientConnectionManager manager = new HttpConnectionMngFactory().getOrCreate(clientCertificateTrustingServer());
        assertEquals(200, get(manager, "https://127.0.0.1:" + serverSocket.getLocalPort() + "/"));
    }

    private static ClientCertificate clientCertificateTrustingServer() {
        ClientCertificate clientCertificate = new ClientCertificate();
        clientCertificate.setKeyStore(serverKeyStore.toString());
        clientCertificate.setKeyStorePassword(STORE_PASSWORD, false);
        clientCertificate.setTrustStore(trustStore.toString());
        clientCertificate.setTrustStorePassword(STORE_PASSWORD);
        return clientCertificate;
    }

    private static int get(HttpClientConnectionManager manager, String url) throws IOException {
        try (CloseableHttpClient client = HttpClients.custom().setConnectionManager(manager).build();
            CloseableHttpResponse response = client.execute(new HttpGet(url))) {
            return response.getStatusLine().getStatusCode();
        }
    }

    private static void serve() {
        while (!serverSocket.isClosed()) {
            try (Socket socket = serverSocket.accept()) {
                ((SSLSocket) socket).startHandshake();
                InputStream in = socket.getInputStream();
                int last = 0;
                int current;
                int crlfCount = 0;
                while (crlfCount < 2 && (current = in.read()) != -1) {
                    if (current == '\n' && last == '\r') {
                        crlfCount++;
                    } else if (current != '\r') {
                        crlfCount = 0;
                    }
                    last = current;
                }
                OutputStream out = socket.getOutputStream();
                out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } catch (IOException e) {
                // handshake rejected by the client or server socket closed
            }
        }
    }

    private static void keytool(String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("keytool failed: " + String.join(" ", args));
        }
    }
}
