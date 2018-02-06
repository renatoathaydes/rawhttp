package com.athaydes.rawhttp.core.client;

import com.athaydes.rawhttp.core.RawHttp;
import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class TcpRawHttpClient implements RawHttpClient<Void>, Closeable {

    private final Map<String, Socket> socketByHost = new HashMap<>(4);

    @Override
    public RawHttpResponse<Void> send(RawHttpRequest request) throws IOException {
        String host = request.getUri().getHost();
        @Nullable Socket socket = socketByHost.get(host);

        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            int port = request.getUri().getPort();
            if (port == -1) {
                port = "https".equalsIgnoreCase(request.getUri().getScheme()) ?
                        43 : 80;
            }
            socket = new Socket(host, port);
            socketByHost.put(host, socket);
        }

        request.writeTo(socket.getOutputStream());
        return new RawHttp().parseResponse(socket.getInputStream());
    }

    public void close() {
        for (Socket socket : socketByHost.values()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
