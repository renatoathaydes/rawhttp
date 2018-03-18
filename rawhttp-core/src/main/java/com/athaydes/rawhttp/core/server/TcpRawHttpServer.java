package com.athaydes.rawhttp.core.server;

import com.athaydes.rawhttp.core.EagerHttpResponse;
import com.athaydes.rawhttp.core.RawHttp;
import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;
import com.athaydes.rawhttp.core.errors.InvalidHttpRequest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    private final AtomicReference<RouterAndSocket> routerRef = new AtomicReference<>();
    private final TcpRawHttpServerOptions options;

    public TcpRawHttpServer(int port) {
        this.options = () -> new ServerSocket(port);
    }

    public TcpRawHttpServer(TcpRawHttpServerOptions options) {
        this.options = options;
    }

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
         * @return the {@link RawHttp} instance to use to parse requests and responses
         */
        default RawHttp getRawHttp() {
            return new RawHttp();
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
         * @return the default ServerError (500) response to send out when an Exception occurs in the {@link Router}.
         */
        default EagerHttpResponse<Void> serverErrorResponse() {
            return null;
        }

        /**
         * @return the default NotFound (404) response to send out when an Exception occurs in the {@link Router}.
         */
        default EagerHttpResponse<Void> notFoundResponse() {
            return null;
        }

    }

    private static class RouterAndSocket {

        private final Router router;
        private final ServerSocket socket;
        private final ExecutorService executorService;
        private final RawHttp http;
        private final EagerHttpResponse<Void> serverErrorResponse;
        private final EagerHttpResponse<Void> notFoundResponse;

        public RouterAndSocket(Router router, TcpRawHttpServerOptions options) throws IOException {
            this.router = router;
            this.socket = options.getServerSocket();
            this.http = options.getRawHttp();
            this.executorService = options.getExecutorService();
            this.serverErrorResponse = Optional.<RawHttpResponse<Void>>ofNullable(
                    options.serverErrorResponse()).orElseGet(() ->
                    http.parseResponse("HTTP/1.1 500 Server Error\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 28\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Pragma: no-cache\r\n" +
                            "\r\n" +
                            "A Server Error has occurred.")).eagerly();

            this.notFoundResponse = Optional.<RawHttpResponse<Void>>ofNullable(
                    options.notFoundResponse()).orElseGet(() ->
                    http.parseResponse("HTTP/1.1 404 Not Found\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 23\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Pragma: no-cache\r\n" +
                            "\r\n" +
                            "Resource was not found.")).eagerly();

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
            while (true) {
                try {
                    RawHttpRequest request = http.parseRequest(
                            client.getInputStream(),
                            ((InetSocketAddress) client.getRemoteSocketAddress()).getAddress());
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

                    break; // cannot keep listening anymore
                }
            }
        }

        private RawHttpResponse<?> route(RawHttpRequest request) {
            try {
                RawHttpResponse<?> response = router.route(request);
                if (response == null) {
                    return notFoundResponse;
                } else {
                    return response;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return serverErrorResponse;
            }
        }

        void stop() {
            try {
                socket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                executorService.shutdown();
            }
        }
    }

}
