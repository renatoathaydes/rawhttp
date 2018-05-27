# About RawHTTP

## Motivation

RawHTTP was created by [Renato Athaydes](https://software.athaydes.com) after he realized just how complex Java
HTTP clients and servers were, when in reality, [HTTP itself](https://tools.ietf.org/html/rfc7230) is pretty simple.

Imagine that you just wanted to fire up a little microservice with a couple of REST endpoints.

One of the "simplest" Java server micro-frameworks around, [SparkJava](http://sparkjava.com), lets you
do that, but it takes all of these things with it:

<pre style="font-size: 8px; line-height: 1rem; font-family: Courier">
\--- com.sparkjava:spark-core:2.7.1
     +--- org.slf4j:slf4j-api:1.7.13
     +--- org.eclipse.jetty:jetty-server:9.4.6.v20170531
     |    +--- javax.servlet:javax.servlet-api:3.1.0
     |    +--- org.eclipse.jetty:jetty-http:9.4.6.v20170531
     |    |    +--- org.eclipse.jetty:jetty-util:9.4.6.v20170531
     |    |    \--- org.eclipse.jetty:jetty-io:9.4.6.v20170531
     |    |         \--- org.eclipse.jetty:jetty-util:9.4.6.v20170531
     |    \--- org.eclipse.jetty:jetty-io:9.4.6.v20170531 (*)
     +--- org.eclipse.jetty:jetty-webapp:9.4.6.v20170531
     |    +--- org.eclipse.jetty:jetty-xml:9.4.6.v20170531
     |    |    \--- org.eclipse.jetty:jetty-util:9.4.6.v20170531
     |    \--- org.eclipse.jetty:jetty-servlet:9.4.6.v20170531
     |         \--- org.eclipse.jetty:jetty-security:9.4.6.v20170531
     |              \--- org.eclipse.jetty:jetty-server:9.4.6.v20170531 (*)
     +--- org.eclipse.jetty.websocket:websocket-server:9.4.6.v20170531
     |    +--- org.eclipse.jetty.websocket:websocket-common:9.4.6.v20170531
     |    |    +--- org.eclipse.jetty.websocket:websocket-api:9.4.6.v20170531
     |    |    +--- org.eclipse.jetty:jetty-util:9.4.6.v20170531
     |    |    \--- org.eclipse.jetty:jetty-io:9.4.6.v20170531 (*)
     |    +--- org.eclipse.jetty.websocket:websocket-client:9.4.6.v20170531
     |    |    +--- org.eclipse.jetty:jetty-util:9.4.6.v20170531
     |    |    +--- org.eclipse.jetty:jetty-io:9.4.6.v20170531 (*)
     |    |    +--- org.eclipse.jetty:jetty-client:9.4.6.v20170531
     |    |    |    +--- org.eclipse.jetty:jetty-http:9.4.6.v20170531 (*)
     |    |    |    \--- org.eclipse.jetty:jetty-io:9.4.6.v20170531 (*)
     |    |    \--- org.eclipse.jetty.websocket:websocket-common:9.4.6.v20170531 (*)
     |    +--- org.eclipse.jetty.websocket:websocket-servlet:9.4.6.v20170531
     |    |    +--- org.eclipse.jetty.websocket:websocket-api:9.4.6.v20170531
     |    |    \--- javax.servlet:javax.servlet-api:3.1.0
     |    +--- org.eclipse.jetty:jetty-servlet:9.4.6.v20170531 (*)
     |    \--- org.eclipse.jetty:jetty-http:9.4.6.v20170531 (*)
     \--- org.eclipse.jetty.websocket:websocket-servlet:9.4.6.v20170531 (*)
</pre>

Now you need to understand what all these dependencies are doing if you really want to know how your
little microservice actually works.

If you look at [Spring Boot](https://projects.spring.io/spring-boot/) or
[Dropwizard](https://www.dropwizard.io/1.3.1/docs/), things are worse still.

HTTP clients may not be as complex, but they tend to be difficult to learn and use when compared to plain
(or, let's say, raw) HTTP.

## Why RawHTTP

RawHTTP has ZERO dependencies and should be trivial to learn.

You have 100% control of what your server or client should do. No magic!

Of course, RawHTTP is not a full package, and it doesn't try to be. It just gives you an easy-to-use
API to implement your own HTTP clients and servers.

Want to convert your POJOs to JSON or XML before sending it to a client?

Well, just use [Gson](https://github.com/google/gson) or
your favourite XML parser to convert the POJO into bytes, put those bytes into a HTTP response,
then write that to a `Socket`:

{{< highlight java >}}
RawHttp http = new RawHttp();
RawHttpResponse<?> response = http.parseResponse("200 OK");
response.withBody(
    new BytesBody(bytes, "application/json")
).writeTo(socket);
{{< / highlight >}}

Need to send a `GET` request to fetch some JSON from your API?

{{< highlight java >}}
RawHttp http = new RawHttp();
RawHttpRequest request = http.parseRequest(
    "GET https://example.com/my-resource/123\n" +
    "Accept: application/json");
RawHttpResponse<?> response =
    new TcpRawHttpClient().send(request);
{{< / highlight >}}

You can even copy-paste a sample HTTP request from the API docs and send it out as-is.

The RawHTTP API was designed to make it intuitive to use and allow all behaviour to be easily
customized.

All objects are immutable, so mutator methods always return a new object rather than change the receiver.
This is done very efficiently, so performance is not compromised.

Check [RawHTTP in 5 minutes](/rawhttp/in-5-minutes) for a quick overview, or head to the [documentation](/rawhttp/docs) for more details.

## Source code

RawHTTP is on [GitHub](https://github.com/renatoathaydes/rawhttp).

## License

[Apache 2.0](https://github.com/renatoathaydes/rawhttp/blob/master/LICENSE.txt)
