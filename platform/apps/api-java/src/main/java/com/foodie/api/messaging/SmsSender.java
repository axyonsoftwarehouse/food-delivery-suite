package com.foodie.api.messaging;

public interface SmsSender {
    String provider();

    void send(String phone, String message);
}
