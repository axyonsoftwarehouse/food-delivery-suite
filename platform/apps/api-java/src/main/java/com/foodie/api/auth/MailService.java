package com.foodie.api.auth;

public interface MailService {
    void send(String to, String subject, String body);
}
