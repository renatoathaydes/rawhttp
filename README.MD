# RawHTTP

| Module Name | Latest Version | Documentation |
|-------------| :--------------:|--------------|
| rawhttp-core | [![rawhttp-core](https://img.shields.io/maven-central/v/com.athaydes.rawhttp/rawhttp-core.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.athaydes.rawhttp%22%20AND%20a:%22rawhttp-core%22) | [RawHTTP Core](https://renatoathaydes.github.io/rawhttp/docs/index.html) |
| rawhttp-cli | [![rawhttp-cli](https://img.shields.io/maven-central/v/com.athaydes.rawhttp/rawhttp-cli.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.athaydes.rawhttp%22%20AND%20a:%22rawhttp-cli%22) | [RawHTTP CLI](https://renatoathaydes.github.io/rawhttp/rawhttp-modules/cli.html) |
| rawhttp-duplex | [![rawhttp-duplex](https://img.shields.io/maven-central/v/com.athaydes.rawhttp/rawhttp-duplex.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.athaydes.rawhttp%22%20AND%20a:%22rawhttp-duplex%22) | [RawHTTP Duplex](https://renatoathaydes.github.io/rawhttp/rawhttp-modules/duplex.html) |
| rawhttp-cookies | [![rawhttp-cookies](https://img.shields.io/maven-central/v/com.athaydes.rawhttp/rawhttp-cookies.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.athaydes.rawhttp%22%20AND%20a:%22rawhttp-cookies%22) | [RawHTTP Cookies](https://renatoathaydes.github.io/rawhttp/rawhttp-modules/cookies.html) |
| rawhttp-req-in-edit | [![rawhttp-req-in-edit](https://img.shields.io/maven-central/v/com.athaydes.rawhttp/rawhttp-req-in-edit.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.athaydes.rawhttp%22%20AND%20a:%22rawhttp-req-in-edit%22) | [RawHTTP ReqInEdit (HTTP Tests)](https://renatoathaydes.github.io/rawhttp/rawhttp-modules/req-in-edit.html) |

[![Actions Status](https://github.com/renatoathaydes/rawhttp/workflows/Build%20And%20Test%20on%20All%20OSs/badge.svg)](https://github.com/renatoathaydes/rawhttp/actions)

[![Maven Central](https://img.shields.io/maven-central/v/com.athaydes.rawhttp/rawhttp-core.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.athaydes.rawhttp%22%20AND%20a:%22rawhttp-core%22)

A Java library to make it easy to deal with raw HTTP 1.1, as defined by [RFC-7230](https://tools.ietf.org/html/rfc7230),
and most of HTTP 1.0 ([RFC-1945](https://tools.ietf.org/html/rfc1945)).

> For details about using RawHTTP and the motivation for this project, see the
 [blog post](https://sites.google.com/a/athaydes.com/renato-athaydes/posts/announcingrawhttp-ajvmlibraryforhandlingrawhttp)
 I wrote about it!

> For testing HTTP servers, check out the [blog post I wrote about rawhttp-req-in-edit](https://renato.athaydes.com/posts/writing-http-files-for-testing.html),
> which lets you write [HTTP files](https://www.jetbrains.com/help/idea/exploring-http-syntax.html) to send requests 
> and assert responses using JS scripts. 

For more documentation, [visit the website](https://renatoathaydes.github.io/rawhttp).

## Introduction

HTTP is really simple in 99.9% of cases.

For example, the raw HTTP request you would make to fetch a resource from a web server looks like this:

> The example below is taken from the [HTTP 1.1 RFC 7230](https://tools.ietf.org/html/rfc7230#section-2.1).

```
GET /hello.txt HTTP/1.1
User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3
Host: www.example.com
Accept-Language: en, mi
```

To send that request out to a HTTP server using RawHTTP, you can parse the Request and stream it out via a 
`Socket`.

Here's the whole code to do that:

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

```java
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
try (OutputStream out = Files.newOutputStream(responseFile.toPath())) {
    response.writeTo(out);
}
```

That simple!

Notice that just with the above, you have everything you need to send and receive HTTP messages. 

To illustrate that, here is a simple implementation of a HTTP server that waits for a single request,
then responds with a valid response:

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

## HTTP client

Even though it's quite simple to implement your own HTTP client by using the `RawHttp` class to parse 
requests and responses (which can then be transmitted via Sockets), RawHTTP offers a simple HTTP client definition
(and implementation) that makes it a little bit more convenient to consume HTTP APIs.

Here's the [`RawHttpClient`](rawhttp-core/src/main/java/rawhttp/core/client/RawHttpClient.java) interface:

```java
public interface RawHttpClient<Response> {
    RawHttpResponse<Response> send(RawHttpRequest request) throws IOException;
}
```   

The `Response` type parameter allows implementations to expose their own type for HTTP Responses, if needed.

In the core module, a simple implementation is provided: [`TcpRawHttpClient`](rawhttp-core/src/main/java/rawhttp/core/client/TcpRawHttpClient.java).

Example usage:

```java
RawHttpClient<?> client = new TcpRawHttpClient();
EagerHttpResponse<?> response = client.send(request).eagerly();
```

> Unless you want to take care of streaming the response body later, always call `eagerly()`
  as shown above to consume the full response body (allowing the connection to be re-used).

Other implementations are available in separate modules:

* `RawHttpComponentsClient` - based on HttpComponents's HttpClient.

> Requires the `rawhttp:rawhttp-httpcomponents` module.

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

## HTTP server

RawHTTP also contains a [package](rawhttp-core/src/main/java/rawhttp/core/server) defining a few types
that describe a simple HTTP server.

The main type is the interface `RawHttpServer`, which uses a `Router` to route HTTP requests, returning a HTTP response.
`Router` is a functional interface (i.e. it can be implemented with a Java lambda), so implementing a full
server is very simple.

A default implementation of `TcpRawHttpServer` is provided... it spans a `Thread` (but re-uses it when possible) for 
each connected client.

Here's an example, written in Kotlin, of how to use `RawHttpServer`:

```kotlin
val server = TcpRawHttpServer(8093)

server.start { req ->
    when (req.uri.path) {
        "/hello", "/" ->
            when (req.method) {
                "GET" ->
                    http.parseResponse("HTTP/1.1 200 OK\n" +
                            "Content-Type: text/plain"
                    ).withBody(StringBody("Hello RawHTTP!"))
                else ->
                    http.parseResponse("HTTP/1.1 405 Method Not Allowed\n" +
                            "Content-Type: text/plain"
                    ).withBody(StringBody("Sorry, can't handle this method"))
            }
        else ->
            http.parseResponse("HTTP/1.1 404 Not Found\n" +
                    "Content-Type: text/plain"
            ).withBody(StringBody("Content was not found"))
    }
}
```

## Samples

Several samples showing how to use RawHTTP, including all [examples](samples/src/test/java/rawhttp/samples/JavaSample.java)
in this page, can be found in the [samples](samples) project.

> Note: to run the samples, execute the tests with the `-Prun-samples` argument.

The `rawhttp-duplex` module has its own sample, a [chat application](rawhttp-duplex/src/test/kotlin/chat-example.kt).
