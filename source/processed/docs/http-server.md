---
title: "HTTP Server"
date: 2018-05-10T14:02:44+02:00
draft: false
---

### Using a HTTP server

Even though you can write a simple HTTP server using only a `ServerSocket` and `RawHttp` to parse requests and
responses, RawHTTP also offers a simple server implementation that makes things easier:

{{< highlight java >}}
import rawhttp.core.*;
import rawhttp.core.server.*;

RawHttpServer server = new TcpRawHttpServer(8086);
RawHttp http = new RawHttp();

server.start(request -> {
    RawHttpResponse<?> response = http.parseResponse(...);
    return Optional.of(response);
});
{{< / highlight >}}

Stop the server once you don't need it anymore:

{{< highlight java >}}
server.stop();
{{< / highlight >}}

### Configuring a HTTP server

As with the HTTP client, to configure a HTTP server, pass the options into the constructor.

An example implementation of `TcpRawHttpServer.TcpRawHttpServerOptions`:

{{< highlight java >}}
class ExampleTcpRawHttpServerOptions implements TcpRawHttpServer.TcpRawHttpServerOptions {

    @Override
    public ServerSocket getServerSocket() throws IOException {
        return new ServerSocket(8080);
    }

    @Override
    public RawHttp getRawHttp() {
        return new RawHttp();
    }

    @Override
    public ExecutorService createExecutorService() {
        return Executors.newCachedThreadPool();
    }

    @Override
    public Optional<EagerHttpResponse<Void>> serverErrorResponse(RawHttpRequest request) {
        return Optional.empty(); // use the default 500 error response
    }

    @Override
    public Optional<EagerHttpResponse<Void>> notFoundResponse(RawHttpRequest request) {
        return Optional.empty(); // use the default 404 error response
    }

    @Override
    public RawHttpResponse<Void> onResponse(RawHttpRequest request, RawHttpResponse<Void> response) {
        // TODO this is a good place to log the request and response
        // or add standard HTTP response headers, like "Server" and "Date"
        return response;
    }

    @Override
    public void close() throws IOException {
    }
}
{{< / highlight >}}

The default options used by the server creates an unlimited, cached Thread pool to serve requests
and automatically inserts a `Server` and `Date` headers to all responses by implementing
the `autoHeadersSupplier()` method. If you provide your own options, this behaviour is overridden.

<hr>

[Index](/rawhttp/docs)