{{ define title "RawHTTP" }}
{{ define moduleName "HTTP Client" }}
{{ define path baseURL + "/docs/http-client.html" }}
{{ include /processed/fragments/_header.html }}
{{ include /processed/fragments/_nav.html }}

### Using a HTTP client

To make it easier to send HTTP requests, you can use a `TcpRawHttpClient`:

```java
import rawhttp.core.*;
import rawhttp.core.client.*;

TcpRawHttpClient client = new TcpRawHttpClient();
RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest("...");
RawHttpResponse<?> response = client.send(request);
```

The client keeps connections alive if possible, so after you're done with it, close it:

```java
client.close();
```

### Configuring a HTTP client

To configure a HTTP client, use the constructor that takes an instance of
`TcpRawHttpClient.TcpRawHttpClientOptions`:

```java
import rawhttp.core.client.*;

TcpRawHttpClient client = new TcpRawHttpClient(new TcpRawHttpClient.TcpRawHttpClientOptions() {
    @Override
    public Socket getSocket(URI uri) {
        // TODO implement creation of a Socket based on the URI
        return null;
    }

    @Override
    public RawHttpResponse<Void> onResponse(Socket socket, URI uri, RawHttpResponse<Void> httpResponse) throws IOException {
        // TODO return a modified HTTP response
        return httpResponse;
    }

    @Override
    public void close() throws IOException {
        // TODO the client was closed, perform cleanup
    }
});
```

Example implementation of `TcpRawHttpClientOptions` which only allows HTTPS connections
and always forces HTTP responses to be consumed fully, including its body, by calling `response.eagerly()`:

```java
class SafeHttpClientOptions implements TcpRawHttpClient.TcpRawHttpClientOptions {
    @Override
    public Socket getSocket(URI uri) {
        String host = uri.getHost();
        int port = uri.getPort();
        if (port < 1) {
            port = 443;
        }
        try {
            // only allow HTTPS connections!
            return SSLSocketFactory.getDefault().createSocket(host, port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public RawHttpResponse<Void> onResponse(Socket socket, URI uri, RawHttpResponse<Void> httpResponse) throws IOException {
        return httpResponse.eagerly();
    }

    @Override
    public void close() throws IOException {
        // TODO the client was closed, perform cleanup
    }
}
```

To configure a HTTP Client to support TLS connections using a specific keystore/truststore,
you can use the `rawhttp.core.server.TlsConfiguration` class as follows:

```java
var sslContext = TlsConfiguration.createSSLContext(
    keystoreURL, KEYSTORE_PASS,
    truststoreURL, TRUSTSTORE_PASS);

var safeHttpClient = new TcpRawHttpClient(TlsConfiguration.clientOptions(sslContext));
```

Conversely, you can also create a HTTP client that is unsafe to use, but handy for quickly testing something,
by ignoring TLS certificates:

```java
var unsafeClient = new TcpRawHttpClient(new TlsCertificateIgnorer.UnsafeHttpClientOptions());
```

> Do not blame me if this is used in production and you get hacked!

<hr>

{{ include /processed/fragments/_footer.html }}
