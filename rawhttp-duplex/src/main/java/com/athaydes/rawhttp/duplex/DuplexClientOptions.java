package com.athaydes.rawhttp.duplex;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.client.TcpRawHttpClient;

final class DuplexClientOptions implements TcpRawHttpClient.TcpRawHttpClientOptions {

    private final TcpRawHttpClient.TcpRawHttpClientOptions defaultOptions = new TcpRawHttpClient.DefaultOptions();
    int socketTimeout = 15 * 60 * 1_000;

    @Override
    public Socket getSocket(URI uri) {
        Socket socket = defaultOptions.getSocket(uri);
        try {
            socket.setSoTimeout(socketTimeout);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        return socket;
    }

    @Override
    public RawHttpResponse<Void> onResponse(Socket socket, URI uri, RawHttpResponse<Void> httpResponse) throws
            IOException {
        return defaultOptions.onResponse(socket, uri, httpResponse);
    }

}
