package com.athaydes.rawhttp.duplex;

import com.athaydes.rawhttp.duplex.body.StreamedChunkedBody;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Stream;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.body.ChunkedBodyContents;
import rawhttp.core.body.ChunkedBodyParser;
import rawhttp.core.body.InputStreamChunkDecoder;
import rawhttp.core.client.TcpRawHttpClient;

import static com.athaydes.rawhttp.duplex.MessageSender.PING_MESSAGE;
import static com.athaydes.rawhttp.duplex.MessageSender.PLAIN_TEXT_HEADERS;
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
 * The way duplex communication is achieved using only HTTP/1.1 standard mechanisms is as follows:
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
 * Because each chunk may contain "extensions", {@link RawHttpDuplex} sends a single extension to idenfity text
 * messages: {@code Content-Type: text/plain}. If the chunk does not contain this extension, then it is considered
 * to be a binary message.
 */
public class RawHttpDuplex {

    private final RawHttpResponse<Void> okResponse;
    private final TcpRawHttpClient client;

    /**
     * Create a new instance of {@link RawHttpDuplex} using the default configuration.
     */
    public RawHttpDuplex() {
        this(new TcpRawHttpClient(new DuplexClientOptions()));
    }

    /**
     * Create a new instance of {@link RawHttpDuplex} that uses the given client to connect to a remote server.
     *
     * @param client used to connect to a server
     */
    public RawHttpDuplex(TcpRawHttpClient client) {
        this.okResponse = new RawHttp().parseResponse("200 OK");
        this.client = client;
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
            client.close();
            throw new RuntimeException("Server response status code is not 200: " + response.getStatusCode());
        }

        InputStream responseStream = response.getBody().map(b -> b.isChunked() ? b.asStream() : null)
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
     */
    public RawHttpResponse<Void> accept(RawHttpRequest request,
                                        Function<MessageSender, MessageHandler> createHandler) {
        final Iterator<ChunkedBodyContents.Chunk> receivedChunkStream = request.getBody()
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
     */
    public RawHttpResponse<Void> acceptText(Stream<String> incomingMessageStream,
                                            Function<MessageSender, MessageHandler> createHandler) {
        return accept(incomingMessageStream.map(text ->
                        new ChunkedBodyContents.Chunk(PLAIN_TEXT_HEADERS, text.getBytes(UTF_8))).iterator(),
                createHandler);
    }

    /**
     * Start accepting a potentially lazy, infinite stream of chunks (representing messages)
     * to establish duplex communications as a server.
     *
     * @param incomingMessageStream stream of messages being received from a client.
     * @param createHandler         callback that takes a message sender that can be used to send out messages, and returns a
     *                              message handler receives messages from the remote.
     */
    public RawHttpResponse<Void> accept(Iterator<ChunkedBodyContents.Chunk> incomingMessageStream,
                                        Function<MessageSender, MessageHandler> createHandler) {
        MessageSender sender = new MessageSender();
        MessageHandler handler = createHandler.apply(sender);
        startMessageLoop(incomingMessageStream, sender, handler);
        return okResponse.withBody(new StreamedChunkedBody(sender.getChunkStream()));
    }

    private void startMessageLoop(Iterator<ChunkedBodyContents.Chunk> chunkReceiver, MessageSender sender, MessageHandler handler) {
        new Thread(() -> {
            try {
                while (chunkReceiver.hasNext()) {
                    ChunkedBodyContents.Chunk chunk = chunkReceiver.next();
                    if (chunk.size() == 0) {
                        break;
                    }
                    String contentType = chunk.getExtensions().getFirst("Content-Type").orElse("");
                    if (contentType.equalsIgnoreCase("text/plain")) {
                        handler.onTextMessage(new String(chunk.getData()));
                    } else {
                        byte[] message = chunk.getData();
                        if (!Arrays.equals(message, PING_MESSAGE)) {
                            handler.onBinaryMessage(chunk.getData());
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
            }
        }).start();
    }

}
