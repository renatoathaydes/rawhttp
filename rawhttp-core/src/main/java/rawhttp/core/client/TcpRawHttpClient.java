package rawhttp.core.client;

import rawhttp.core.HttpVersion;
import rawhttp.core.IOSupplier;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpOptions;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple implementation of {@link RawHttpClient} based on TCP {@link Socket}s.
 */
public class TcpRawHttpClient implements RawHttpClient<Void>, Closeable {

    private final TcpRawHttpClientOptions options;
    private final RawHttp rawHttp;

    /**
     * Create a new {@link TcpRawHttpClient} using {@link DefaultOptions}.
     */
    public TcpRawHttpClient() {
        this(null);
    }

    /**
     * Create a new {@link TcpRawHttpClient} using the given options, which can give a custom
     * socketProvider and onClose callback.
     * <p>
     * If no options are given, {@link DefaultOptions} is used.
     *
     * @param options configuration for this client
     */
    public TcpRawHttpClient(@Nullable TcpRawHttpClientOptions options) {
        this(options == null ? new DefaultOptions() : options,
                new RawHttp(RawHttpOptions.newBuilder()
                        .doNotAllowNewLineWithoutReturn()
                        .build()));
    }

    /**
     * Create a new {@link TcpRawHttpClient} using the given options, which can give a custom
     * socketProvider and onClose callback.
     * <p>
     * If no options are given, {@link DefaultOptions} is used.
     *
     * @param options configuration for this client
     * @param rawHttp http instance to use to send out requests
     */
    public TcpRawHttpClient(@Nullable TcpRawHttpClientOptions options,
                            RawHttp rawHttp) {
        this.options = options == null ? new DefaultOptions() : options;
        this.rawHttp = rawHttp;
    }

    @Override
    public RawHttpResponse<Void> send(RawHttpRequest request) throws IOException {
        Socket socket;
        try {
            socket = options.getSocket(request.getUri());
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw e;
        }

        boolean expectContinue = !request.getStartLine().getHttpVersion().isOlderThan(HttpVersion.HTTP_1_1) &&
                request.expectContinue();

        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();

        options.getExecutorService().submit(requestSender(request, outputStream, expectContinue));

        RawHttpResponse<Void> response;
        if (expectContinue) {
            ResponseWaiter responseWaiter = new ResponseWaiter(() ->
                    rawHttp.parseResponse(inputStream, request.getStartLine()));
            try {
                if (options.shouldContinue(responseWaiter)) {
                    //noinspection OptionalGetWithoutIsPresent (Expect continue is only valid when there is a body)
                    request.getBody().get().writeTo(outputStream);
                    // call the response waiter if the custom shouldContinue implementation hasn't yet done that
                    if (!responseWaiter.wasCalled.get()) {
                        responseWaiter.call();
                    }
                    response = responseWaiter.response;
                } else {
                    socket.close();
                    options.removeSocket(socket);
                    throw new RuntimeException("Unable to obtain a response due to a 100-continue " +
                            "request not being continued");
                }
            } catch (Exception e) {
                socket.close();
                options.removeSocket(socket);
                throw new RuntimeException("Unable to obtain a response due to a 100-continue " +
                        "request not being continued", e);
            }
        } else {
            response = rawHttp.parseResponse(inputStream, request.getStartLine());
        }

        if (response.getStatusCode() == 100) {
            // 100-Continue: ignore the first response, then expect a new one...
            options.onResponse(socket, request.getUri(), response);

            return options.onResponse(socket, request.getUri(),
                    rawHttp.parseResponse(socket.getInputStream(), request.getStartLine()));
        }

        return options.onResponse(socket, request.getUri(), response);
    }

