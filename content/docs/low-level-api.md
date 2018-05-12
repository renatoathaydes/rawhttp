---
title: "Low level API"
date: 2018-05-10T14:25:00+02:00
draft: true
---

### Send a GET request

{{< highlight java >}}
import com.athaydes.rawhttp.core.*;
import java.net.Socket;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "GET / HTTP/1.1\r\n" +
    "Host: headers.jsontest.com\r\n" +
    "User-Agent: RawHTTP\r\n" +
    "Accept: application/json");

Socket socket = new Socket("headers.jsontest.com", 80);
request.writeTo(socket.getOutputStream());
{{< / highlight >}}

### Send a POST request with a body

{{< highlight java >}}
import com.athaydes.rawhttp.core.*;
import java.net.Socket;

String jsonBody = "{ \"hello\": true, \"raw_http\": \"2.0\" }";
RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "POST /post HTTP/1.1\r\n" +
    "Host: httpbin.org\r\n" +
    "User-Agent: RawHTTP\r\n" +
    "Content-Length: " + jsonBody.length() + "\r\n" +
    "Content-Type: application/json\r\n" +
    "Accept: application/json\r\n" +
    "\r\n" +
    jsonBody);

Socket socket = new Socket("httpbin.org", 80);
request.writeTo(socket.getOutputStream());
{{< / highlight >}}

### Read a response

... continues from the previous example:

{{< highlight java >}}
RawHttpResponse<?> response = http.parseResponse(socket.getInputStream()).eagerly();
System.out.println("RESPONSE:\n" + response);

assertEquals(200, response.getStatusCode());
assertTrue(response.getBody().isPresent());

String textBody = response.getBody().get().decodeBodyToString(UTF_8);
assertTrue(textBody.contains(jsonBody.replaceAll(" ", "")));
{{< / highlight >}}

### Check headers (in both request or response)

... continues from the previous examples:

{{< highlight java >}}
assertEquals("application/json",
    request.getHeaders().getFirst("Accept").orElse(""));

assertEquals("application/json",
    response.getHeaders().getFirst("Content-Type").orElse(""));

assertEquals(asList("application/json"),
    response.getHeaders().get("Content-Type"));

assertEquals(asList("Host", "User-Agent", "Content-Length", "Content-Type", "Accept"),
    request.getHeaders().getHeaderNames());
{{< / highlight >}}

### Listen for HTTP requests (as a server)

{{< highlight java >}}
import com.athaydes.rawhttp.core.*;
import java.net.ServerSocket;

ServerSocket serverSocket = new ServerSocket(8082);
RawHttp http = new RawHttp();
Socket client = serverSocket.accept();
RawHttpRequest request = http.parseRequest(client.getInputStream());
{{< / highlight >}}

### Respond to a HTTP request

... continues from the previous example:

{{< highlight java >}}
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
response.writeTo(client.getOutputStream());
{{< / highlight >}}

<hr>

[Index](/docs) [Next: Working with message body](/docs/working-with-http-body)
