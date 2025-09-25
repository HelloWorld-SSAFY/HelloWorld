// service/gen/DummyGmsImageGenClient.java
package com.example.helloworld.calendar_diary_server.service.gen;

import org.springframework.stereotype.Component;

@Component
public class DummyGmsImageGenClient implements GmsImageGenClient {
    @Override public byte[] generateCaricature(byte[] sourceBytes) {
        // TODO: 여기에 실제 GMS 호출 붙이기
        return sourceBytes; // 일단 패스스루
    }
}
