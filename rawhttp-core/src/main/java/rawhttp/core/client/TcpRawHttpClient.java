package rawhttp.core.client;

import rawhttp.core.HttpVersion;
import rawhttp.core.IOSupplier;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpOptions;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.RequestLine;
import rawhttp.core.body.BodyReader;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

    protected final TcpRawHttpClientOptions options;
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
        this(options, new RawHttp(RawHttpOptions.newBuilder()
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
        final RawHttpRequest finalRequest = options.onRequest(request);
        RequestLine startLine = finalRequest.getStartLine();
        Socket socket = getSocket(startLine.getUri());
        return send(finalRequest, startLine, socket, true);
    }

    private RawHttpResponse<Void> send(RawHttpRequest finalRequest,
                                       RequestLine startLine,
                                       Socket socket,
                                       boolean retryOnSocketError) throws IOException {
        boolean expectContinue = !startLine.getHttpVersion().isOlderThan(HttpVersion.HTTP_1_1) &&
                finalRequest.expectContinue();

        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();

        // probe the socket before continuing as it may have been closed by the server
        try {
            startLine.writeTo(outputStream);
            outputStream.flush(); // make sure the bytes are actually sent
        } catch (SocketException e) {
            socket.close();

            // there's a good chance we can retry it without side effects
            // as the request could not have been fully accepted by the server yet
            if (retryOnSocketError) {
                Socket replacementSocket = getSocket(startLine.getUri());
                return send(finalRequest, startLine, replacementSocket, false);
            }
            throw e;
        }

        options.getExecutorService().submit(requestSender(finalRequest, outputStream, expectContinue));

        RawHttpResponse<Void> response;
        if (expectContinue) {
            ResponseWaiter responseWaiter = new ResponseWaiter(() ->
                    rawHttp.parseResponse(inputStream, startLine));
            try {
                if (options.shouldContinue(responseWaiter)) {
                    //noinspection OptionalGetWithoutIsPresent (Expect continue is only valid when there is a body)
                    finalRequest.getBody().get().writeTo(outputStream);
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
            response = rawHttp.parseResponse(inputStream, startLine);
        }

        if (response.getStatusCode() == 100) {
            // 100-Continue: ignore the first response, then expect a new one...
            options.onResponse(socket, finalRequest.getUri(), response);

            return options.onResponse(socket, finalRequest.getUri(),
                    rawHttp.parseResponse(socket.getInputStream(), startLine));
        }

        return options.onResponse(socket, finalRequest.getUri(), response);
    }

    private Socket getSocket(URI uri) throws IOException {
        try {
            return options.getSocket(uri);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw e;
        }
    }

    /**
     * This method returns a {@link Runnable} that when called, submits the request
     * to the server.
     * <p>
     * Notice that since RawHTTP 2.5.0, this method does not send the {@link rawhttp.core.StartLine}
     * because that is sent first to "probe" whether the HTTP server is accepting requests. Only
     * after that succeeds, does RawHTTP send the rest of the HTTP request asynchronously
     * while waiting for the response at the same time.
     *
     * @param request        current request to send
     * @param outputStream   stream connected to a HTTP server
     * @param expectContinue whether this request expects a 100-Continue response
     * @return a Runnable that submits the request when called.
     */
    protected Runnable requestSender(RawHttpRequest request,
                                     OutputStream outputStream,
                                     boolean expectContinue) {
        return () -> {
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(4096);
                request.getHeaders().writeTo(buffer);
                buffer.writeTo(outputStream);
                if (!expectContinue) {
                    Optional<? extends BodyReader> body = request.getBody();
                    if (body.isPresent()) {
                        body.get().writeTo(outputStream);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    outputStream.close();
                } catch (IOException ex) {
                    // nothing better to do here
                }
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
         * Callback that will be called every time the HTTP client is about to send a request.
         * <p>
         * This allows for modifying the request, logging it, inspecting it for sensitive data and many other use cases.
         * <p>
         * The default implementation returns the unmodified request.
         *
         * @param httpRequest the HTTP request to be sent
         * @return a possibly transformed httpRequest
         * @throws IOException if any communication problem occurs
         */
        default RawHttpRequest onRequest(RawHttpRequest httpRequest) throws IOException {
            return httpRequest;
        }

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
        ExecutorService getExecutorService();

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

        private final Map<HostKey, Socket> socketByHost = new HashMap<>(4);
        private final ExecutorService executorService;

        public DefaultOptions() {
            final AtomicInteger threadCount = new AtomicInteger(1);
            this.executorService = Executors.newFixedThreadPool(4, runnable -> {
                Thread t = new Thread(runnable);
                t.setDaemon(true);
                t.setName("tcp-rawhttp-client-" + threadCount.incrementAndGet());
                return t;
            });
        }

        @Override
        public Socket getSocket(URI uri) {
            String host = Optional.ofNullable(uri.getHost()).orElseThrow(() ->
                    new RuntimeException("Host is not available in the URI"));
            boolean useHttps = "https".equalsIgnoreCase(uri.getScheme());

            @Nullable Socket socket = socketByHost.get(new HostKey(host, useHttps));

            if (socket == null || socket.isClosed() || !socket.isConnected()) {
                int port = uri.getPort();
                if (port < 1) {
                    port = useHttps ? 443 : 80;
                }
                try {
                    socket = createSocket(useHttps, host, port);
                    socket.setSoTimeout(5_000);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                socketByHost.put(new HostKey(host, useHttps), socket);
            }

            return socket;
        }

        protected Socket createSocket(boolean useHttps, String host, int port) throws IOException {
            return useHttps
                    ? SSLSocketFactory.getDefault().createSocket(host, port)
                    : new Socket(host, port);
        }

        @Override
        public RawHttpResponse<Void> onResponse(Socket socket,
                                                URI uri,
                                                RawHttpResponse<Void> httpResponse) throws IOException {
            if (RawHttpResponse.shouldCloseConnectionAfter(httpResponse)) {
                boolean useHttps = "https".equalsIgnoreCase(uri.getScheme());

                // resolve the full response before closing the socket
                try (Socket ignore = socketByHost.remove(new HostKey(uri.getHost(), useHttps))) {
                    return httpResponse.eagerly(false);
                }
            }

            return httpResponse;
        }

        @Override
        public ExecutorService getExecutorService() {
            return executorService;
        }

        @Override
        public void close() throws IOException {
            try {
                executorService.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
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

    private static final class HostKey {
        final String host;
        final boolean https;

        HostKey(String host, boolean https) {
            this.host = host;
            this.https = https;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            HostKey hostKey = (HostKey) o;

            if (https != hostKey.https) return false;
            return Objects.equals(host, hostKey.host);
        }

        @Override
        public int hashCode() {
            int result = host != null ? host.hashCode() : 0;
            result = 31 * result + (https ? 1 : 0);
            return result;
        }
    }

}
