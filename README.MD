# RawHTTP

A library to make it easy to deal with raw HTTP 1.1, as defined by [RFC-7230](https://tools.ietf.org/html/rfc7230),
and most of HTTP 1.0 ([RFC-1945](https://tools.ietf.org/html/rfc1945)).

> For details using RawHTTP, see the [blog post](https://sites.google.com/a/athaydes.com/renato-athaydes/posts/announcingrawhttp-ajvmlibraryforhandlingrawhttp)
 I wrote about it!

Raw HTTP is really simple in 99.9% of cases.

For example, the raw HTTP request you would make to fetch a resource from a web server looks like this:

> The example below is taken from the [HTTP 1.1 RFC 7230](https://tools.ietf.org/html/rfc7230#section-2.1).

```
GET /hello.txt HTTP/1.1
User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3
Host: www.example.com
Accept-Language: en, mi
```

To send that request to a HTTP server using RawHTTP, you just parse the Request and stream it out via a `Socket`:

```java
RawHttp rawHttp = new RawHttp();

RawHttpRequest request = rawHttp.parseRequest(
    "GET /hello.txt HTTP/1.1\r\n" +
    "User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3\r\n" +
    "Host: www.example.com\r\n" +
    "Accept-Language: en, mi");
Socket socket = new Socket("www.example.com", 80);
request.writeTo(socket.getOutputStream());
```

To read the response, it's just as easy:

```
RawHttpResponse<?> response = rawHttp.parseResponse(socket.getInputStream());

// call "eagerly()" in order to download the body
System.out.println(response.eagerly());
```

Which prints the complete response:

```
HTTP/1.1 404 Not Found
Accept-Ranges: bytes
Cache-Control: max-age=604800
Content-Type: text/html
Date: Mon, 04 Dec 2017 21:19:04 GMT
Expires: Mon, 11 Dec 2017 21:19:04 GMT
Last-Modified: Sat, 02 Dec 2017 02:10:22 GMT
Server: ECS (lga/1389)
Vary: Accept-Encoding
X-Cache: 404-HIT
Content-Length: 1270


<!doctype html>
...
```

A `RawHttpResponse`, just like a `RawHttpRequest` can be written to a `File`'s, `ServerSocket`'s
or any other `OutpuStream`:

```java
try (FileOutputStream out = new FileOutputStream(responseFile)) {
    response.writeTo(out);
}
```

That simple!

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

## Using a client

RawHTTP has the following clients so far:

* `TcpRawHttpClient` - manages `java.net.Socket` instances to send requests to the correct host.

Example usage:

```java
RawHttpClient<?> client = new TcpRawHttpClient();
EagerHttpResponse<?> response = client.send(request).eagerly();
```

* `RawHttpComponentsClient` - based on HttpComponents's HttpClient.

You can use this if you need support for external specifications, such as
cookies ([RFC-6265](https://tools.ietf.org/html/rfc6265)), or basic-auth, for example.

Example usage:

```java
// use a default instance of CloseableHttpClient
RawHttpClient<?> client = new RawHttpComponentsClient();

// or create and configure your own client, then pass it into the constructor
CloseableHttpClient httpClient = HttpClients.createDefault();
RawHttpClient<?> client = new RawHttpComponentsClient(httpClient);
```

> Notice that `RawHttpClient` instances are generic because depending on the actual implementation used,
 the client may return responses containing a `libResponse`. In the case of the `RawHttpComponentsClient`,
 the full type of the client is `RawHttpClient<CloseableHttpResponse>`.
 Because `TcpRawHttpClient` does not have a `libResponse`, its full type is `RawHttpClient<Void>`.

Unless you want to take care of streaming the response body later, always call `eagerly()`
as shown above to consume the full response body (allowing the connection to be re-used).

## Samples

Several samples showing how to use RawHTTP, including all [examples](samples/src/test/java/com/athaydes/rawhttp/samples/JavaSample.java)
in this page, can be found in the [samples](samples) project.
