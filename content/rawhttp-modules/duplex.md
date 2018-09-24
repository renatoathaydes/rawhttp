---
title: "RawHTTP Duplex"
date: 2018-06-03T12:40:11+02:00
draft: false
---

The rawhttp-duplex module can be used to create a duplex communication channel as either a client or a server.

The entry point of the library is the `com.athaydes.rawhttp.duplex.RawHttpDuplex` class.

Its `connect` methods are used from a client to connect to a server,
while the `accept` methods should be used within a HTTP server to handle requests from a client.

Example code on the server:

{{< highlight kotlin >}}
import rawhttp.core.*
import com.athaydes.rawhttp.duplex.*
import rawhttp.core.server.TcpRawHttpServer;

val http = RawHttp()
val duplex = RawHttpDuplex()
val server = TcpRawHttpServer(8082)

server.start { request ->
    // call duplex.accept() to return a response that can do duplex communication 
    Optional.of(duplex.accept(request, { sender ->
        object : MessageHandler {
            override fun onTextMessage(message: String) { /* handle text message */ }      
            override fun onBinaryMessage(message: ByteArray, headers: RawHttpHeaders) { /* handle binary message */ }
            override fun onClose() { /* handle closed connection */ }
        }
    }))
}
{{< / highlight >}}

Example code on the client:

{{< highlight kotlin >}}
import rawhttp.core.*
import com.athaydes.rawhttp.duplex.*
import rawhttp.core.server.TcpRawHttpServer;

val http = RawHttp()
val duplex = RawHttpDuplex()

duplex.connect(http.parseRequest("POST http://localhost:8082/connect"), { sender ->
    object : MessageHandler { /* same API as on the server */ }
}
{{< / highlight >}}

The way duplex communication is achieved uses only HTTP/1.1 standard mechanisms and can be described as follows:

* The server listens for requests to start duplex communication.
* When a client connects, the server sends out a single chunked response in which each chunk
  is a new message from the server to the client.
* The client does the same: it sends a chunked body with the request in which each chunk is a message from the
  client to the server.

In other words, a single request/response is used to bootstrap communications. Both the request and the response
have effectively infinite chunked bodies where each chunk represents a message.

`RawHttpDuplex` sends a single extension parameter to idenfity text
messages: `Content-Type: text/plain` (notice that each chunk may contain "extensions").

If the chunk does not contain this extension, then it is considered to be a binary message.

Text messages may also contain the `Charset: <charset>` (e.g. `Charset: US-ASCII`) extension parameter to provide a
charset for the message. By default, `UTF-8` is used.

Each side of a connection pings the other every 5 seconds, by default, to avoid the TCP socket timing out.
To use a different ping period, use the {@link RawHttpDuplex#RawHttpDuplex(TcpRawHttpClient, Duration)} constructor.

## Demo

As is mandatory for duplex communication implementations,
a [Chat Demo application](https://github.com/renatoathaydes/rawhttp/blob/back-to-java8/rawhttp-duplex/src/test/kotlin/chat-example.kt)
was written in Kotlin to demonstrate usage of this library.

The video below shows it in action:

<iframe width="600" height="400" alt="rawhttp-duplex chat app in action"
 src="https://www.youtube.com/embed/_h3a5yodVgM"></iframe>
