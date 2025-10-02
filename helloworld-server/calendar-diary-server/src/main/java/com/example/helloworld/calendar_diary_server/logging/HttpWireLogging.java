// logging/HttpWireLogging.java
package com.example.helloworld.calendar_diary_server.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class HttpWireLogging implements ClientHttpRequestInterceptor {
    private static final Logger log = LoggerFactory.getLogger("WIRE.OUTBOUND");
    private static final int MAX = 4000;

    @Override
    public ClientHttpResponse intercept(HttpRequest req, byte[] body, ClientHttpRequestExecution ex) throws IOException {
        HttpHeaders maskedReq = mask(req.getHeaders());
        String reqBody = new String(body, StandardCharsets.UTF_8);

        log.info("\n=== OUTBOUND REQUEST ===\n{} {}\nHeaders: {}\nContent-Length: {}\nTransfer-Encoding: {}\nBody({}): {}\n",
                req.getMethod(), req.getURI(), maskedReq,
                maskedReq.getFirst(HttpHeaders.CONTENT_LENGTH),
                maskedReq.getFirst(HttpHeaders.TRANSFER_ENCODING),
                reqBody.length(), trunc(reqBody));

        ClientHttpResponse raw = ex.execute(req, body);
        BufferingClientHttpResponse resp = new BufferingClientHttpResponse(raw);

        HttpHeaders maskedResp = mask(resp.getHeaders());
        String respBody = resp.bodyAsString();

        // getRawStatusCode() 대신
        log.info("\n=== OUTBOUND RESPONSE ===\nStatus: {} {}\nHeaders: {}\nContent-Length: {}\nTransfer-Encoding: {}\nBody({}): {}\n",
                resp.getStatusCode().value(), resp.getStatusText(),
                maskedResp,
                maskedResp.getFirst(HttpHeaders.CONTENT_LENGTH),
                maskedResp.getFirst(HttpHeaders.TRANSFER_ENCODING),
                respBody.length(), trunc(respBody));

        return resp; // 바디 재사용 가능
    }

    private static String trunc(String s){ return (s==null||s.length()<=MAX)?s:s.substring(0,MAX)+"...(truncated)"; }

    private static HttpHeaders mask(HttpHeaders src){
        HttpHeaders h=new HttpHeaders();
        for(Map.Entry<String, List<String>> e: src.entrySet()){
            String k=e.getKey();
            if(k.equalsIgnoreCase(HttpHeaders.AUTHORIZATION)){
                h.put(k, List.of("Bearer ***MASKED***"));
            }else h.put(k, e.getValue());
        }
        return h;
    }
}
