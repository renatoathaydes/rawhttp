package com.athaydes.rawhttp.duplex;

import rawhttp.core.client.TcpRawHttpClient;

import java.net.Socket;
import java.net.SocketException;
import java.net.URI;

final class DuplexClientOptions extends TcpRawHttpClient.DefaultOptions {

    int socketTimeout = 60 * 1_000;

    @Override
    public Socket getSocket(URI uri) {
        Socket socket = super.getSocket(uri);
        try {
            socket.setSoTimeout(socketTimeout);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        return socket;
    }
}
