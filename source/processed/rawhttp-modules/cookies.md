{{ define title "RawHTTP" }}
{{ define moduleName "RawHTTP Cookies" }}
{{ define path baseURL + "/rawhttp-modules/cookies.html" }}
{{ include /processed/fragments/_header.html }}
{{ include /processed/fragments/_nav.html }}

# RawHTTP Cookies

[![Javadocs](https://javadoc.io/badge2/com.athaydes.rawhttp/rawhttp-cookies/javadoc.svg)](https://javadoc.io/doc/com.athaydes.rawhttp/rawhttp-cookies)

The `rawhttp-cookies` module provides functionality for HTTP clients and servers to handle cookies.

It also provides a `rawhttp.cookies.persist.FileCookieJar` class which implements `java.net.CookieStore`
for easily persisting cookies.

## HTTP client usage

Instances of `rawhttp.core.client.TcpRawHttpClient` can support cookies by using this module's
`rawhttp.cookies.ClientOptionsWithCookies` class, which was designed to _wrap_ another implementation of
`TcpRawHttpClient.TcpRawHttpClientOptions` (i.e. it uses composition instead of inheritance to add behaviour).

Basic usage:

```java
var client = new TcpRawHttpClient(new ClientOptionsWithCookies(), HTTP);
```

If you already have a custom implementation of `TcpRawHttpClient.TcpRawHttpClientOptions`, you can _wrap_ it:

```java
TcpRawHttpClientOptions customClientOptions = new MyCustomClientOptions();
var client = new TcpRawHttpClient(new ClientOptionsWithCookies(
    new CookieManager(), // java.net.CookieManager
    customClientOptions),
    HTTP);
```

If you want the client to have persistent cookies, pass a `FileCookieJar` into the constructor:

```
var cookieJar = new FileCookieJar(new File("cookies"));
var client = new TcpRawHttpClient(
    new ClientOptionsWithCookies(cookieJar),
    HTTP);
```

## HTTP server usage

HTTP Servers can use the `rawhttp.cookies.ServerCookieHelper` class to handle cookies.

Basic usage:

_Setting a cookie_

```java
var headers = RawHttpHeaders.newBuilder();
var cookie = new HttpCookie("sid", "123456");
ServerCookieHelper.setCookie(headers, cookie);
var response = new RawHttp().parseResponse("200 OK")
  .withHeaders(headers.build());
// send response to client
```

_Reading the cookies sent by the client_

```java
val cookies = ServerCookieHelper.readClientCookies(request);
```

Because Java cookies do not support the increasingly common `SameSite` header, all the methods to set cookies
support a separate parameter with a type of `rawhttp.cookies.SameSite`:

```java
var headers = RawHttpHeaders.newBuilder();
var cookie = new HttpCookie("sid", "123456");
ServerCookieHelper.setCookie(headers, cookie, SameSite.STRICT);
```

## Persistent cookie jar

The `rawhttp.cookies.persist.FileCookieJar` class, as mentioned above, can be used to persist cookies in a file.

It supports different strategies for _flushing_ cookies added to the jar. These strategies are:

#### `rawhttp.cookies.persist.JvmShutdownFlushStrategy`

Only ever flush the cookies when the JVM shutdown. This is the fastest option at runtime because it behaves as an
in-memory cookie jar until the JVM is shut down.

The downside is that it may lose all cookies if the JVM crashes and does not shut down cleanly.

#### `rawhttp.cookies.persist.OnWriteFlushStrategy`

A flush strategy that will flush every `n` updates, where `n >= 1`.

For example, if `n` is set to 10, this strategy will only write to the cookie jar's file after 10 cookies are set 
or modified.

This strategy also flushes when the JVM shuts down to avoid losing cookies where possible.

#### `rawhttp.cookies.persist.PeriodicFlushStrategy`

The periodic flush strategy flushes the cookies to disc periodically. The duration of time between flushes
can be chosen by passing a `java.time.Duration` instance into its constructor (must be greater than 1 second).

Flushes are performed on a background, daemon Thread. A custom `ScheduledExecutorService` may be provided in the 
constructor to be used for scheduling flushes.

This strategy also flushes when the JVM shuts down to avoid losing cookies where possible.

{{ include /processed/fragments/_footer.html }}
