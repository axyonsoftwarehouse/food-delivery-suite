const fallback = 'http://127.0.0.1:4001';

export const API_URL = (process.env.EXPO_PUBLIC_API_URL ?? fallback).replace(/\/$/, '');

export const POLL_INTERVAL_MS = 8_000;

export const LATE_AFTER_MS = 10 * 60 * 1_000;

export const SESSION_TOKEN_KEY = 'foodie_kitchen_token';

export const SESSION_USER_KEY = 'foodie_kitchen_user';
