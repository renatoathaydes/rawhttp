package rawhttp.core.server;

import rawhttp.core.EagerHttpResponse;
import rawhttp.core.HttpVersion;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpOptions;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.errors.InvalidHttpRequest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static rawhttp.core.RawHttp.responseHasBody;

/**
 * Simple implementation of {@link RawHttpServer}.
 * <p>
 * This implementation, by default, spans a Thread for each client that connects, re-using Threads as possible.
 * Notice that this does not scale to a large number of clients, so this server is not recommended for production use.
 * <p>
 * It is possible to configure this server by passing an instance of {@link TcpRawHttpServerOptions} to its
 * constructor.
 */
public class TcpRawHttpServer implements RawHttpServer {

    private static final RawHttpHeaders SERVER_HEADER = RawHttpHeaders.newBuilder()
            .with("Server", "RawHTTP")
            .build();

    public static final RawHttp STRICT_HTTP = new RawHttp(RawHttpOptions.strict());

    private static final DateHeaderProvider DATE_HEADER_PROVIDER = new DateHeaderProvider(Duration.ofSeconds(1));

    private final AtomicReference<RouterAndSocket> routerRef = new AtomicReference<>();
    private final TcpRawHttpServerOptions options;

    public TcpRawHttpServer(int port) {
        this.options = () -> new ServerSocket(port);
    }

    public TcpRawHttpServer(TcpRawHttpServerOptions options) {
        this.options = options;
    }

    /**
     * @return the options used by this server
     */
    public TcpRawHttpServerOptions getOptions() {
        return options;
    }

