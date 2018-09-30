package com.athaydes.rawhttp.duplex;

import com.athaydes.rawhttp.duplex.body.StreamedChunkedBody;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.body.ChunkedBodyContents.Chunk;
import rawhttp.core.body.ChunkedBodyParser;
import rawhttp.core.body.InputStreamChunkDecoder;
import rawhttp.core.client.RawHttpClient;
import rawhttp.core.client.TcpRawHttpClient;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.athaydes.rawhttp.duplex.MessageSender.PING_MESSAGE;
import static com.athaydes.rawhttp.duplex.MessageSender.PLAIN_TEXT_HEADER;
import static com.athaydes.rawhttp.duplex.MessageSender.UTF8_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static rawhttp.core.HttpMetadataParser.createStrictHttpMetadataParser;

/**
 * Entry-point of the rawhttp-duplex library.
 * <p>
 * This class can be used to create a duplex communication channel as either a client or a server.
 * The {@code connect} methods are used from a client to connect to a server,
 * while the {@code accept} methods should be used within
 * a HTTP server to handle requests from a client.
 * <p>
 * The way duplex communication is achieved uses only HTTP/1.1 standard mechanisms and can be described as follows:
 *
 * <ul>
 * <li>The server listens for requests to start duplex communication.</li>
 * <li>When a client connects, the server sends out a single chunked response in which each chunk
 * is a new message from the server to the client.</li>
 * <li>The client does the same: it sends a chunked body with the request in which each chunk is a message from the
 * client to the server.</li>
 * </ul>
 * In other words, a single request/response is used to bootstrap communications. Both the request and the response
 * have effectively infinite chunked bodies where each chunk represents a message.
 * <p>
 * {@link RawHttpDuplex} sends a single extension parameter to idenfity text
 * messages: {@code Content-Type: text/plain} (notice that each chunk may contain "extensions").
 * If the chunk does not contain this extension, then it is considered
 * to be a binary message.
 * <p>
 * Each side of a connection pings the other every 5 seconds, by default, to avoid the TCP socket timing out.
 * To use a different ping period, use either the {@link RawHttpDuplex#RawHttpDuplex(RawHttpClient, Duration)}
 * or the {@link RawHttpDuplex#RawHttpDuplex(RawHttpDuplexOptions)} constructors.
 */
public class RawHttpDuplex {

    private static final Duration DEFAULT_PING_PERIOD = Duration.ofSeconds(5);

    private final RawHttpResponse<Void> okResponse;
    private final RawHttpClient<?> client;
    private final Duration pingPeriod;
    private final ScheduledExecutorService pinger;

    /**
     * Create a new instance of {@link RawHttpDuplex} using the default configuration.
     */
    public RawHttpDuplex() {
        this(new TcpRawHttpClient(new DuplexClientOptions()), DEFAULT_PING_PERIOD);
    }

    /**
     * Create a new instance of {@link RawHttpDuplex} that uses the given client to connect to a remote server.
     *
     * @param client used to connect to a server
     */
    public RawHttpDuplex(RawHttpClient<?> client) {
        this(client, DEFAULT_PING_PERIOD);
    }

    /**
     * Create a new instance of {@link RawHttpDuplex} that uses the given client to connect to a remote server.
     *
     * @param client     used to connect to a server
     * @param pingPeriod the period between pings.
     *                   <p>
     *                   Pings are used to keep socket reads from timing out.
     */
    public RawHttpDuplex(RawHttpClient<?> client, Duration pingPeriod) {
        this(new RawHttpDuplexOptions(client, pingPeriod));
    }

    /**
     * Create a new instance of {@link RawHttpDuplex} that uses the given options.
     * <p>
     * This is the most general constructor, as {@link RawHttpDuplexOptions} can provide all options other constructors
     * accept.
     *
     * @param options to use for this instance
     */
    public RawHttpDuplex(RawHttpDuplexOptions options) {
        this.okResponse = new RawHttp().parseResponse("200 OK");
        this.client = options.getClient();
        this.pingPeriod = options.getPingPeriod();
        this.pinger = options.getPingScheduler();
    }

    /**
     * Connect to a remote server, establishing duplex communications as a client.
     *
     * @param request       request to send to a server in order to bootstrap the communications.
     *                      <p>
     *                      The request body will be replaced with an
     *                      infinite stream of chunks, each representing a message to the server.
     * @param createHandler callback that takes a message sender that can be used to send out messages, and returns a
     *                      message handler receives messages from the remote.
     * @throws IOException if an error occurs while connecting
     */
    public void connect(RawHttpRequest request, Function<MessageSender, MessageHandler> createHandler)
            throws IOException {
        MessageSender sender = new MessageSender();
        MessageHandler handler = createHandler.apply(sender);

        RawHttpResponse<?> response = client.send(request
                .withBody(new StreamedChunkedBody(sender.getChunkStream())));

        if (response.getStatusCode() != 200) {
            if (client instanceof Closeable) {
                ((Closeable) client).close();
            }
            throw new RuntimeException("Server response status code is not 200: " + response.getStatusCode());
        }

        InputStream responseStream = response.getBody().map(b -> b.isChunked() ? b.asRawStream() : null)
                .orElseThrow(() ->
                        new IllegalStateException("HTTP response does not contain a chunked body"));

        InputStreamChunkDecoder responseDecoder = new InputStreamChunkDecoder(
                new ChunkedBodyParser(createStrictHttpMetadataParser()),
                responseStream);

        startMessageLoop(responseDecoder.asIterator(), sender, handler);
    }

