package rawhttp.cli.client;

import rawhttp.cli.PrintResponseMode;
import rawhttp.cli.util.RequestStatistics;
import rawhttp.cookies.ClientOptionsWithCookies;
import rawhttp.cookies.persist.FileCookieJar;
import rawhttp.core.EagerHttpResponse;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.client.TcpRawHttpClient;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.Socket;
import java.net.URI;

public final class RawHttpCliClient extends TcpRawHttpClient {

    private final boolean logRequest;
    private final ClientOptions clientOptions;

    public static RawHttpCliClient create(boolean logRequest, PrintResponseMode printResponseMode) {
        return create(logRequest, printResponseMode, null);
    }

    public static RawHttpCliClient create(boolean logRequest, PrintResponseMode printResponseMode,
                                          @Nullable File cookieJar) {
        ClientOptions clientOptions = new ClientOptions(ResponsePrinter.of(printResponseMode));
        ClientOptionsWithCookies parentOptions = new ClientOptionsWithCookies(
                cookieManagerFor(cookieJar), clientOptions);
        return new RawHttpCliClient(logRequest, parentOptions, clientOptions);
    }

    private RawHttpCliClient(boolean logRequest,
                             TcpRawHttpClientOptions parentOptions,
                             ClientOptions clientOptions) {
        super(parentOptions);
        this.logRequest = logRequest;
        this.clientOptions = clientOptions;
    }

    private static CookieHandler cookieManagerFor(@Nullable File cookieJar) {
        CookieStore cookieStore;
        try {
            cookieStore = cookieJar == null ? null : new FileCookieJar(cookieJar);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new CookieManager(cookieStore, CookiePolicy.ACCEPT_ALL);
    }

    @Override
    public RawHttpResponse<Void> send(RawHttpRequest request) throws IOException {
        if (logRequest) {
            try {
                request = request.eagerly();
                request.writeTo(System.out);
                System.out.println();
            } catch (IOException e) {
                System.err.println("Error logging request to sysout: " + e);
            }
        }
        return super.send(request);
    }

    @Override
    protected Runnable requestSender(RawHttpRequest request, OutputStream outputStream, boolean expectContinue) {
        return new TimedRunnable(super.requestSender(request, outputStream, expectContinue));
    }

    private final class TimedRunnable implements Runnable {
        private final Runnable delegate;

        public TimedRunnable(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            clientOptions.updateSendTime();
            delegate.run();
        }
    }

    private static final class ClientOptions extends DefaultOptions {
        private final ResponsePrinter responsePrinter;
        private TimedSocket currentSocket;

        public ClientOptions(ResponsePrinter responsePrinter) {
            this.responsePrinter = responsePrinter;
        }

        void updateSendTime() {
            currentSocket.markHttpRequestSendTimeNow();
        }

        @Override
        protected Socket createSocket(boolean useHttps, String host, int port) throws IOException {
            // overridden to ensure the connection to host:port is done later so we can time it
            Socket socket = useHttps
                    ? SSLSocketFactory.getDefault().createSocket()
                    : new Socket();
            return new TimedSocket(socket, host, port);
        }

        @Override
        public TimedSocket getSocket(URI uri) {
            currentSocket = (TimedSocket) super.getSocket(uri);

            try {
                currentSocket.connect();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return currentSocket;
        }

        @Override
        public RawHttpResponse<Void> onResponse(Socket socket, URI uri, RawHttpResponse<Void> httpResponse) throws IOException {
            assert socket == currentSocket;
            if (httpResponse.getStatusCode() == 100) {
                responsePrinter.print(httpResponse.getStartLine());
                return httpResponse;
            }

            responsePrinter.print(httpResponse.getStartLine());
            responsePrinter.print(httpResponse.getHeaders());

            boolean keepAlive = !RawHttpResponse.shouldCloseConnectionAfter(httpResponse);
            EagerHttpResponse<Void> eagerResponse = httpResponse.eagerly(keepAlive);
            RequestStatistics stats = currentSocket.computeStatistics();

            responsePrinter.print(eagerResponse.getBody().orElse(null));
            responsePrinter.printStats(stats);

            responsePrinter.waitFor();

            return super.onResponse(socket, uri, eagerResponse);
        }

        @Override
        public void close() throws IOException {
            responsePrinter.close();
            super.close();
        }
    }
}
