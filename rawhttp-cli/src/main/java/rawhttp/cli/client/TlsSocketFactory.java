package rawhttp.cli.client;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

final class TlsSocketFactory {

    private static final TrustManager[] unsafeTrustManagers = new TrustManager[]{
            new UnsafeTrustManager()
    };

    private final SocketFactory socketFactory;

    public TlsSocketFactory(boolean ignoreTlsCert) {
        try {
            if (ignoreTlsCert) {
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, unsafeTrustManagers, new java.security.SecureRandom());
                socketFactory = sc.getSocketFactory();
            } else {
                socketFactory = SSLSocketFactory.getDefault();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    Socket create() throws IOException {
        return socketFactory.createSocket();
    }
}

final class UnsafeTrustManager implements X509TrustManager {

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
