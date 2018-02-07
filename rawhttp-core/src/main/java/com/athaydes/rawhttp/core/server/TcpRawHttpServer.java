package com.athaydes.rawhttp.core.server;

import com.athaydes.rawhttp.core.EagerHttpResponse;
import com.athaydes.rawhttp.core.RawHttp;
import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;
import com.athaydes.rawhttp.core.errors.InvalidHttpRequest;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

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

    public interface TcpRawHttpServerOptions {

        ServerSocket getServerSocket() throws IOException;

        default RawHttp getRawHttp() {
            return new RawHttp();
        }

        default ExecutorService getExecutorService() {
            return Executors.newCachedThreadPool();
        }

        default EagerHttpResponse<Void> serverErrorResponse() {
            return null;
        }

    }

    private static class RouterAndSocket {

        private final Router router;
        private final ServerSocket socket;
        private final ExecutorService executorService;
        private final RawHttp http;
        private final EagerHttpResponse<Void> serverErrorResponse;

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
                            "\r\n" +
                            "A Server Error has occurred.")).eagerly();

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
                    RawHttpRequest request = http.parseRequest(client.getInputStream());
                    RawHttpResponse<Void> response = route(request);
                    response.writeTo(client.getOutputStream());
                } catch (Exception e) {
                    if (!(e instanceof SocketException)) {
                        if (e instanceof InvalidHttpRequest &&
                                ((InvalidHttpRequest) e).getLineNumber() == 0) {
                            // client was probably closed as we received no bytes
                        } else {
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

        private RawHttpResponse<Void> route(RawHttpRequest request) {
            RequestHandler handler;
            try {
                handler = router.route(request.getStartLine().getUri().getPath());
            } catch (Exception e) {
                e.printStackTrace();

                // bad router
                return serverErrorResponse;
            }

            try {
                return handler.accept(request);
            } catch (Exception e) {
                e.printStackTrace();

                // bad handler
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
