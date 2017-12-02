package com.athaydes.rawhttp.core.client;

import com.athaydes.rawhttp.core.RawHttp;
import com.athaydes.rawhttp.core.RawHttpClient;
import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;

import java.io.IOException;
import java.net.Socket;

public class TcpRawHttpClient implements RawHttpClient<Void> {

    private final Socket socket;

    public TcpRawHttpClient(Socket socket) {
        this.socket = socket;
    }

    @Override
    public RawHttpResponse<Void> send(RawHttpRequest request) throws IOException {
        request.writeTo(socket.getOutputStream());
        return new RawHttp().parseResponse(socket.getInputStream());
    }

}
