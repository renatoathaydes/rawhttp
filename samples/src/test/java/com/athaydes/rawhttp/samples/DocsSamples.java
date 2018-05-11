package com.athaydes.rawhttp.samples;

import com.athaydes.rawhttp.core.RawHttp;
import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;
import com.athaydes.rawhttp.core.body.BytesBody;
import com.athaydes.rawhttp.core.body.ChunkedBody;
import com.athaydes.rawhttp.core.body.FileBody;
import com.athaydes.rawhttp.core.body.StringBody;
import com.athaydes.rawhttp.core.client.TcpRawHttpClient;
import com.athaydes.rawhttp.core.server.RawHttpServer;
import com.athaydes.rawhttp.core.server.TcpRawHttpServer;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Ignore;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Ignore("Do not run these tests automatically.")
public class DocsSamples {

    @Test
    public void sendRawRequest() throws IOException {
        RawHttp http = new RawHttp();
        RawHttpRequest request = http.parseRequest(
                "GET / HTTP/1.1\r\n" +
                        "Host: headers.jsontest.com\r\n" +
                        "User-Agent: RawHTTP\r\n" +
                        "Accept: application/json");

        Socket socket = new Socket("headers.jsontest.com", 80);
        request.writeTo(socket.getOutputStream());

        // get the response
        RawHttpResponse<?> response = http.parseResponse(socket.getInputStream());
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().isPresent());
        String textBody = response.getBody().get().decodeBodyToString(UTF_8);
        assertTrue(textBody.contains("\"User-Agent\": \"RawHTTP\""));
    }

    @Test
    public void sendRawPostRequest() throws IOException {
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

        // get the response
        RawHttpResponse<?> response = http.parseResponse(socket.getInputStream()).eagerly();
        System.out.println("RESPONSE:\n" + response);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().isPresent());

        String textBody = response.getBody().get().decodeBodyToString(UTF_8);
        assertTrue(textBody.contains(jsonBody.replaceAll(" ", "")));

        assertEquals("application/json",
                request.getHeaders().getFirst("Accept").orElse(""));

        assertEquals("application/json",
                response.getHeaders().getFirst("Content-Type").orElse(""));

        assertEquals(asList("application/json"),
                response.getHeaders().get("Content-Type"));

        assertEquals(asList("Host", "User-Agent", "Content-Length", "Content-Type", "Accept"),
                request.getHeaders().getHeaderNames());
    }

    @Test
    public void serveHttpRequests()
            throws IOException, InterruptedException {
        ServerSocket serverSocket = new ServerSocket(8082);
        RawHttp http = new RawHttp();
        CountDownLatch latch = new CountDownLatch(1);

        // run a server in a separate Thread
        new Thread(() -> {
            Socket client = null;
            latch.countDown();
            try {
                client = serverSocket.accept();
                RawHttpRequest request = http.parseRequest(client.getInputStream());

                System.out.println("Got Request:\n" + request);
                String body = "Hello RawHTTP!";
                String dateString = RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
                RawHttpResponse<?> response = http.parseResponse("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: plain/text\r\n" +
                        "Content-Length: " + body.length() + "\r\n" +
                        "Server: RawHTTP\r\n" +
                        "Date: " + dateString + "\r\n" +
                        "\r\n" +
                        body);
                response.writeTo(client.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (client != null) {
                    try {
                        client.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();

        // wait for the server to start
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // then let the socket get bound
        Thread.sleep(100L);

        RawHttpRequest request = http.parseRequest("GET /\r\nHost: localhost");

        Socket socket = new Socket(InetAddress.getLocalHost(), 8082);
        request.writeTo(socket.getOutputStream());

        // get the response
        RawHttpResponse<?> response = http.parseResponse(socket.getInputStream()).eagerly();
        System.out.println("RESPONSE:\n" + response);
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().isPresent());
        assertEquals("Hello RawHTTP!", response.getBody().get().decodeBodyToString(UTF_8));
    }

    @Test
    public void requestWithTcpRawHttpClient() throws IOException {
        TcpRawHttpClient client = new TcpRawHttpClient();
        RawHttp http = new RawHttp();

        RawHttpRequest request = http.parseRequest(
                "GET / HTTP/1.1\r\n" +
                        "Host: headers.jsontest.com\r\n" +
                        "User-Agent: RawHTTP\r\n" +
                        "Accept: application/json");

        RawHttpResponse<?> response = client.send(request);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().isPresent());
        String textBody = response.getBody().get().decodeBodyToString(UTF_8);
        assertTrue(textBody.contains("\"User-Agent\": \"RawHTTP\""));

        client.close();
    }

    @Test
    public void requestWithConfiguredTcpRawHttpClient() throws IOException {
        class SafeHttpClientOptions implements TcpRawHttpClient.TcpRawHttpClientOptions {
            @Override
            public Socket getSocket(URI uri) {
                String host = uri.getHost();
                int port = uri.getPort();
                if (port < 1) {
                    port = 443;
                }
                try {
                    // only allow HTTPS connections!
                    return SSLSocketFactory.getDefault().createSocket(host, port);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public RawHttpResponse<Void> onResponse(Socket socket, URI uri, RawHttpResponse<Void> httpResponse) throws IOException {
                return httpResponse.eagerly();
            }

            @Override
            public void close() throws IOException {
                // TODO the client was closed, perform cleanup
            }
        }

        TcpRawHttpClient client = new TcpRawHttpClient(new SafeHttpClientOptions());
        RawHttp http = new RawHttp();

        RawHttpRequest request = http.parseRequest(
                "GET https://jsonplaceholder.typicode.com/posts/1\n" +
                        "User-Agent: RawHTTP\n" +
                        "Accept: application/json");

        RawHttpResponse<?> response = client.send(request);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().isPresent());
        String textBody = response.getBody().get().decodeBodyToString(UTF_8);
        assertTrue(textBody.contains("\"userId\": 1"));

        client.close();
    }

    @Test
    public void useHttpServer() throws InterruptedException, IOException {
        RawHttpServer server = new TcpRawHttpServer(8086);
        RawHttp http = new RawHttp();

        server.start(request -> {
            System.out.println("Got Request:\n" + request);

            String body = "Hello RawHTTP!";
            String dateString = RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));

            RawHttpResponse<?> response = http.parseResponse("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: plain/text\r\n" +
                    "Content-Length: " + body.length() + "\r\n" +
                    "Server: RawHTTP\r\n" +
                    "Date: " + dateString + "\r\n" +
                    "\r\n" +
                    body);

            return Optional.of(response);
        });

        // wait for the socket get bound
        Thread.sleep(150L);

        RawHttpRequest request = http.parseRequest("GET /\r\nHost: localhost");

        Socket socket = new Socket(InetAddress.getLocalHost(), 8086);
        request.writeTo(socket.getOutputStream());

        // get the response
        RawHttpResponse<?> response = http.parseResponse(socket.getInputStream()).eagerly();
        System.out.println("RESPONSE:\n" + response);
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().isPresent());
        assertEquals("Hello RawHTTP!", response.getBody().get().decodeBodyToString(UTF_8));

        server.stop();
    }

    @Test
    public void replacingBodyWithString() throws IOException {
        RawHttp http = new RawHttp();
        RawHttpRequest request = http.parseRequest("POST http://example.com/hello");
        RawHttpRequest requestWithBody = request.withBody(new StringBody("Hello RawHTTP", "text/plain"));
        System.out.println(requestWithBody.eagerly());
    }

    @Test(expected = FileNotFoundException.class)
    public void replacingBodyWithFile() throws Throwable {
        RawHttp http = new RawHttp();
        RawHttpRequest request = http.parseRequest("POST http://example.com/hello");
        try {
            RawHttpRequest requestWithBody = request.withBody(
                    new FileBody(new File("hello.request"), "text/plain", true));
            System.out.println(requestWithBody.eagerly());
        } catch (RuntimeException e) {
            throw e.getCause();
        }
    }

    @Test
    public void replacingBodyWithBytes() throws IOException {
        byte[] bytes = "Hello RawHTTP".getBytes();

        RawHttp http = new RawHttp();
        RawHttpRequest request = http.parseRequest("POST http://example.com/hello");
        RawHttpRequest requestWithBody = request.withBody(
                new BytesBody(bytes, "text/plain"));
        System.out.println(requestWithBody.eagerly());
    }

    @Test
    public void replacingBodyWithChunkedEncodedMessage() throws IOException {
        InputStream stream = new ByteArrayInputStream("Hello RawHTTTP".getBytes());
        int chunkSize = 4;

        RawHttp http = new RawHttp();
        RawHttpRequest request = http.parseRequest("POST http://example.com/hello");
        RawHttpRequest requestWithBody = request.withBody(
                new ChunkedBody(stream, "text/plain", chunkSize));
        System.out.println(requestWithBody.eagerly());
    }

}
