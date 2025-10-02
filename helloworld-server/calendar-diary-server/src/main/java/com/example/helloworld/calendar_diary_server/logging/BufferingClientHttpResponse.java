// logging/BufferingClientHttpResponse.java
package com.example.helloworld.calendar_diary_server.logging;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class BufferingClientHttpResponse implements ClientHttpResponse {
    private final ClientHttpResponse delegate;
    private final byte[] body;

    public BufferingClientHttpResponse(ClientHttpResponse delegate) throws IOException {
        this.delegate = delegate;
        this.body = StreamUtils.copyToByteArray(delegate.getBody()); // 바디를 메모리에 복사
    }

    @Override public HttpStatusCode getStatusCode() throws IOException { return delegate.getStatusCode(); }
    @Override public String getStatusText() throws IOException { return delegate.getStatusText(); }
    @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
    @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
    @Override public void close() { delegate.close(); }

    // 편의: 로그에서 바로 쓰고 싶을 때
    public String bodyAsString() { return new String(body, StandardCharsets.UTF_8); }
}
