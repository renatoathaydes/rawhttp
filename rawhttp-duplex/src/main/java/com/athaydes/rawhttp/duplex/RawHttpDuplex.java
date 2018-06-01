package com.athaydes.rawhttp.duplex;

import com.athaydes.rawhttp.duplex.body.StreamedChunkedBody;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Stream;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.body.ChunkedBodyContents;
import rawhttp.core.body.ChunkedBodyParser;
import rawhttp.core.body.InputStreamChunkDecoder;
import rawhttp.core.client.RawHttpClient;
import rawhttp.core.client.TcpRawHttpClient;

import static com.athaydes.rawhttp.duplex.MessageSender.PLAIN_TEXT_HEADERS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static rawhttp.core.HttpMetadataParser.createStrictHttpMetadataParser;

public class RawHttpDuplex {

    private final RawHttpResponse<Void> okResponse;
    private final RawHttpClient<?> client;

    public RawHttpDuplex() {
        this(new TcpRawHttpClient());
    }

    public RawHttpDuplex(RawHttpClient<?> client) {
        this.okResponse = new RawHttp().parseResponse("200 OK");
        this.client = client;
    }

    public void connect(RawHttpRequest request, Function<MessageSender, MessageHandler> createHandler)
            throws IOException {
        MessageSender sender = new MessageSender();
        MessageHandler handler = createHandler.apply(sender);

        RawHttpResponse<?> response = client.send(request
                .withBody(new StreamedChunkedBody(sender.getChunkStream())));

        if (response.getStatusCode() != 200) {
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

    public RawHttpResponse<Void> acceptText(Stream<String> chunkReceiver,
                                            Function<MessageSender, MessageHandler> createHandler) {
        return accept(chunkReceiver.map(text ->
                        new ChunkedBodyContents.Chunk(PLAIN_TEXT_HEADERS, text.getBytes(UTF_8))).iterator(),
                createHandler);
    }

    public RawHttpResponse<Void> accept(Iterator<ChunkedBodyContents.Chunk> chunkReceiver,
                                        Function<MessageSender, MessageHandler> createHandler) {
        MessageSender sender = new MessageSender();
        MessageHandler handler = createHandler.apply(sender);
        startMessageLoop(chunkReceiver, sender, handler);
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
                        handler.onBinaryMessage(chunk.getData());
                    }
                }
                try {
                    sender.close();
                } finally {
                    handler.onClose();
                }
            } catch (Exception e) {
                handler.onError(e);
            }
        }).start();
    }

}
