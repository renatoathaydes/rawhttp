package com.athaydes.rawhttp.duplex;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
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

    static final RawHttpHeaders PLAIN_TEXT_HEADERS = RawHttpHeaders.newBuilder()
            .with("Content-Type", "text/plain")
            .build();

    static final byte[] PING_MESSAGE = new byte[]{'\n'};

    private final LinkedBlockingDeque<Object> messages = new LinkedBlockingDeque<>(10);
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
        if (isClosed.get()) {
            throw new IllegalStateException("Sender has been closed");
        }
        if (message.isEmpty()) {
            ping();
        } else {
            messages.addLast(message);
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
        if (isClosed.get()) {
            throw new IllegalStateException("Sender has been closed");
        }
        if (message.length == 0) {
            ping();
        } else {
            messages.addLast(message);
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
            messages.addLast(new byte[0]);
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
                Object message = null;
                while (message == null) {
                    try {
                        message = messages.poll(5, TimeUnit.MINUTES);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                ChunkedBodyContents.Chunk chunk;
                if (message instanceof byte[]) {
                    chunk = new ChunkedBodyContents.Chunk(RawHttpHeaders.empty(), (byte[]) message);
                } else if (message instanceof String) {
                    chunk = new ChunkedBodyContents.Chunk(PLAIN_TEXT_HEADERS, ((String) message).getBytes(StandardCharsets.UTF_8));
                } else {
                    throw new IllegalStateException("Unknown message type: " + message);
                }
                hasMoreChunks = chunk.size() > 0;
                return chunk;
            }
        };
    }

}