    /**
     * Accept a request from a client to establish duplex communications as a server.
     *
     * @param request       request sent by the client.
     *                      <p>
     *                      The request body is assumed to consist of an
     *                      infinite stream of chunks, each representing a message from the client.
     * @param createHandler callback that takes a message sender that can be used to send out messages, and returns a
     *                      message handler receives messages from the remote.
     * @return response to be sent to the client to initiate duplex communication.
     */
    public RawHttpResponse<Void> accept(RawHttpRequest request,
                                        Function<MessageSender, MessageHandler> createHandler) {
        final Iterator<Chunk> receivedChunkStream = request.getBody()
                .flatMap((b) -> {
                    try {
                        return b.asChunkStream();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElseThrow(() -> new IllegalStateException("Client did not send chunked body"));
        return accept(receivedChunkStream, createHandler);
    }

    /**
     * Start accepting a potentially lazy, infinite stream of text messages
     * to establish duplex communications as a server.
     *
     * @param incomingMessageStream stream of text messages being received from a client.
     * @param createHandler         callback that takes a message sender that can be used to send out messages, and returns a
     *                              message handler receives messages from the remote.
     * @return response to be sent to the client to initiate duplex communication.
     */
    public RawHttpResponse<Void> acceptText(Iterator<String> incomingMessageStream,
                                            Function<MessageSender, MessageHandler> createHandler) {
        return accept(new TextToChunkIterator(incomingMessageStream), createHandler);
    }

    /**
     * Start accepting a potentially lazy, infinite stream of chunks (representing messages)
     * to establish duplex communications as a server.
     *
     * @param incomingMessageStream stream of messages being received from a client.
     * @param createHandler         callback that takes a message sender that can be used to send out messages, and returns a
     *                              message handler receives messages from the remote.
     * @return response to be sent to the client to initiate duplex communication.
     */
    public RawHttpResponse<Void> accept(Iterator<Chunk> incomingMessageStream,
                                        Function<MessageSender, MessageHandler> createHandler) {
        MessageSender sender = new MessageSender();
        MessageHandler handler = createHandler.apply(sender);
        startMessageLoop(incomingMessageStream, sender, handler);
        return okResponse.withBody(new StreamedChunkedBody(sender.getChunkStream()));
    }

    private void startMessageLoop(Iterator<Chunk> chunkReceiver,
                                  MessageSender sender,
                                  MessageHandler handler) {
        pinger.scheduleAtFixedRate(sender::ping, pingPeriod.toMillis(), pingPeriod.toMillis(), TimeUnit.MILLISECONDS);

        new Thread(() -> {
            try {
                while (chunkReceiver.hasNext()) {
                    Chunk chunk = chunkReceiver.next();
                    if (chunk.size() == 0) {
                        break;
                    }
                    final RawHttpHeaders extensions = chunk.getExtensions();
                    final String contentType = extensions.getFirst("Content-Type").orElse("");
                    if (contentType.equalsIgnoreCase("text/plain")) {
                        Charset charset = getCharset(extensions);
                        handler.onTextMessage(new String(chunk.getData(), charset), chunk.getExtensions());
                    } else {
                        byte[] message = chunk.getData();
                        if (!Arrays.equals(message, PING_MESSAGE)) {
                            handler.onBinaryMessage(chunk.getData(), chunk.getExtensions());
                        }
                    }
                }
                try {
                    sender.close();
                } finally {
                    handler.onClose();
                }
            } catch (Exception e) {
                Throwable error = e;
                if (e.getCause() instanceof IOException) {
                    error = e.getCause();
                }
                handler.onError(error);
                sender.close();
            } finally {
                pinger.shutdown();
            }
        }, toString()).start();
    }

    private static Charset getCharset(RawHttpHeaders extensions) {
        String charsetValue = extensions.getFirst("Charset").orElse("UTF-8");
        Charset charset;
        if (Charset.isSupported(charsetValue)) {
            charset = Charset.forName(charsetValue);
        } else {
            System.err.println("Received text message with unsupported charset: " + charsetValue +
                    ". Will try to use UTF-8 instead.");
            charset = StandardCharsets.UTF_8;
        }
        return charset;
    }

    private static class TextToChunkIterator implements Iterator<Chunk> {

        static final RawHttpHeaders plainTextHeaders = PLAIN_TEXT_HEADER.and(UTF8_HEADER);

        private final Iterator<String> textIterator;

        TextToChunkIterator(Iterator<String> textIterator) {
            this.textIterator = textIterator;
        }

        @Override
        public boolean hasNext() {
            return textIterator.hasNext();
        }

        @Override
        public Chunk next() {
            String text = textIterator.next();
            return new Chunk(plainTextHeaders, text.getBytes(UTF_8));
        }
    }

}