    private Runnable requestSender(RawHttpRequest request,
                                   OutputStream outputStream,
                                   boolean expectContinue) {
        return () -> {
            try {
                if (expectContinue) {
                    request.getStartLine().writeTo(outputStream);
                    request.getHeaders().writeTo(outputStream);
                } else {
                    request.writeTo(outputStream);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        };
    }

    @Override
    public void close() throws IOException {
        options.close();
    }

    /**
     * Configuration options for {@link TcpRawHttpClient}.
     */
    public interface TcpRawHttpClientOptions extends Closeable {

        /**
         * @param uri the URI to connect the Socket to
         * @return socket provider function that, given a request's {@link URI},
         * provides a {@link Socket} that can be used to send out the request.
         */
        Socket getSocket(URI uri);

        /**
         * Callback that will be called every time the HTTP client receives a response.
         * <p>
         * It may be used to perform maintenance (such as calling {@link RawHttpResponse#eagerly()}
         * then closing the connection), or transform the provided HTTP response, returning a modified one.
         * <p>
         * If the response has the 100-Continue status code, this method may be called more than once for the same
         * request, once with the 100-Continue response, and then with the final response.
         *
         * @param socket       the socket used to send out a HTTP request. This socket is always
         *                     the same as provided by a previous call to {@link #getSocket(URI)}.
         * @param uri          used to make the HTTP request
         * @param httpResponse the HTTP response received from the server
         * @return a possibly transformed httpResponse
         * @throws IOException if any communication problem occurs
         */
        RawHttpResponse<Void> onResponse(
                Socket socket, URI uri, RawHttpResponse<Void> httpResponse) throws IOException;

        /**
         * Decide whether the body of a request with the 100-continue expectation should be sent to the server
         * after obtaining a HTTP response from it.
         * <p>
         * The default implementation waits for up to 5 seconds for a response, and continues only if the response comes
         * within that time limit and has either the 100-Continue or a 2xx or a 3xx status code.
         *
         * @param waitForHttpResponse callable that will block until the server sends a HTTP response
         * @return true to continue and send the message body to the server, false to stop.
         * @throws Exception if an error occurs. If that happens, the request body will not be sent and the connection
         *                   to the server will be closed.
         */
        default boolean shouldContinue(Callable<RawHttpResponse<Void>> waitForHttpResponse) throws Exception {
            Future<RawHttpResponse<Void>> responseFuture = getExecutorService().submit(waitForHttpResponse);
            try {
                RawHttpResponse<Void> response = responseFuture.get(5, TimeUnit.SECONDS);
                return response.getStatusCode() == 100 ||
                        200 <= response.getStatusCode() && response.getStatusCode() < 400;
            } catch (TimeoutException e) {
                return false;
            }
        }

        /**
         * @return executor service to use to run send the full request body.
         * <p>
         * By sending the request's body asynchronously, it is possible to send more than
         * one request to a server without waiting for each response to be downloaded first.
         */
        default ExecutorService getExecutorService() {
            final AtomicInteger threadCount = new AtomicInteger(1);
            return Executors.newFixedThreadPool(4, runnable -> {
                Thread t = new Thread(runnable);
                t.setDaemon(true);
                t.setName("tcp-rawhttp-client-" + threadCount.incrementAndGet());
                return t;
            });
        }

        @Override
        default void close() throws IOException {
        }

        /**
         * Remove a "bad" socket from the pool of sockets being currently used by the client.
         * <p>
         * This method may be called when an error occurs due to transmission of protocol problems.
         *
         * @param socket to remove
         */
        default void removeSocket(Socket socket) {
        }
    }

    /**
     * The default Socket provider used by {@link TcpRawHttpClient}.
     * <p>
     * When resolving a {@link Socket} for a {@link URI}, unless the port is present
     * in the {@link URI}, port 80 will be used for "http" requests,
     * and port 43 for "https" requests.
     * <p>
     * Sockets are re-used if possible (following the {@code Connection} header as described
     * in <a href="https://tools.ietf.org/html/rfc7230#section-6.3">Section 6.3</a> of RFC-7230).
     */
    public static class DefaultOptions implements TcpRawHttpClientOptions {

        private final Map<String, Socket> socketByHost = new HashMap<>(4);

        @Override
        public Socket getSocket(URI uri) {
            String host = Optional.ofNullable(uri.getHost()).orElseThrow(() ->
                    new RuntimeException("Host is not available in the URI"));

            @Nullable Socket socket = socketByHost.get(host);

            if (socket == null || socket.isClosed() || !socket.isConnected()) {
                boolean useHttps = "https".equalsIgnoreCase(uri.getScheme());
                int port = uri.getPort();
                if (port < 1) {
                    port = useHttps ? 443 : 80;
                }
                try {
                    socket = useHttps
                            ? SSLSocketFactory.getDefault().createSocket(host, port)
                            : new Socket(host, port);
                    socket.setSoTimeout(5_000);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                socketByHost.put(host, socket);
            }

            return socket;
        }

        @Override
        public RawHttpResponse<Void> onResponse(Socket socket,
                                                URI uri,
                                                RawHttpResponse<Void> httpResponse) throws IOException {
            if (httpResponse.getHeaders()
                    .getFirst("Connection")
                    .orElse("")
                    .equalsIgnoreCase("close") ||
                    httpResponse.getStartLine().getHttpVersion().isOlderThan(HttpVersion.HTTP_1_1)) {

                socketByHost.remove(uri.getHost());

                // resolve the full response before closing the socket
                return httpResponse.eagerly(false);
            }

            return httpResponse;
        }

        @Override
        public void close() {
            for (Socket socket : socketByHost.values()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void removeSocket(Socket socket) {
            socketByHost.values().remove(socket);
        }

    }

    private static class ResponseWaiter implements Callable<RawHttpResponse<Void>> {
        private final AtomicBoolean wasCalled = new AtomicBoolean(false);
        volatile RawHttpResponse<Void> response;
        private final IOSupplier<RawHttpResponse<Void>> readResponse;

        private ResponseWaiter(IOSupplier<RawHttpResponse<Void>> readResponse) {
            this.readResponse = readResponse;
        }

        @Override
        public RawHttpResponse<Void> call() throws Exception {
            if (wasCalled.compareAndSet(false, true)) {
                response = readResponse.get();
                return response;
            } else {
                throw new IllegalStateException("Cannot receive HTTP Request more than once");
            }
        }
    }

}
