---
title: "Mistakes RawHttp fixes up"
date: 2018-05-10T14:24:00+02:00
draft: false
---

By default, `RawHttp` will fix the following common mistakes when parsing a HTTP message:

#### Line separators

According to [RFC-7230](https://tools.ietf.org/html/rfc7230#section-3), which defines the HTTP/1.1 message format,
line separators must be `CRLF`, or `\r\n`.

However, by default, `RawHttp` allows a simple `\n` to be used:

{{< highlight java >}}
import rawhttp.core.*;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "GET /hello HTTP/1.1\n" +
    "Host: example.com");
{{< / highlight >}}

#### HTTP version

For simplicity, the HTTP version can be omitted from HTTP messages, in which case `HTTP/1.1` is used:

{{< highlight java >}}
import rawhttp.core.*;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "GET /hello\n" +
    "Host: example.com");
{{< / highlight >}}

#### Host header

The `Host` header is mandatory in HTTP requests. But `RawHttp` allows specifying a full URL in the method-line,
which makes it automatically add a `Host` header to the request in case it's missing:

{{< highlight java >}}
import rawhttp.core.*;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "GET http://example.com/hello");
{{< / highlight >}}

#### Leading new-line in message

[RFC-7230](https://tools.ietf.org/html/rfc7230#section-3.5) recommends that HTTP message receivers ignore a leading
new-line for the sake of robustness and historical reasons.

`RawHttp` will do that by default:

{{< highlight java >}}
import rawhttp.core.*;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    // this is ok!
    "\nGET http://example.com/hello");
{{< / highlight >}}

<hr>

### Configuring `RawHttp`

The `RawHttp` class has a constructor which takes a `RawHttpOptions` instance, allowing for all of the fix-ups
mentioned above to be turned off:

{{< highlight java >}}
import rawhttp.core.*;

RawHttp strictHttp = new RawHttp(RawHttpOptions.newBuilder()
            .doNotAllowNewLineWithoutReturn()
            .doNotInsertHostHeaderIfMissing()
            .doNotInsertHttpVersionIfMissing()
            .doNotIgnoreLeadingEmptyLine()
            .build());
{{< / highlight >}}


### Allowing unescaped URIs

By default, RawHTTP is strict when parsing URIs. However, you can make it lenient so that you don't need to escape
all forbidden characters (e.g. whitespaces don't need to be given as `%20`).

{{< highlight java >}}
import rawhttp.core.*;

RawHttp lenientUriOptions = new RawHttp(RawHttpOptions.newBuilder()
            .allowIllegalStartLineCharacters()
            .build());
{{< / highlight >}}

With this enabled, you can give URIs more easily:

```
GET https://www.example.com/name=Joe Doe&age=43 HTTP/1.1
Accept: text/html
```

<hr>

[Index](/rawhttp/docs) [Next: HTTP Client](/rawhttp/docs/http-client)
