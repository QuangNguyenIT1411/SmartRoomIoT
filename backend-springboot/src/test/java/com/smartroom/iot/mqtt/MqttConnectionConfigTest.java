package com.smartroom.iot.mqtt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.internal.SSLNetworkModuleFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class MqttConnectionConfigTest {
    @TempDir static Path temporary;
    private static Path ca;
    private static SSLContext serverContext;

    @BeforeAll
    static void createEphemeralTlsIdentity() throws Exception {
        // Generated only in the temporary test directory; no fixture private key or real credential.
        String testPassword = UUID.randomUUID().toString();
        Path keyStorePath = temporary.resolve("test-identity.p12");
        ca = temporary.resolve("test-ca.crt");
        keytool("-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-ext", "bc:c",
                "-validity", "2", "-storetype", "PKCS12", "-keystore", keyStorePath.toString(),
                "-storepass", testPassword, "-noprompt");
        keytool("-exportcert", "-alias", "test", "-rfc", "-keystore", keyStorePath.toString(),
                "-storepass", testPassword, "-file", ca.toString());
        var store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keyStorePath)) { store.load(input, testPassword.toCharArray()); }
        var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, testPassword.toCharArray());
        serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keys.getKeyManagers(), null, null);
    }

    @Test
    void anonymousLocalDoesNotRequireTlsOrCredentials() {
        var options = options("tcp://localhost:1883", false, "");
        assertThat(options.getUserName()).isNull();
        assertThat(options.getPassword()).isNull();
        assertThat(options.getSocketFactory()).isNull();
    }

    @Test
    void credentialsArePassedWithoutTrimming() {
        String testPassword = " " + UUID.randomUUID() + " ";
        var options = MqttConnectionConfig.createOptions("ssl://localhost:8883", "test-user", testPassword, true, "");
        assertThat(options.getUserName()).isEqualTo("test-user");
        assertThat(options.getPassword()).isEqualTo(testPassword.toCharArray());
        assertThat(options.isHttpsHostnameVerificationEnabled()).isTrue();
    }

    @Test
    void rejectsTlsDowngradeAndInconsistentScheme() {
        assertThatThrownBy(() -> options("tcp://localhost:1883", true, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> options("ssl://localhost:8883", false, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> options("mqtts://localhost:8883", true, "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmbeddedCredentialsWithoutEchoingThem() {
        String testPassword = UUID.randomUUID().toString();
        assertThatThrownBy(() -> options("ssl://user:" + testPassword + "@localhost:8883", true, ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(testPassword);
        assertThatThrownBy(() -> MqttConnectionConfig.createOptions("tcp://localhost:1883", "", testPassword, false, ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(testPassword);
    }

    @Test
    void rejectsMissingOrInvalidCaAndCaWithoutTls() throws Exception {
        assertThatThrownBy(() -> options("ssl://localhost:8883", true, temporary.resolve("missing.crt").toString()))
                .isInstanceOf(IllegalArgumentException.class);
        Path invalid = temporary.resolve("invalid.crt");
        Files.writeString(invalid, "invalid certificate");
        assertThatThrownBy(() -> options("ssl://localhost:8883", true, invalid.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> options("tcp://localhost:1883", false, ca.toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pahoHandshakeAcceptsConfiguredCaAndMatchingHostname() throws Exception {
        handshake("localhost", options("ssl://localhost:8883", true, ca.toString()), true);
        handshake("localhost", options("ssl://localhost:8883", true, ca.toUri().toString()), true);
    }

    @Test
    void pahoHandshakeRejectsHostnameMismatchEvenWithTrustedCa() throws Exception {
        handshake("127.0.0.1", options("ssl://localhost:8883", true, ca.toString()), false);
    }

    @Test
    void pahoHandshakeRejectsUntrustedCaWithDefaultJvmTrust() throws Exception {
        handshake("localhost", options("ssl://localhost:8883", true, ""), false);
    }

    private static MqttConnectOptions options(String url, boolean tls, String caPath) {
        return MqttConnectionConfig.createOptions(url, "", "", tls, caPath);
    }

    private void handshake(String hostname, MqttConnectOptions options, boolean shouldSucceed) throws Exception {
        try (var server = (SSLServerSocket) serverContext.getServerSocketFactory()
                .createServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            var executor = Executors.newSingleThreadExecutor();
            var peer = executor.submit(() -> {
                try (var socket = (SSLSocket) server.accept()) {
                    socket.setSoTimeout(5000);
                    socket.startHandshake();
                } catch (Exception ex) {
                    if (shouldSucceed) throw new RuntimeException(ex);
                }
            });
            try {
                var module = new SSLNetworkModuleFactory().createNetworkModule(
                        java.net.URI.create("ssl://" + hostname + ":" + server.getLocalPort()), options, "tls-test");
                try {
                    if (shouldSucceed) module.start();
                    else assertThatThrownBy(module::start).isInstanceOf(SSLHandshakeException.class);
                } finally { module.stop(); }
                peer.get(10, TimeUnit.SECONDS);
            } finally { executor.shutdownNow(); }
        }
    }

    private static void keytool(String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(java.util.List.of(arguments));
        var process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
    }
}
