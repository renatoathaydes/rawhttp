package rawhttp.cli;

import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.server.TcpRawHttpServer;
import rawhttp.core.server.TlsConfiguration;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;

final class CliServerOptions implements TcpRawHttpServer.TcpRawHttpServerOptions {

    private final int port;
    private final RequestLogger requestLogger;
    @Nullable
    private final SSLContext sslContext;

    public CliServerOptions(int port,
                            @Nullable URL keystore,
                            @Nullable String keystorePass,
                            RequestLogger requestLogger) {
        this.port = port;
        this.requestLogger = requestLogger;
        if (keystore != null) {
            try {
                sslContext = TlsConfiguration.createSSLContext(keystore, keystorePass);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            sslContext = null;
        }
    }

    @Override
    public ServerSocket getServerSocket() throws IOException {
        if (sslContext != null) {
            return sslContext.getServerSocketFactory().createServerSocket(port);
        }
        return new ServerSocket(port);
    }

    @Override
    public RawHttpResponse<Void> onResponse(RawHttpRequest request, RawHttpResponse<Void> response) throws IOException {
        requestLogger.logRequest(request, response);
        return TcpRawHttpServer.TcpRawHttpServerOptions.super.onResponse(request, response);
    }
}
