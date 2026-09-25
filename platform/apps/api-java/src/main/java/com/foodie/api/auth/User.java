package com.foodie.api.auth;

public record User(long id, String name, String email, String role, Long restaurantId) {}
