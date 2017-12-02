# RawHTTP

A library to make it easy to deal with raw HTTP.

Raw HTTP is really simple in 99.9% of cases.

For example, the raw HTTP request you would make to fetch a resource from a web server looks like this:

> The example below is taken from the [HTTP 1.1 RFC 7230](https://tools.ietf.org/html/rfc7230#section-2.1).

```
GET /hello.txt HTTP/1.1
User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3
Host: www.example.com
Accept-Language: en, mi
```

With a traditional Java HttpClient, the same request would require learning the library's API
(which, in the case of HttpComponents, is infamous for changing completely across versions)
and making some non-trivial calls:

```java
// HttpComponents library usage example

CloseableHttpClient httpclient = HttpClients.createDefault();
CloseableHttpResponse response = null;
try {
    HttpUriRequest httpRequest = RequestBuilder.get()
            .addHeader(HttpHeaders.USER_AGENT, "curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3")
            .addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en, mi")
            .setUri(URI.create("http://www.example.com"))
            .build();
    response = httpclient.execute(httpRequest);

    // use response
    ...
} catch (IOException e) {
    e.printStackTrace();
} finally {
    if (response != null) try {
        response.close();
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

With **RawHTTP**, you just send it as-is:

```java
RawHttpRequest request = new RawHttp().parseRequest(
        "GET / HTTP/1.0\n" +
        "User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3\n" +
        "Host: www.example.com\n" +
        "Accept-Language: en, mi");

try (TcpRawHttpClient client = new TcpRawHttpClient()) {
    RawHttpResponse<?> rawResponse = client.send(request).eagerly();

    // use response
    ...
} catch (IOException e) {
    e.printStackTrace();
}
```

Almost no API to learn.

You just need to know how a HTTP request should look like, how to create a client and how to send the request.

## Creating a client

RawHTTP has the following clients so far:

* `TcpRawHttpClient` - uses `java.net.Socket` directly to send requests

Example:

```java
RawHttpClient<?> client = new TcpRawHttpClient();
```

* `RawHttpComponentsClient` - based on HttpComponents's HttpClient

```java
// use a default instance of CloseableHttpClient
RawHttpClient<?> client = new RawHttpComponentsClient();

// or create and confiture your own client, then pass it into the constructor
CloseableHttpClient httpClient = HttpClients.createDefault();
RawHttpClient<?> client = new RawHttpComponentsClient(httpClient);
```

> Notice that `RawHttpClient` instances are generic because depending on the actual implementation used,
 the client may return responses containing a `libResponse`. In the case of the `RawHttpComponentsClient`,
 the full type of the client is `RawHttpClient<CloseableHttpResponse>`.
 Because `TcpRawHttpClient` does not have a `libResponse`, its full type is `RawHttpClient<Void>`.

## Sending a request

To create a request, you just parse it using the `RawHttp` class as shown in the example.

Once you have a request, send it using the client you've just created.

```java
RawHttpRequest request = new RawHttp().parseRequest("GET localhost:8080/hello");
RawHttpResponse<?> rawResponse = client.send(request).eagerly();
```

Unless you want to take care of streaming the response body yourself, always call `eagerly()`
as shown above to consume the full response body (allowing the connection to be re-used).

## Using a client is not raw enough for you?

No problem, send the request using a raw `Socket`:

```java
RawHttp rawHttp = new RawHttp();

RawHttpRequest request = rawHttp.parseRequest("GET localhost:8082/hello HTTP/1.0");
Socket socket = new Socket("localhost", 8082);
request.writeTo(socket.getOutputStream());

EagerHttpResponse<?> response = rawHttp.parseResponse(socket.getInputStream()).eagerly();
```

Requests and Responses can be written to an `OutputStream` directly, so you don't need no fancy client
to do it! The only benefit of using `TcpRawHttpClient` is that it will automatically create/close the
`Socket`s for you, sending the requests to the right connections (notice that using the code above,
you can actually send a request to any server regardless of what the `Host` header says!).

You could even write your own rudimentary HTTP server quite easily using that:

```java
RawHttp http = new RawHttp();
ServerSocket server = new ServerSocket(8083);

new Thread(() -> {
    try {
        Socket client = server.accept();
        RawHttpRequest request = http.parseRequest(client.getInputStream());

        if (request.getUri().getPath().equals("/saysomething")) {
            http.parseResponse("HTTP/1.1 200 OK\n" +
                    "Content-Type: text/plain\n" +
                    "Content-Length: 9\n" +
                    "\n" +
                    "something").writeTo(client.getOutputStream());
        } else {
            http.parseResponse("HTTP/1.1 404 Not Found\n" +
                    "Content-Type: text/plain\n" +
                    "Content-Length: 0\n" +
                    "\n").writeTo(client.getOutputStream());
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
}).start();
```

## Samples

Several samples showing how to use RawHTTP, including all [examples](samples/src/test/java/com/athaydes/rawhttp/samples/JavaSample.java)
in this page, can be found in the [samples](samples) project.
