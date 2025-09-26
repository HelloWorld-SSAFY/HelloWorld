package com.example.helloworld.calendar_diary_server.service.gen;

public interface GmsImageGenClient {
    /**
     * 원본 이미지 바이트(초음파)로 캐리커처(이미지 PNG/JPG 바이트)를 생성한다.
     * prompt/옵션이 필요하면 파라미터 확장.
     */
    byte[] generateCaricature(byte[] sourceBytes);
}