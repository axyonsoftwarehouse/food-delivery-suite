package com.foodie.api.notifications;

public interface PushSender {
    boolean configured();

    String publicKey();

    void send(String endpoint, String p256dh, String auth, String payload);
}
