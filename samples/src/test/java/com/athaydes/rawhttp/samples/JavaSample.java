package com.athaydes.rawhttp.samples;

import com.athaydes.rawhttp.core.EagerHttpResponse;
import com.athaydes.rawhttp.core.RawHttp;
import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.client.RawHttpClient;
import com.athaydes.rawhttp.core.client.TcpRawHttpClient;
import com.athaydes.rawhttp.httpcomponents.RawHttpComponentsClient;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import spark.Spark;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class JavaSample {

    @BeforeClass
    public static void startServer() throws Exception {
        Spark.port(8082);
        Spark.get("/hello", "text/plain", (req, res) -> "Hello");
        Thread.sleep(150L);
    }

    @AfterClass
    public static void stopServer() {
        Spark.stop();
    }

    /**
     * This test runs two identical requests to the example.com server as illustrated in the HTTP RFC,
     * then compares the results, which should be exactly the same.
     */
    @Test
    public void httpExampleFromHttpRFC() {
        int httpComponentStatusCode;
        String httpComponentsContentType;
        String httpComponentsResponseBody;

        // first, use the HTTPComponents HttpClient
        CloseableHttpClient httpClient = HttpClients.createDefault();

        CloseableHttpResponse response = null;
        try {
            HttpUriRequest httpRequest = RequestBuilder.get()
                    .addHeader(HttpHeaders.USER_AGENT, "curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3")
                    .addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en, mi")
                    .setUri(URI.create("http://www.example.com"))
                    .build();
            response = httpClient.execute(httpRequest);

            // collect the results
            httpComponentStatusCode = response.getStatusLine().getStatusCode();
            httpComponentsContentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue();
            httpComponentsResponseBody = EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (response != null) try {
                response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        int rawHttpStatusCode;
        String rawHttpContentType;
        String rawHttpResponseBody;

        // now, using RawHTTP
        RawHttpRequest request = new RawHttp().parseRequest(
                "GET / HTTP/1.0\n" +
                        "User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3\n" +
                        "Host: www.example.com\n" +
                        "Accept-Language: en, mi");

        try (TcpRawHttpClient client = new TcpRawHttpClient()) {
            EagerHttpResponse<?> rawResponse = client.send(request).eagerly();
            rawHttpStatusCode = rawResponse.getStatusCode();
            rawHttpContentType = rawResponse.getHeaders().get(HttpHeaders.CONTENT_TYPE)
                    .iterator().next().split(";")[0];
            rawHttpResponseBody = rawResponse.getBody().map(b -> b.asString(UTF_8))
                    .orElseThrow(() -> new RuntimeException("No body"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        assertThat(rawHttpContentType, equalTo(httpComponentsContentType));
        assertThat(rawHttpStatusCode, equalTo(httpComponentStatusCode));
        assertThat(rawHttpResponseBody, equalTo(httpComponentsResponseBody));
    }

    @Test
    public void usingRawHttpWithHttpComponents() throws IOException {
        RawHttpClient<?> client = new RawHttpComponentsClient();
        RawHttpRequest request = new RawHttp().parseRequest("GET localhost:8082/hello HTTP/1.0");
        EagerHttpResponse<?> response = client.send(request).eagerly();

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBody().map(b -> b.asString(UTF_8))
                .orElseThrow(() -> new RuntimeException("No body")), equalTo("Hello"));
    }

    @Test
    public void frontPageExample() throws IOException {
        RawHttp rawHttp = new RawHttp();

        RawHttpRequest request = rawHttp.parseRequest(
                "GET /hello.txt HTTP/1.1\r\n" +
                        "User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3\r\n" +
                        "Host: www.example.com\r\n" +
                        "Accept-Language: en, mi");
        Socket socket = new Socket("www.example.com", 80);
        request.writeTo(socket.getOutputStream());

        EagerHttpResponse<?> response = rawHttp.parseResponse(socket.getInputStream()).eagerly();

        // call "eagerly()" in order to download the body
        System.out.println(response.eagerly());

        assertThat(response.getStatusCode(), equalTo(404));
        assertTrue(response.getBody().isPresent());

        File responseFile = Files.createTempFile("rawhttp", ".http").toFile();
        try (FileOutputStream out = new FileOutputStream(responseFile)) {
            response.writeTo(out);
        }

        System.out.printf("Response parsed from file (%s):", responseFile);
        System.out.println(rawHttp.parseResponse(responseFile).eagerly());
    }

    @Test
    public void goingRawWithoutFancyClient() throws IOException {
        RawHttp rawHttp = new RawHttp();

        RawHttpRequest request = rawHttp.parseRequest("GET localhost:8082/hello HTTP/1.0");
        Socket socket = new Socket("localhost", 8082);
        request.writeTo(socket.getOutputStream());

        EagerHttpResponse<?> response = rawHttp.parseResponse(socket.getInputStream()).eagerly();

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBody().map(b -> b.asString(UTF_8))
                .orElseThrow(() -> new RuntimeException("No body")), equalTo("Hello"));
    }

    @Test
    public void rudimentaryHttpServerCalledFromHttpComponentsClient() throws Exception {
        RawHttp http = new RawHttp();
        ServerSocket server = new ServerSocket(8083);

        new Thread(() -> {
            try {
                Socket client = server.accept();
                RawHttpRequest request = http.parseRequest(client.getInputStream());
                System.out.println("REQUEST:\n" + request);
                if (request.getUri().getPath().equals("/saysomething")) {
                    http.parseResponse("HTTP/1.1 200 OK\n" +
                            "Content-Type: text/plain\n" +
                            "Content-Length: 9\n" +
                            "\n" +
                            "something").writeTo(client.getOutputStream());
                } else {
                    http.parseResponse("HTTP/1.1 404 Not Found\n" +
                            "Content-Type: text/plain\n" +
                            "Content-Length: 0\n" +
                            "\n").writeTo(client.getOutputStream());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // let the server start
        Thread.sleep(150L);

        CloseableHttpClient httpClient = HttpClients.createDefault();

        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(URI.create("http://localhost:8083/saysomething"))
                .build();

        try {
            CloseableHttpResponse response = httpClient.execute(httpRequest);
            System.out.println("RESPONSE:\n" + response);

            assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
            assertThat(response.getEntity().getContentLength(), equalTo(9L));
            assertThat(EntityUtils.toString(response.getEntity()), equalTo("something"));
        } finally {
            httpClient.close();
            server.close();
        }
    }

}