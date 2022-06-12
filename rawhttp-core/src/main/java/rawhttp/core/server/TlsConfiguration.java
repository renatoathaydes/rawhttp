package rawhttp.core.server;

import rawhttp.core.client.TcpRawHttpClient;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * A simple utility class to make it easy to create TLS Sockets for both Server and Client.
 */
public class TlsConfiguration {

    /**
     * Create a {@link TcpRawHttpClient.DefaultOptions} object that overrides the {@code createSocket}
     * method to use the given {@link SSLContext} to create HTTPS connections.
     *
     * @param sslContext SSL Context to use for HTTPS connections.
     * @return client options configured to use the given SSL Context
     */
    public static TcpRawHttpClient.TcpRawHttpClientOptions clientOptions(SSLContext sslContext) {
        return new TcpRawHttpClient.DefaultOptions() {
            @Override
            protected Socket createSocket(boolean useHttps, String host, int port) throws IOException {
                if (useHttps) {
                    return sslContext.getSocketFactory().createSocket(host, port);
                }
                return super.createSocket(useHttps, host, port);
            }
        };
    }

    /**
     * Create a {@link TcpRawHttpServer.TcpRawHttpServerOptions} object that overrides the {@code getServerSocket}
     * method to use the given {@link SSLContext} to create a {@link javax.net.ssl.SSLServerSocket}.
     *
     * @param sslContext SSL Context to use for configuring {@link TcpRawHttpServer}.
     * @return server options using the given SSL Context
     */
    public static TcpRawHttpServer.TcpRawHttpServerOptions serverOptions(SSLContext sslContext,
                                                                         int port) {
        return () -> sslContext.getServerSocketFactory().createServerSocket(port);
    }

    /**
     * Create a SSLContext with the provided keystore.
     *
     * @param keystore         keystore
     * @param keyStorePassword keystore password
     * @return SSLContext with the given configuration
     * @throws KeyStoreException         on key errors
     * @throws CertificateException      on certificate errors
     * @throws IOException               on IO errors
     * @throws NoSuchAlgorithmException  if encryption/signing algorithms are not found
     * @throws KeyManagementException    on key management errors
     * @throws UnrecoverableKeyException on key errors
     */
    public static SSLContext createSSLContext(
            URL keystore,
            @Nullable String keyStorePassword)
            throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException {
        return createSSLContext(keystore, keyStorePassword, null, null);
    }

    /**
     * Create a SSLContext with the provided keystore and truststore.
     *
     * @param keystore           keystore
     * @param keyStorePassword   keystore password
     * @param trustStore         truststore
     * @param trustStorePassword truststore password
     * @return SSLContext with the given configuration
     * @throws KeyStoreException         on key errors
     * @throws CertificateException      on certificate errors
     * @throws IOException               on IO errors
     * @throws NoSuchAlgorithmException  if encryption/signing algorithms are not found
     * @throws KeyManagementException    on key management errors
     * @throws UnrecoverableKeyException on key errors
     */
    public static SSLContext createSSLContext(
            @Nullable URL keystore,
            @Nullable String keyStorePassword,
            @Nullable URL trustStore,
            @Nullable String trustStorePassword)
            throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException {
        TrustManager[] trustManagers = null;
        KeyManager[] keyManagers = null;

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

        if (trustStore != null) {
            try (InputStream trustStoreStream = trustStore.openStream()) {
                keyStore.load(trustStoreStream, arrayOrNull(trustStorePassword));
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        }

        if (keystore != null) {
            try (InputStream keystoreStream = keystore.openStream()) {
                keyStore.load(keystoreStream, arrayOrNull(keyStorePassword));
            }
            KeyManagerFactory keyManagerFactory = KeyManagerFactory
                    .getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, arrayOrNull(keyStorePassword));
            keyManagers = keyManagerFactory.getKeyManagers();
        }

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(keyManagers, trustManagers, null);

        return ctx;
    }

    private static char[] arrayOrNull(@Nullable String value) {
        if (value == null) return null;
        return value.toCharArray();
    }
}
