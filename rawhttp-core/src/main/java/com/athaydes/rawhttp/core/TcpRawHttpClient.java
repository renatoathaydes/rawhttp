package com.athaydes.rawhttp.core;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TcpRawHttpClient implements RawHttpClient<Void> {

    private final Socket socket;

    public TcpRawHttpClient(Socket socket) {
        this.socket = socket;
    }

    @Override
    public RawHttpResponse<Void> send(RawHttpRequest request) throws IOException {
        socket.getOutputStream().write(request.toString().getBytes(StandardCharsets.UTF_8));
        return new RawHttp().parseResponse(socket.getInputStream());
    }

}
