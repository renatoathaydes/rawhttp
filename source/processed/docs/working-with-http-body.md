---
title: "Working with message body"
date: 2018-05-10T14:02:50+02:00
draft: false
---

RawHTTP makes it easy to replace the body of a HTTP message, changing the relevant headers as appropriate.

### Set body from String

{{< highlight java >}}
import rawhttp.core.*;
import rawhttp.core.body.*;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "POST http://example.com/hello");
RawHttpRequest requestWithBody = request.withBody(
    new StringBody("Hello RawHTTP", "text/plain"));

System.out.println(requestWithBody.eagerly());
{{< / highlight >}}

Prints:

```
POST /hello HTTP/1.1
Host: example.com
Content-Type: text/plain
Content-Length: 13

Hello RawHTTP
```

### Set body from File

{{< highlight java >}}
import rawhttp.core.*;
import rawhttp.core.body.*;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "POST http://example.com/hello");
RawHttpRequest requestWithBody = request.withBody(
    new FileBody(new File("hello.request"), "text/plain"));
{{< / highlight >}}

### Set body from byte array

{{< highlight java >}}
import rawhttp.core.*;
import rawhttp.core.body.*;

byte[] bytes = "Hello RawHTTP".getBytes();

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "POST http://example.com/hello");
RawHttpRequest requestWithBody = request.withBody(
    new BytesBody(bytes, "text/plain"));
{{< / highlight >}}

### Set body from InputStream (chunked)

{{< highlight java >}}
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
{{< / highlight >}}

The `InputStream` is read lazily as the body of the HTTP message is consumed by a reader.

The header `Transfer-Encoding: chunked` is set, and the body is encoded accordingly.

<hr>

[Index](/rawhttp/docs) [Next: Mistakes RawHttp fixes up](/rawhttp/docs/mistakes-rawhttp-fixes-up)
