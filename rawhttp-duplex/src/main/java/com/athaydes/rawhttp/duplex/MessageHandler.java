package com.athaydes.rawhttp.duplex;

import rawhttp.core.RawHttpHeaders;

/**
 * A handler of messages being received from a server.
 * <p>
 * Implementations that desire to receive text messages should override either
 * {@link MessageHandler#onTextMessage(String)} OR {@link MessageHandler#onTextMessage(String, RawHttpHeaders)},
 * not both, in  most cases. {@link RawHttpDuplex} only calls the latter method, but its default implementation
 * delegates to the former, so users can choose which method to use.
 * <p>
 * The same applies to binary messages: override either {@link MessageHandler#onBinaryMessage(byte[])} OR
 * {@link MessageHandler#onBinaryMessage(byte[], RawHttpHeaders)}.
 */
public interface MessageHandler {

    /**
     * Callback for receiving text messages.
     * <p>
     * This method is NOT called by {@link RawHttpDuplex} directly. It is called by
     * the default implementation of {@link MessageHandler#onTextMessage(String, RawHttpHeaders)}, which just
     * drops the extensions parameter. If you override that method, this method will not necessarily
     * be ever called.
     *
     * @param message the text message
     */
    default void onTextMessage(String message) {
    }

    /**
     * Callback for receiving text messages.
     * <p>
     * The default implementation of this method delegates to {@link MessageHandler#onTextMessage(String)}.
     *
     * @param message    the text message
     * @param extensions the chunk extensions
     */
    default void onTextMessage(String message, RawHttpHeaders extensions) {
        onTextMessage(message);
    }

    /**
     * Callback for receiving binary messages.
     * <p>
     * This method is NOT called by {@link RawHttpDuplex} directly. It is called by
     * the default implementation of {@link MessageHandler#onBinaryMessage(byte[], RawHttpHeaders)}, which just
     * drops the extensions parameter. If you override that method, this method will not necessarily
     * be ever called.
     *
     * @param message the binary message
     */
    default void onBinaryMessage(byte[] message) {
    }

    /**
     * Callback for receiving binary messages.
     * <p>
     * The default implementation of this method delegates to {@link MessageHandler#onBinaryMessage(byte[])}.
     *
     * @param message    the binary message
     * @param extensions the chunk extensions
     */
    default void onBinaryMessage(byte[] message, RawHttpHeaders extensions) {
        onBinaryMessage(message);
    }

    /**
     * Callback for handling errors.
     *
     * @param error the error
     */
    default void onError(Throwable error) {
        error.printStackTrace();
    }

    /**
     * Callback for handling a connection being closed.
     */
    default void onClose() {
    }

}
