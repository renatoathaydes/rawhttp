package rawhttp.samples;

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
import rawhttp.core.EagerHttpResponse;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.body.EagerBodyReader;
import rawhttp.core.client.RawHttpClient;
import rawhttp.core.client.TcpRawHttpClient;
import rawhttp.core.errors.InvalidHttpRequest;
import rawhttp.httpcomponents.RawHttpComponentsClient;
import spark.Spark;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class JavaSample {

    private static final int PORT = 8083;

    @BeforeClass
    public static void startServer() throws Exception {
        Spark.port(PORT);
        Spark.get("/hello", "text/plain", (req, res) -> "Hello");
        RawHttp.waitForPortToBeTaken(PORT, Duration.ofSeconds(2));
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
                    .setUri(URI.create("http://ip.jsontest.com"))
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
                        "Host: ip.jsontest.com\n" +
                        "Accept-Language: en, mi");

        try (TcpRawHttpClient client = new TcpRawHttpClient()) {
            EagerHttpResponse<?> rawResponse = client.send(request).eagerly();
            rawHttpStatusCode = rawResponse.getStatusCode();
            rawHttpContentType = rawResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE).orElse("");
            rawHttpResponseBody = rawResponse.getBody().map(EagerBodyReader::toString)
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
        RawHttpRequest request = new RawHttp().parseRequest(String.format("GET localhost:%d/hello HTTP/1.0", PORT));
        EagerHttpResponse<?> response = client.send(request).eagerly();

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBody().map(EagerBodyReader::toString)
                .orElseThrow(() -> new RuntimeException("No body")), equalTo("Hello"));
    }

    @Test
    public void frontPageExample() throws IOException {
        RawHttp rawHttp = new RawHttp();

        RawHttpRequest request = rawHttp.parseRequest(
                "GET /hello.txt HTTP/1.1\r\n" +
                        "User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3\r\n" +
                        "Host: headers.jsontest.com\r\n" +
                        "Accept-Language: en, mi");
        Socket socket = new Socket("headers.jsontest.com", 80);
        request.writeTo(socket.getOutputStream());

        EagerHttpResponse<?> response = rawHttp.parseResponse(socket.getInputStream()).eagerly();

        // call "eagerly()" in order to download the body
        System.out.println(response.eagerly());

        assertThat(response.getStatusCode(), equalTo(200));
        assertTrue(response.getBody().isPresent());

        File responseFile = Files.createTempFile("rawhttp", ".http").toFile();
        try (OutputStream out = Files.newOutputStream(responseFile.toPath())) {
            response.writeTo(out);
        }

        System.out.printf("Response parsed from file (%s):", responseFile);
        System.out.println(rawHttp.parseResponse(responseFile).eagerly());
    }

    @Test
    public void goingRawWithoutFancyClient() throws IOException {
        RawHttp rawHttp = new RawHttp();

        RawHttpRequest request = rawHttp.parseRequest(String.format("GET localhost:%d/hello HTTP/1.0", PORT));
        Socket socket = new Socket("localhost", PORT);
        request.writeTo(socket.getOutputStream());

        EagerHttpResponse<?> response = rawHttp.parseResponse(socket.getInputStream()).eagerly();

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBody().map(EagerBodyReader::toString)
                .orElseThrow(() -> new RuntimeException("No body")), equalTo("Hello"));
    }

    @Test
    public void rudimentaryHttpServerCalledFromHttpComponentsClient() throws Exception {
        RawHttp http = new RawHttp();
        ServerSocket server = new ServerSocket(8084);

        new Thread(() -> {
            while (true) {
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

                } catch (InvalidHttpRequest e) {
                    //noinspection StatementWithEmptyBody
                    if ("No content".equalsIgnoreCase(e.getMessage())) {
                        // heartbeat, ignore
                    } else {
                        e.printStackTrace();
                        break;
                    }
                } catch (IOException e) {
                    // stopped
                    break;
                }
            }
        }).start();

        // let the server start
        RawHttp.waitForPortToBeTaken(8084, Duration.ofSeconds(2));

        CloseableHttpClient httpClient = HttpClients.createDefault();

        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(URI.create("http://localhost:8084/saysomething"))
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