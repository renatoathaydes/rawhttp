package rawhttp.core.internal;

import rawhttp.core.client.TcpRawHttpClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

/**
 * Utilities to make it easier to bypass TLS protections for testing purposes.
 */
public final class TlsCertificateIgnorer {

    private static volatile SSLSocketFactory unsafeSocketFactory;

    private static SSLSocketFactory createUnsafeClientSocketFactory()
            throws KeyManagementException, NoSuchAlgorithmException {
        TrustManager[] trustManagers = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }
                }
        };
        SSLContext ctx = SSLContext.getInstance("SSL");
        ctx.init(null, trustManagers, null);
        return ctx.getSocketFactory();
    }

    private static SSLSocketFactory getUnsafeClientSocketFactory() {
        if (unsafeSocketFactory == null) {
            synchronized (UnsafeHttpClientOptions.class) {
                if (unsafeSocketFactory == null) {
                    try {
                        unsafeSocketFactory = createUnsafeClientSocketFactory();
                    } catch (KeyManagementException | NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return unsafeSocketFactory;
    }

    /**
     * Create an unsafe {@link SSLSocket}.
     * <p>
     * The TLS certificate will be entirely ignored, which means that any protection that might
     * have been provided by using TLS (socket encryption and host verification) is completely removed.
     * <p>
     * To use this with {@link TcpRawHttpClient}, pass an instance of {@link UnsafeHttpClientOptions}
     * to its constructor:
     * <pre>
     * var client = new TcpRawHttpClient(new TlsCertificateIgnorer.UnsafeHttpClientOptions())
     * </pre>
     * <p>
     * Please, only use this for testing.
     *
     * @param host host
     * @param port port
     * @return an unsafe SSLSocket
     * @throws IOException on IO errors
     */
    public static SSLSocket createUnsafeSocket(String host, int port) throws IOException {
        return (SSLSocket) getUnsafeClientSocketFactory().createSocket(host, port);
    }

    /**
     * Create an unsafe {@link SSLSocket}.
     * <p>
     * The TLS certificate will be entirely ignored, which means that any protection that might
     * have been provided by using TLS (socket encryption and host verification) is completely removed.
     * <p>
     * To use this with {@link TcpRawHttpClient}, pass an instance of {@link UnsafeHttpClientOptions}
     * to its constructor:
     * <pre>
     * var client = new TcpRawHttpClient(new TlsCertificateIgnorer.UnsafeHttpClientOptions())
     * </pre>
     * <p>
     * Please, only use this for testing.
     *
     * @return an unsafe SSLSocket
     * @throws IOException on IO errors
     */
    public static SSLSocket createUnsafeSocket() throws IOException {
        return (SSLSocket) getUnsafeClientSocketFactory().createSocket();
    }

    /**
     * @see TlsCertificateIgnorer#createUnsafeSocket(String, int)
     */
    public static class UnsafeHttpClientOptions extends TcpRawHttpClient.DefaultOptions {
        @Override
        public Socket createSocket(boolean useHttps, String host, int port) throws IOException {
            if (useHttps) {
                return createUnsafeSocket(host, port);
            }
            return super.createSocket(false, host, port);
        }
    }
}
