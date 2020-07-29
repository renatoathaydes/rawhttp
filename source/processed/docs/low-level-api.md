{{ define title "RawHTTP" }}
{{ define moduleName "Low Level API" }}
{{ define path baseURL + "/docs/low-level-api.html" }}
{{ include /processed/fragments/_header.html }}
{{ include /processed/fragments/_nav.html }}

### Send a GET request

```java
import rawhttp.core.*;
import java.net.Socket;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "GET / HTTP/1.1\r\n" +
    "Host: headers.jsontest.com\r\n" +
    "User-Agent: RawHTTP\r\n" +
    "Accept: application/json");

Socket socket = new Socket("headers.jsontest.com", 80);
request.writeTo(socket.getOutputStream());
```

### Send a POST request with a body

```java
import rawhttp.core.*;
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
```

### Read a response

... continues from the previous example:

```java
RawHttpResponse<?> response = http.parseResponse(socket.getInputStream()).eagerly();
System.out.println("RESPONSE:\n" + response);

assertEquals(200, response.getStatusCode());
assertTrue(response.getBody().isPresent());

String textBody = response.getBody().get().decodeBodyToString(UTF_8);
assertTrue(textBody.contains(jsonBody.replaceAll(" ", "")));
```

### Check headers (in both request or response)

... continues from the previous examples:

```java
assertEquals("application/json",
    request.getHeaders().getFirst("Accept").orElse(""));

assertEquals("application/json",
    response.getHeaders().getFirst("Content-Type").orElse(""));

assertEquals(asList("application/json"),
    response.getHeaders().get("Content-Type"));

assertEquals(asList("Host", "User-Agent", "Content-Length", "Content-Type", "Accept"),
    request.getHeaders().getHeaderNames());
```

### Specify a specific encoding for header values

By default, HTTP headers's values are decoded using US-ASCII as that is the [recommendation](https://tools.ietf.org/html/rfc7230#section-3.2.4).

If you need to support other encodings, use a custom `RawHttp` instance to parse messages, as follows:

```java
var http = new RawHttp(RawHttpOptions.newBuilder()
        .withHttpHeadersOptions()
        .withValuesCharset(StandardCharsets.UTF_8)
        .done()
        .build());

// now this will be allowed
http.parseRequest("""
     GET / HTTP/1.1
     Accept: こんにちは, text/plain
     User-Agent: RawHTTP
     Host: localhost:$serverPort""");
```

### Listen for HTTP requests (as a server)

```java
import rawhttp.core.*;
import java.net.ServerSocket;

ServerSocket serverSocket = new ServerSocket(8082);
RawHttp http = new RawHttp();
Socket client = serverSocket.accept();
RawHttpRequest request = http.parseRequest(client.getInputStream());
```

### Respond to a HTTP request

... continues from the previous example:

```java
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
```

<hr>

{{ include /processed/fragments/_footer.html }}
