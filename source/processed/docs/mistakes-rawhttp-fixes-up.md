{{ define title "RawHTTP" }}
{{ define moduleName "Mistakes RawHTTP fixes" }}
{{ define path baseURL + "/docs/mistakes-rawhttp-fixes-up.html" }}
{{ include /processed/fragments/_header.html }}
{{ include /processed/fragments/_nav.html }}

By default, `RawHttp` will fix the following common mistakes when parsing a HTTP message:

#### Line separators

According to [RFC-7230](https://tools.ietf.org/html/rfc7230#section-3), which defines the HTTP/1.1 message format,
line separators must be `CRLF`, or `\r\n`.

However, by default, `RawHttp` allows a simple `\n` to be used:

```java
import rawhttp.core.*;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "GET /hello HTTP/1.1\n" +
    "Host: example.com");
```

#### HTTP version

For simplicity, the HTTP version can be omitted from HTTP messages, in which case `HTTP/1.1` is used:

```java
import rawhttp.core.*;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "GET /hello\n" +
    "Host: example.com");
```

#### Host header

The `Host` header is mandatory in HTTP requests. But `RawHttp` allows specifying a full URL in the method-line,
which makes it automatically add a `Host` header to the request in case it's missing:

```java
import rawhttp.core.*;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "GET http://example.com/hello");
```

#### Leading new-line in message

[RFC-7230](https://tools.ietf.org/html/rfc7230#section-3.5) recommends that HTTP message receivers ignore a leading
new-line for the sake of robustness and historical reasons.

`RawHttp` will do that by default:

```java
import rawhttp.core.*;

RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    // this is ok!
    "\nGET http://example.com/hello");
```

<hr>

### Configuring `RawHttp`

The `RawHttp` class has a constructor which takes a `RawHttpOptions` instance, allowing for all of the fix-ups
mentioned above to be turned off:

```java
import rawhttp.core.*;

RawHttp strictHttp = new RawHttp(RawHttpOptions.newBuilder()
            .doNotAllowNewLineWithoutReturn()
            .doNotInsertHostHeaderIfMissing()
            .doNotInsertHttpVersionIfMissing()
            .doNotIgnoreLeadingEmptyLine()
            .build());
```

### Allowing unescaped URIs

By default, RawHTTP is strict when parsing URIs. However, you can make it lenient so that you don't need to escape
all forbidden characters (e.g. whitespaces don't need to be given as `%20`).

```java
import rawhttp.core.*;

RawHttp lenientUriOptions = new RawHttp(RawHttpOptions.newBuilder()
            .allowIllegalStartLineCharacters()
            .build());
```

With this enabled, you can give URIs more easily:

```
GET https://www.example.com/name=Joe Doe&age=43 HTTP/1.1
Accept: text/html
```

<hr>

{{ include /processed/fragments/_footer.html }}
