---
title: "HTTP Client"
date: 2018-05-10T14:02:44+02:00
draft: true
---

### Using a HTTP client

To make it easier to send HTTP requests, you can use a `TcpRawHttpClient`:

{{< highlight java >}}
import com.athaydes.rawhttp.core.*;
import com.athaydes.rawhttp.core.client.*;

TcpRawHttpClient client = new TcpRawHttpClient();
RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest("...");
RawHttpResponse<?> response = client.send(request);
{{< / highlight >}}

The client keeps connections alive if possible, so after you're done with it, close it:

{{< highlight java >}}
client.close();
{{< / highlight >}}

### Configuring a HTTP client

To configure a HTTP client, use the constructor that takes an instance of
`com.athaydes.rawhttp.core.client.TcpRawHttpClient.TcpRawHttpClientOptions`:

{{< highlight java >}}
import com.athaydes.rawhttp.core.client.*;

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
{{< / highlight >}}

Example implementation of `TcpRawHttpClientOptions` which only allows HTTPS connections
and always forces HTTP responses to be consumed fully, including its body, by calling `response.eagerly()`:

{{< highlight java >}}
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
{{< / highlight >}}

<hr>

[Index](/docs) [Next: HTTP Server](/docs/http-server)