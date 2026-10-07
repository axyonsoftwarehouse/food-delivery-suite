package com.foodie.api.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PushEndpointsTest {
    @Test
    void acceptsTheBrowserPushServices() {
        assertThat(PushEndpoints.allowed("https://fcm.googleapis.com/fcm/send/abc:def")).isTrue();
        assertThat(PushEndpoints.allowed("https://updates.push.services.mozilla.com/wpush/v2/abc")).isTrue();
        assertThat(PushEndpoints.allowed("https://web.push.apple.com/QK1abc")).isTrue();
        assertThat(PushEndpoints.allowed("https://wns2-bl2p.notify.windows.com/w/?token=abc")).isTrue();
    }

    @Test
    void refusesInternalOrLookalikeAddresses() {
        assertThat(PushEndpoints.allowed("http://fcm.googleapis.com/fcm/send/abc")).isFalse();
        assertThat(PushEndpoints.allowed("https://db:3306/")).isFalse();
        assertThat(PushEndpoints.allowed("https://127.0.0.1/admin")).isFalse();
        assertThat(PushEndpoints.allowed("https://169.254.169.254/latest/meta-data")).isFalse();
        assertThat(PushEndpoints.allowed("https://fcm.googleapis.com.evil.example/x")).isFalse();
        assertThat(PushEndpoints.allowed("https://evilnotify.windows.com.example/x")).isFalse();
        assertThat(PushEndpoints.allowed("https://user@fcm.googleapis.com/x")).isFalse();
        assertThat(PushEndpoints.allowed("https://fcm.googleapis.com:8443/x")).isFalse();
        assertThat(PushEndpoints.allowed("not a url")).isFalse();
        assertThat(PushEndpoints.allowed(null)).isFalse();
    }
}
