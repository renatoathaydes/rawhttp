package com.athaydes.rawhttp.samples;

import com.athaydes.rawhttp.core.RawHttp;
import com.athaydes.rawhttp.core.RawHttpClient;
import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;
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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class JavaSample {

    @BeforeClass
    public static void startServer() {
        Spark.port(8082);
        Spark.get("/hello", "text/plain", (req, res) -> "Hello");
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
    public void rawHttpExampleFromHttpRFC() {
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
        RawHttpClient<?> client = new RawHttpComponentsClient();

        RawHttpRequest request = new RawHttp().parseRequest(
                "GET / HTTP/1.0\n" +
                        "User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3\n" +
                        "Host: www.example.com\n" +
                        "Accept-Language: en, mi");

        try {
            RawHttpResponse<?> rawResponse = client.send(request).eagerly();
            rawHttpStatusCode = rawResponse.getStatusCode();
            rawHttpContentType = rawResponse.getHeaders().get(HttpHeaders.CONTENT_TYPE).iterator().next();
            rawHttpResponseBody = new String(rawResponse.getBodyReader().eager().asBytes(), StandardCharsets.UTF_8);
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
        RawHttpResponse<?> response = client.send(request).eagerly();

        assertThat(response.getStatusCode(), is(200));
        assertThat(new String(response.getBodyReader().eager().asBytes()), equalTo("Hello"));
    }

}
