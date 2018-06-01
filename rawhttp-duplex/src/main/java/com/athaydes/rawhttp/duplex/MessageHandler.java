package com.athaydes.rawhttp.duplex;

public interface MessageHandler {

    default void onTextMessage(String message) {
    }

    default void onBinaryMessage(byte[] message) {
    }

    default void onError(Throwable error) {
        error.printStackTrace();
    }

    default void onClose() {
    }

}
