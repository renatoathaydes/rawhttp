package com.athaydes.rawhttp.duplex;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.body.ChunkedBodyContents;

/**
 * A sender of messages.
 * <p>
 * Used by a client to send messages to a server.
 */
public final class MessageSender {

    private static final String PLAIN_TEXT = "text/plain";

    static final RawHttpHeaders PLAIN_TEXT_HEADER = RawHttpHeaders.newBuilder()
            .with("Content-Type", PLAIN_TEXT)
            .build();

    static final RawHttpHeaders UTF8_HEADER = RawHttpHeaders.newBuilder()
            .with("Charset", "UTF-8")
            .build();

    static final byte[] PING_MESSAGE = new byte[]{'\n'};

    private final LinkedBlockingDeque<Message> messages = new LinkedBlockingDeque<>(10);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final AtomicBoolean gotStream = new AtomicBoolean(false);

    /**
     * Send a text message.
     * <p>
     * The message should not be empty because sending an empty chunk would signal the end of the transmission.
     * For this reason, an empty message is turned into a "ping" (see {@link MessageSender#ping()} message.
     *
     * @param message the text message
     */
    public void sendTextMessage(String message) {
        sendTextMessage(message, RawHttpHeaders.empty());
    }

    /**
     * Send a text message.
     * <p>
     * The message should not be empty because sending an empty chunk would signal the end of the transmission.
     * For this reason, an empty message is turned into a "ping" (see {@link MessageSender#ping()} message.
     *
     * @param message    the text message
     * @param extensions chunk extensions.
     *                   <p>
     *                   Each message is wrapped into a chunk. The provided extensions are attached to the chunk
     *                   wrapping the given message.
     */
    public void sendTextMessage(String message, RawHttpHeaders extensions) {
        if (isClosed.get()) {
            throw new IllegalStateException("Sender has been closed");
        }
        if (message.isEmpty()) {
            ping();
        } else {
            RawHttpHeaders plainTextExtensions = withPlainTextExtension(extensions);
            Charset charset = extractCharset(plainTextExtensions);
            messages.addLast(new Message(message.getBytes(charset), plainTextExtensions));
        }
    }

    /**
     * Send a binary message.
     * <p>
     * The message should not be empty because sending an empty chunk would signal the end of the transmission.
     * For this reason, an empty message is turned into a "ping" (see {@link MessageSender#ping()} message.
     *
     * @param message the binary message
     */
    public void sendBinaryMessage(byte[] message) {
        sendBinaryMessage(message, RawHttpHeaders.empty());
    }

    /**
     * Send a binary message.
     * <p>
     * The message should not be empty because sending an empty chunk would signal the end of the transmission.
     * For this reason, an empty message is turned into a "ping" (see {@link MessageSender#ping()} message.
     *
     * @param message    the binary message
     * @param extensions chunk extensions.
     *                   <p>
     *                   Each message is wrapped into a chunk. The provided extensions are attached to the chunk
     *                   wrapping the given message.
     */
    public void sendBinaryMessage(byte[] message, RawHttpHeaders extensions) {
        if (isClosed.get()) {
            throw new IllegalStateException("Sender has been closed");
        }
        if (message.length == 0) {
            ping();
        } else {
            messages.addLast(new Message(message, extensions));
        }
    }

    /**
     * Ping the receiver.
     * <p>
     * This method may be useful to avoid the connection timing out during long periods of inactivity.
     * <p>
     * Ping is implemented by sending a single new-line (LF) character to the receiver, which is supposed
     * to ignore such message.
     */
    public void ping() {
        sendBinaryMessage(PING_MESSAGE);
    }

    /**
     * Close the connection.
     */
    public void close() {
        if (!isClosed.getAndSet(true)) {
            messages.addLast(new Message(new byte[0], RawHttpHeaders.empty()));
        }
    }

    Iterator<ChunkedBodyContents.Chunk> getChunkStream() {
        if (gotStream.getAndSet(true)) {
            throw new IllegalStateException("Chunk stream was already returned");
        }
        return new Iterator<ChunkedBodyContents.Chunk>() {

            boolean hasMoreChunks = true;

            @Override
            public boolean hasNext() {
                return hasMoreChunks;
            }

            @Override
            public ChunkedBodyContents.Chunk next() {
                if (!hasMoreChunks) {
                    throw new IllegalStateException("No more chunks are available");
                }
                Message message = null;
                while (message == null) {
                    try {
                        message = messages.poll(5, TimeUnit.MINUTES);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                ChunkedBodyContents.Chunk chunk = new ChunkedBodyContents.Chunk(
                        message.extensions, message.data);

                hasMoreChunks = chunk.size() > 0;
                return chunk;
            }
        };
    }

    private static Charset extractCharset(RawHttpHeaders extensions) {
        String charset = extensions.getFirst("Charset").orElse("UTF-8");
        try {
            return Charset.forName(charset);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static RawHttpHeaders withPlainTextExtension(RawHttpHeaders extensions) {
        RawHttpHeaders result = extensions.and(PLAIN_TEXT_HEADER);
        Optional<String> charset = result.getFirst("Charset");
        if (!charset.isPresent() || !Charset.isSupported(charset.get())) {
            result = result.and(UTF8_HEADER);
        }
        return result;
    }

    private static class Message {

        final byte[] data;
        final RawHttpHeaders extensions;

        public Message(byte[] data, RawHttpHeaders extensions) {
            this.data = data;
            this.extensions = extensions;
        }
    }
}
