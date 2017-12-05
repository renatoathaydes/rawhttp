package com.athaydes.rawhttp.httpcomponents;

import com.athaydes.rawhttp.core.BodyReader.BodyType;
import com.athaydes.rawhttp.core.LazyBodyReader;
import com.athaydes.rawhttp.core.RawHttp;
import com.athaydes.rawhttp.core.RawHttpClient;
import com.athaydes.rawhttp.core.RawHttpHeaders;
import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;
import com.athaydes.rawhttp.core.StatusCodeLine;
import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.OptionalLong;

import static java.util.stream.Collectors.joining;

public class RawHttpComponentsClient implements RawHttpClient<CloseableHttpResponse> {

    private final CloseableHttpClient httpClient;

    public RawHttpComponentsClient() {
        this(HttpClients.createDefault());
    }

    public RawHttpComponentsClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public RawHttpResponse<CloseableHttpResponse> send(RawHttpRequest request) throws IOException {
        RequestBuilder builder = RequestBuilder.create(request.getMethod());
        builder.setUri(request.getUri());
        builder.setVersion(toProtocolVersion(request.getStartLine().getHttpVersion()));
        request.getHeaders().getHeaderNames().forEach((name) ->
                request.getHeaders().get(name).forEach(value ->
                        builder.addHeader(new BasicHeader(name, value))));

        request.getBody().ifPresent(b -> builder.setEntity(new InputStreamEntity(b.asStream())));

        CloseableHttpResponse response = httpClient.execute(builder.build());

        RawHttpHeaders headers = readHeaders(response);

        @Nullable LazyBodyReader body;
        if (response.getEntity() != null) {
            OptionalLong headerLength = RawHttp.parseContentLength(headers);
            @Nullable Long length = headerLength.isPresent() ? headerLength.getAsLong() : null;
            BodyType bodyType = RawHttp.getBodyType(headers, length);
            body = new LazyBodyReader(bodyType, response.getEntity().getContent(), length, false);
        } else {
            body = null;
        }

        return new RawHttpResponse<>(response, request, adaptStatus(response.getStatusLine()), headers, body);
    }

    private StatusCodeLine adaptStatus(StatusLine statusLine) {
        return new StatusCodeLine(statusLine.getProtocolVersion().toString(),
                statusLine.getStatusCode(), statusLine.getReasonPhrase());
    }

    private RawHttpHeaders readHeaders(CloseableHttpResponse response) {
        Header[] allHeaders = response.getAllHeaders();
        RawHttpHeaders.Builder headers = RawHttpHeaders.Builder.newBuilder();
        for (Header header : allHeaders) {
            String meta = header.getElements().length > 0 ?
                    ";" + Arrays.stream(header.getElements())
                            .flatMap(it -> Arrays.stream(it.getParameters()).map(v -> v.getName() + "=" + v.getValue()))
                            .collect(joining(";")) :
                    "";
            headers.with(header.getName(), header.getValue() + meta);
        }
        return headers.build();
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