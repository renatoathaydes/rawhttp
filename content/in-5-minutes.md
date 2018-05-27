# RawHTTP in 5 minutes

RawHTTP is a Java library that makes it easy to work with HTTP 1.0 and 1.1.

It has ZERO dependencies.

You might want to use it if you need a stripped-down-to-basics HTTP client or server.

## How does it work?

RawHTTP allows you to write raw HTTP messages by hand, with just a little extra support to avoid mistakes.

The main class in the library is `RawHttp`.

You can use it to create a HTTP request:

{{< highlight java >}}
import rawhttp.core.*;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "GET / HTTP/1.1\r\n" +
    "Host: headers.jsontest.com\r\n" +
    "User-Agent: RawHTTP\r\n" +
    "Accept: application/json");
{{< / highlight >}}

RawHTTP is not strict by default, so you can use `\n` instead of `\r\n`, omit the
HTTP version (so `HTTP/1.1` is used), and specify a full URL on the first line
(the mandatory `Host` header is added automatically):

{{< highlight java >}}
import rawhttp.core.*;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "GET headers.jsontest.com\n" +
    "User-Agent: RawHTTPn" +
    "Accept: application/json");
{{< / highlight >}}

You can also create a HTTP response:

{{< highlight java >}}
import rawhttp.core.*;
import java.time.*;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

String body = "Hello RawHTTP!";
String dateString = RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
RawHttpResponse<?> response = http.parseResponse(
    "HTTP/1.1 200 OK\r\n" +
    "Content-Type: plain/text\r\n" +
    "Content-Length: " + body.length() + "\r\n" +
    "Server: RawHTTP\r\n" +
    "Date: " + dateString + "\r\n" +
    "\r\n" +
    body);
{{< / highlight >}}

To send out the HTTP message, just write it to an `OutputStream`.

{{< highlight java >}}
import java.net.Socket;

Socket socket = new Socket("headers.jsontest.com", 80);
request.writeTo(socket.getOutputStream());
{{< / highlight >}}

<hr>

If you prefer, you can also use the `TcpRawHttpClient` class to send requests
without managing sockets yourself:

{{< highlight java >}}
import rawhttp.core.*;
import rawhttp.core.client.*;

TcpRawHttpClient client = new TcpRawHttpClient();
RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest("...");
RawHttpResponse<?> response = client.send(request);
{{< / highlight >}}

If it's a server you're after, use `TcpRawHttpServer`:

{{< highlight java >}}
import rawhttp.core.*;
import rawhttp.core.server.*;

RawHttpServer server = new TcpRawHttpServer(8086);
RawHttp http = new RawHttp();

server.start(request -> {
    RawHttpResponse<?> response = http.parseResponse(...);
    return response;
});
{{< / highlight >}}

Easy!

Besides the core RawHTTP library itself, the RawHTTP project also includes a
[few related modules](/rawhttp/rawhttp-modules),
including a [CLI](/rawhttp/rawhttp-modules/cli) (command-line interface).

To start using RawHTTP, head to the [Get Started](/rawhttp/docs/get-started) page.