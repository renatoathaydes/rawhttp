# RawHTTP

A library to make it easy to deal with raw HTTP.

Raw HTTP is really simple in 99.9% of cases.

For example, the raw HTTP request you would make to fetch a resource from a web server looks like this:

> The example below is taken from the [HTTP 1.1 RFC 7230](https://tools.ietf.org/html/rfc7230#section-2.1).

```
GET /hello.txt HTTP/1.1
User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3
Host: www.example.com
Accept-Language: en, mi
```

With a traditional Java HttpClient, the same request would require learning the library's API
(which, in the case of HttpComponents, is infamous for changing completely across versions)
and making some non-trivial calls:

```java
CloseableHttpClient httpclient = HttpClients.createDefault();
CloseableHttpResponse response = null;
try {
    HttpUriRequest httpRequest = RequestBuilder.get()
            .addHeader(HttpHeaders.USER_AGENT, "curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3")
            .addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en, mi")
            .setUri(URI.create("http://www.example.com"))
            .build();
    response = httpclient.execute(httpRequest);

    // use response
    ...
} catch (IOException e) {
    e.printStackTrace();
} finally {
    if (response != null) try {
        response.close();
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

With **RawHTTP**, you just send it as-is:

```java
RawHttpClient<?> client = new RawHttpComponentsClient();

RawHttpRequest request = new RawHttp().parseRequest(
        "GET /hello.txt HTTP/1.1\n" +
        "User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3\n" +
        "Host: www.example.com\n" +
        "Accept-Language: en, mi");

try {
    RawHttpResponse<?> rawResponse = client.send(request).eagerly();
    // use response
    ...
} catch (IOException e) {
    e.printStackTrace();
}
```

Almost no API to learn.

You just need to know how a HTTP request should look like, how to create a client and how to send the request.

## Creating a client

RawHTTP has the following clients so far:

* `RawHttpComponentsClient` - based on HttpComponents

## Sending a request

To create a request, you just parse it using the `RawHttp` class as shown in the example.

Once you have a request, send it using the client you've just created.

```java
RawHttpClient<?> client = new RawHttpComponentsClient();
RawHttpRequest request = new RawHttp().parseRequest("GET localhost:8080/hello");
RawHttpResponse<?> rawResponse = client.send(request).eagerly();
```

Unless you want to take care of closing the connection and streaming the response yourself, always call `eagerly()`
as shown above.

