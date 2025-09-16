package com.example.helloworld.userserver.notif;

import com.google.firebase.messaging.*;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FcmClient {

    public String sendToToken(String token, String title, String body, Map<String,String> data)
            throws FirebaseMessagingException {

        Message.Builder mb = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());

        if (data != null && !data.isEmpty()) mb.putAllData(data);

        return FirebaseMessaging.getInstance().send(mb.build());
    }
}
