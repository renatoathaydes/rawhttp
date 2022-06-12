{{ define title "RawHTTP" }}
{{ define moduleName "HTTP Server" }}
{{ define path baseURL + "/docs/http-server.html" }}
{{ include /processed/fragments/_header.html }}
{{ include /processed/fragments/_nav.html }}

### Using a HTTP server

Even though you can write a simple HTTP server using only a `ServerSocket` and `RawHttp` to parse requests and
responses, RawHTTP also offers a simple server implementation that makes things easier:

```java
import rawhttp.core.*;
import rawhttp.core.server.*;

RawHttpServer server = new TcpRawHttpServer(8086);
RawHttp http = new RawHttp();

server.start(request -> {
    RawHttpResponse<?> response = http.parseResponse(...);
    return Optional.of(response);
});
```

Stop the server once you don't need it anymore:

```java
server.stop();
```

### Configuring a HTTP server

As with the HTTP client, to configure a HTTP server, pass the options into the constructor.

An example implementation of `TcpRawHttpServer.TcpRawHttpServerOptions`:

```java
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
```

The default options used by the server creates an unlimited, cached Thread pool to serve requests
and automatically inserts a `Server` and `Date` headers to all responses by implementing
the `autoHeadersSupplier()` method. If you provide your own options, this behaviour is overridden.

### Using a TLS certificate

By implementing `TcpRawHttpServerOptions`, you can provide `SSLSocket`s using whatever Socket factory you have
hanging around... but to make it easier, RawHTTP provides the helper class `rawhttp.core.server.TlsConfiguration`. 

For example, to configure your server to use TLS with a certain keystore:

```java
var sslContext = TlsConfiguration.createSSLContext(KEYSTORE, KEYSTORE_PASS);

var server = new TcpRawHttpServer(TlsConfiguration.serverOptions(sslContext, PORT))
```

<hr>

{{ include /processed/fragments/_footer.html }}
