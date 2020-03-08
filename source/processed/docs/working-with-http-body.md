{{ define title "RawHTTP" }}
{{ define moduleName "Working with the HTTP message body" }}
{{ define path baseURL + "/docs/working-with-http-body.html" }}
{{ include /processed/fragments/_header.html }}
{{ include /processed/fragments/_nav.html }}

RawHTTP makes it easy to replace the body of a HTTP message, changing the relevant headers as appropriate.

### Set body from String

```java
import rawhttp.core.*;
import rawhttp.core.body.*;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "POST http://example.com/hello");
RawHttpRequest requestWithBody = request.withBody(
    new StringBody("Hello RawHTTP", "text/plain"));

System.out.println(requestWithBody.eagerly());
```

Prints:

```
POST /hello HTTP/1.1
Host: example.com
Content-Type: text/plain
Content-Length: 13

Hello RawHTTP
```

### Set body from File

```java
import rawhttp.core.*;
import rawhttp.core.body.*;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "POST http://example.com/hello");
RawHttpRequest requestWithBody = request.withBody(
    new FileBody(new File("hello.request"), "text/plain"));
```

### Set body from byte array

```java
import rawhttp.core.*;
import rawhttp.core.body.*;

byte[] bytes = "Hello RawHTTP".getBytes();

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "POST http://example.com/hello");
RawHttpRequest requestWithBody = request.withBody(
    new BytesBody(bytes, "text/plain"));
```

### Set body from InputStream (chunked)

```java
import rawhttp.core.*;
import rawhttp.core.body.*;

InputStream stream = new ByteArrayInputStream(
    "Hello RawHTTTP".getBytes());
int chunkSize = 4;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "POST http://example.com/hello");
RawHttpRequest requestWithBody = request.withBody(
    new ChunkedBody(stream, "text/plain", chunkSize));
```

The `InputStream` is read lazily as the body of the HTTP message is consumed by a reader.

The header `Transfer-Encoding: chunked` is set, and the body is encoded accordingly.

<hr>

{{ include /processed/fragments/_footer.html }}
