package com.athaydes.rawhttp.core.server;

import com.athaydes.rawhttp.core.EagerHttpResponse;
import com.athaydes.rawhttp.core.HttpVersion;
import com.athaydes.rawhttp.core.RawHttp;
import com.athaydes.rawhttp.core.RawHttpHeaders;
import com.athaydes.rawhttp.core.RawHttpOptions;
import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;
import com.athaydes.rawhttp.core.errors.InvalidHttpRequest;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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

    public static final RawHttp STRICT_HTTP = new RawHttp(RawHttpOptions.newBuilder()
            .doNotAllowNewLineWithoutReturn()
            .doNotInsertHostHeaderIfMissing()
            .build());

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
    public interface TcpRawHttpServerOptions extends Closeable {

        /**
         * Create a server socket for the server to use.
         *
         * @return a server socket
         * @throws IOException if an error occurs when binding the socket
         */
        ServerSocket getServerSocket() throws IOException;

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
        default ExecutorService getExecutorService() {
            final AtomicInteger threadCount = new AtomicInteger(1);
            return Executors.newCachedThreadPool(runnable -> {
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
         * @return a supplier of HTTP headers for responses with the given status code.
         * <p/>
         * By default, the server inserts a "Server: RawHTTP" header and an appropriate "Date" header in all responses
         * (notice that the HTTP specification recommends adding the "Date" header to all responses, but that's not
         * mandatory).
         */
        default Optional<Supplier<RawHttpHeaders>> autoHeadersSupplier(@SuppressWarnings("unused") int statusCode) {
            return Optional.of(() -> getCurrentDateHeader().and(SERVER_HEADER));
        }

        /**
         * Callback that will be called every time the server receives a HTTP request, but before it sends out a
         * HTTP response.
         * <p>
         * The actual response the client will see is the one returned by this method.
         *
         * @param request  received by the server
         * @param response the server routed to. Normally, this callback should return this response with possibly
         *                 minor alterations.
         * @return the response the client should receive. Must not be null.
         */
        default RawHttpResponse<Void> onResponse(RawHttpRequest request, RawHttpResponse<Void> response)
                throws IOException {
            return response;
        }

        @Override
        default void close() throws IOException {
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
            this.executorService = options.getExecutorService();
            this.options = options;

            start();
        }

        private void start() {
            new Thread(() -> {
                int failedAccepts = 0;

                while (true) {
                    Socket client;
                    try {
                        client = socket.accept();
                        executorService.submit(() -> handle(client));
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

        private void handle(Socket client) {
            RawHttpRequest request;
            boolean serverWillCloseConnection = false;

            while (!serverWillCloseConnection) {
                try {
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

                    RawHttpResponse<?> response = route(request);
                    response.writeTo(client.getOutputStream());
                } catch (Exception e) {
                    if (!(e instanceof SocketException)) {
                        // only print stack trace if this is not due to a client closing the connection
                        boolean clientClosedConnection = e instanceof InvalidHttpRequest &&
                                ((InvalidHttpRequest) e).getLineNumber() == 0;

                        if (!clientClosedConnection) {
                            e.printStackTrace();
                        }
                        try {
                            client.close();
                        } catch (IOException ignore) {
                            // we wanted to forget the client, so this is fine
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

        private RawHttpResponse<?> route(RawHttpRequest request) throws IOException {
            RawHttpResponse<Void> response;
            try {
                //noinspection unchecked (it's always safe to cast generic type to Void)
                response = router.route(request).map(res -> (RawHttpResponse<Void>) res)
                        .orElseGet(() -> options.notFoundResponse(request).orElseGet(() ->
                                HttpResponses.getNotFoundResponse(request.getStartLine().getHttpVersion())));
            } catch (Exception e) {
                e.printStackTrace();
                response = options.serverErrorResponse(request).orElseGet(() ->
                        HttpResponses.getServerErrorResponse(request.getStartLine().getHttpVersion()));
            }
            return options.onResponse(request, withAutoHeaders(response));
        }

        void stop() {
            try {
                socket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                executorService.shutdown();

                try {
                    options.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private RawHttpResponse<Void> withAutoHeaders(RawHttpResponse<Void> response) {
            Integer statusCode = response.getStatusCode();
            Optional<Supplier<RawHttpHeaders>> autoHeadersSupplier = options.autoHeadersSupplier(statusCode);
            return autoHeadersSupplier.map(s -> response.withHeaders(s.get())).orElse(response);
        }

    }

}
