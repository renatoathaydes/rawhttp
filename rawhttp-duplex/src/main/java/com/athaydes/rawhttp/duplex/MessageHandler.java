package com.athaydes.rawhttp.duplex;

/**
 * A handler of messages being received from a server.
 */
public interface MessageHandler {

    /**
     * Callback for receiving text messages.
     *
     * @param message the text message
     */
    default void onTextMessage(String message) {
    }

    /**
     * Callback for receiving binary messages.
     *
     * @param message the binary message
     */
    default void onBinaryMessage(byte[] message) {
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