    @Override
    public void start(Router router) {
        try {
            stop();
        } catch (RuntimeException e) {
            // ignore because this means the previous socket probably was already dead,
            // but that shouldn't make it impossible to start another server
        }

        try {
            routerRef.set(new RouterAndSocket(router, options));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        RouterAndSocket current = routerRef.getAndSet(null);
        if (current != null) {
            current.stop();
        }
    }

    /**
     * @return headers containing a single "Date" header with the current date. A returned instance is cached for one
     * second, so multiple calls in quick succession will return the same instance.
     */
    public static RawHttpHeaders getCurrentDateHeader() {
        return DATE_HEADER_PROVIDER.get();
    }

    /**
     * Configuration options for {@link TcpRawHttpServer}.
     */
    public interface TcpRawHttpServerOptions {

        /**
         * Create a server socket for the server to use.
         *
         * @return a server socket
         * @throws IOException if an error occurs when binding the socket
         */
        ServerSocket getServerSocket() throws IOException;

        /**
         * Configure a client socket just as it's connection has been accepted.
         * <p>
         * The default implementation sets the socket read timeout to 5 seconds.
         *
         * @param socket client socket
         * @return the configured socket
         * @throws IOException if configuring the socket causes an error
         */
        default Socket configureClientSocket(Socket socket) throws IOException {
            socket.setSoTimeout(5_000);
            return socket;
        }

        /**
         * @return the {@link RawHttp} instance to use to parse requests and responses
         */
        default RawHttp getRawHttp() {
            return STRICT_HTTP;
        }

        /**
         * @return executor service to use to run client-serving {@link Runnable}s. Each {@link Runnable} runs until
         * the connection with the client is closed or lost.
         */
        default ExecutorService createExecutorService() {
            final AtomicInteger threadCount = new AtomicInteger(1);
            return Executors.newFixedThreadPool(25, runnable -> {
                Thread t = new Thread(runnable);
                t.setDaemon(true);
                t.setName("tcp-rawhttp-server-client-" + threadCount.incrementAndGet());
                return t;
            });
        }

        /**
         * @param request received by the server
         * @return the default ServerError (500) response to send out when an Exception occurs in the {@link Router}.
         */
        default Optional<EagerHttpResponse<Void>> serverErrorResponse(RawHttpRequest request) {
            return Optional.empty();
        }

        /**
         * @param request received by the server
         * @return the default NotFound (404) response to send out when an Exception occurs in the {@link Router}.
         */
        default Optional<EagerHttpResponse<Void>> notFoundResponse(RawHttpRequest request) {
            return Optional.empty();
        }

        /**
         * Callback that will be called every time the server receives a HTTP request, but before it sends out a
         * HTTP response.
         * <p>
         * The actual response the client will see is the one returned by this method.
         * <p>
         * By default, this method adds "Date" and "Server" headers (the latter with the value of "RawHTTP").
         *
         * @param request  received by the server
         * @param response the server routed to. Normally, this callback should return this response with possibly
         *                 minor alterations.
         * @return the response the client should receive. Must not be null.
         * @throws IOException if an error occurs reading/writing HTTP messages
         */
        default RawHttpResponse<Void> onResponse(RawHttpRequest request, RawHttpResponse<Void> response)
                throws IOException {
            return response.withHeaders(getCurrentDateHeader().and(SERVER_HEADER));
        }

    }

    private static class RouterAndSocket {

        private final Router router;
        private final ServerSocket socket;
        private final ExecutorService executorService;
        private final RawHttp http;
        private final TcpRawHttpServerOptions options;

        RouterAndSocket(Router router,
                        TcpRawHttpServerOptions options) throws IOException {
            this.router = router;
            this.socket = options.getServerSocket();
            this.http = options.getRawHttp();
            this.executorService = options.createExecutorService();
            this.options = options;

            start();
        }

        private void start() {
            new Thread(() -> {
                int failedAccepts = 0;

                while (true) {
                    try {
                        Socket client = socket.accept();
                        executorService.submit(() -> handle(client, socket));
                        failedAccepts = 0;
                    } catch (SocketException e) {
                        break; // server socket was closed or got broken
                    } catch (IOException e) {
                        failedAccepts++;
                        e.printStackTrace();
                        if (failedAccepts > 10) {
                            break; // give up, too many accept failures
                        }
                    }
                }
            }, "tcp-raw-http-server").start();
        }

        private void handle(Socket client, ServerSocket serverSocket) {
            RawHttpRequest request;
            boolean serverWillCloseConnection = false;
            try {
                client = options.configureClientSocket(client);
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    client.close();
                } catch (IOException e2) {
                    // nothing to do without a properly configured client socket
                    return;
                }
            }

            while (!serverWillCloseConnection) {
                try {
                    if (serverSocket.isClosed()) {
                        client.close();
                        break;
                    }
                    request = http.parseRequest(
                            client.getInputStream(),
                            ((InetSocketAddress) client.getRemoteSocketAddress()).getAddress());
                    HttpVersion httpVersion = request.getStartLine().getHttpVersion();
                    Optional<String> connectionOption = request.getHeaders().getFirst("Connection");

                    // If the "close" connection option is present, the connection will
                    // not persist after the current response
                    serverWillCloseConnection = connectionOption
                            .map("close"::equalsIgnoreCase)
                            .orElse(false);

                    RawHttpResponse<?> response = null;
                    boolean expects100 = request.expectContinue();

                    if (expects100 && !request.getStartLine().getHttpVersion().isOlderThan(HttpVersion.HTTP_1_1)) {
                        RawHttpResponse<Void> interimResponse = router
                                .continueResponse(request.getStartLine(), request.getHeaders())
                                .orElse(HttpResponses.get100ContinueResponse());
                        if (interimResponse.getStatusCode() == 100) {
                            // tell the client that we shall continue
                            interimResponse.writeTo(client.getOutputStream());
                        } else {
                            // if we don't accept the request body, we must close the connection
                            serverWillCloseConnection = true;
                            response = interimResponse;
                        }
                    }

                    if (!serverWillCloseConnection) {
                        // https://tools.ietf.org/html/rfc7230#section-6.3
                        // If the received protocol is HTTP/1.1 (or later)
                        // OR
                        // If the received protocol is HTTP/1.0, the "keep-alive" connection
                        // option is present
                        // THEN the connection will persist
                        // OTHERWISE close the connection
                        boolean serverShouldPersistConnection =
                                !httpVersion.isOlderThan(HttpVersion.HTTP_1_1)
                                        || (httpVersion == HttpVersion.HTTP_1_0 && connectionOption
                                        .map("keep-alive"::equalsIgnoreCase)
                                        .orElse(false));
                        serverWillCloseConnection = !serverShouldPersistConnection;
                    }

                    try {
                        if (response == null) {
                            response = route(request);
                        }
                        serverWillCloseConnection |= RawHttpResponse.shouldCloseConnectionAfter(response);
                        response.writeTo(client.getOutputStream());
                    } finally {
                        closeBodyOf(response);
                    }
                } catch (SocketTimeoutException e) {
                    serverWillCloseConnection = true;
                } catch (Exception e) {
                    if (!(e instanceof SocketException)) {
                        // only print stack trace if this is not due to a client closing the connection
                        boolean clientClosedConnection = e instanceof InvalidHttpRequest &&
                                ((InvalidHttpRequest) e).getLineNumber() == 0;

                        if (!clientClosedConnection) {
                            e.printStackTrace();
                        }
                    }

                    serverWillCloseConnection = true; // cannot keep listening anymore
                } finally {
                    if (serverWillCloseConnection) {
                        try {
                            client.close();
                        } catch (IOException e) {
                            // not a problem
                        }
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        private RawHttpResponse<?> route(RawHttpRequest request) throws IOException {
            RawHttpResponse<Void> response;
            try {
                response = router.route(request).map(res -> (RawHttpResponse<Void>) res)
                        .orElseGet(() -> options.notFoundResponse(request).orElseGet(() ->
                                HttpResponses.getNotFoundResponse(request.getStartLine().getHttpVersion())));
            } catch (Exception e) {
                e.printStackTrace();
                response = options.serverErrorResponse(request).orElseGet(() ->
                        HttpResponses.getServerErrorResponse(request.getStartLine().getHttpVersion()));
            }
            if (request.getMethod().equals("HEAD") && response.getBody().isPresent()) {
                response = response.withBody(null, false);
            } else if (!response.getBody().isPresent() &&
                    responseHasBody(response.getStartLine(), request.getStartLine())) {
                // we must tell the client the response is empty
                response = response.withHeaders(RawHttpHeaders.CONTENT_LENGTH_ZERO, true);
            }
            return options.onResponse(request, response);
        }

        void stop() {
            try {
                socket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                executorService.shutdown();
                boolean ok = false;
                try {
                    ok = executorService.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (!ok) {
                    executorService.shutdownNow();
                }
            }
        }

        private static void closeBodyOf(RawHttpResponse<?> response) {
            if (response != null) {
                response.getBody().ifPresent(b -> {
                    try {
                        b.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        }

    }

}
