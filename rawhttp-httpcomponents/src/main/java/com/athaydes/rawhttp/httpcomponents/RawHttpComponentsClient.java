package com.athaydes.rawhttp.httpcomponents;

import com.athaydes.rawhttp.core.LazyBodyReader;
import com.athaydes.rawhttp.core.RawHttpClient;
import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;
import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.http.HttpHeaders.CONTENT_LENGTH;

public class RawHttpComponentsClient implements RawHttpClient<CloseableHttpResponse> {

    private final CloseableHttpClient httpClient;

    public RawHttpComponentsClient() {
        this(HttpClients.createDefault());
    }

    public RawHttpComponentsClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public RawHttpResponse<CloseableHttpResponse> send(RawHttpRequest request) throws IOException {
        CloseableHttpResponse response = null;
        RequestBuilder builder = RequestBuilder.create(request.getMethod());
        builder.setUri(request.getUri());
        builder.setVersion(toProtocolVersion(request.getHttpVersion()));
        request.getHeaders().forEach((name, values) ->
                values.forEach(value ->
                        builder.addHeader(new BasicHeader(name, value))));

        request.getBody().ifPresent(body ->
                builder.setEntity(new InputStreamEntity(body.asStream())));

        try {
            response = httpClient.execute(builder.build());
            Map<String, Collection<String>> headers = readHeaders(response);
            LazyBodyReader bodyReader = new LazyBodyReader(
                    new BufferedHttpEntity(response.getEntity()).getContent(),
                    parseContentLength(headers.getOrDefault(CONTENT_LENGTH, emptyList())));
            return new RawHttpResponse<>(response, request, headers, bodyReader, response.getStatusLine().getStatusCode());
        } finally {
            if (response != null) try {
                response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Long parseContentLength(Collection<String> contentLength) {
        if (contentLength.size() == 1) {
            return Long.parseLong(contentLength.iterator().next());
        } else {
            return null;
        }
    }

    private Map<String, Collection<String>> readHeaders(CloseableHttpResponse response) {
        Header[] allHeaders = response.getAllHeaders();
        Map<String, Collection<String>> headers = new HashMap<>(allHeaders.length);
        for (Header header : allHeaders) {
            headers.merge(header.getName(), singletonList(header.getValue()), (a, b) -> {
                a.addAll(b);
                return a;
            });
        }
        return headers;
    }

    private ProtocolVersion toProtocolVersion(String httpVersion) {
        switch (httpVersion) {
            case "HTTP/0.9":
            case "0.9":
                return HttpVersion.HTTP_0_9;
            case "HTTP/1.0":
            case "1.0":
                return HttpVersion.HTTP_1_0;
            case "HTTP/1.1":
            case "1.1":
                return HttpVersion.HTTP_1_1;
            default:
                throw new IllegalArgumentException("Invalid HTTP version: " + httpVersion);

        }
    }

}