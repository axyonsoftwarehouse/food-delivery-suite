package com.foodie.api.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class SentryService {
    private static final Logger log = LoggerFactory.getLogger(SentryService.class);
    private static final ParameterizedTypeReference<List<Map<String, Object>>> ISSUES = new ParameterizedTypeReference<>() {};

    private final String orgSlug;
    private final String token;
    private final List<ProjectConfig> projects;
    private final long cacheSeconds;
    private final RestClient client;

    private volatile Summary cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    public SentryService(@Value("${app.sentry.api-url:https://sentry.io/api/0}") String apiUrl,
                         @Value("${app.sentry.org-slug:axyon-software-house}") String orgSlug,
                         @Value("${app.sentry.projects:foodie-api,foodie-web}") String projects,
                         @Value("${app.sentry.api-token:}") String token,
                         @Value("${app.sentry.cache-seconds:60}") long cacheSeconds) {
        this.orgSlug = orgSlug;
        this.token = token == null ? "" : token.strip();
        this.projects = Arrays.stream(projects.split(","))
            .map(String::strip)
            .filter((slug) -> !slug.isBlank())
            .map((slug) -> new ProjectConfig(slug, projectName(slug)))
            .toList();
        this.cacheSeconds = Math.max(0, cacheSeconds);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        String baseUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public Summary summary() {
        if (token.isBlank()) {
            return unavailable("Sentry não configurado: defina SENTRY_API_TOKEN no servidor.");
        }
        Instant now = Instant.now();
        Summary current = cached;
        if (current != null && now.isBefore(cachedAt.plusSeconds(cacheSeconds))) {
            return current;
        }
        synchronized (this) {
            now = Instant.now();
            current = cached;
            if (current != null && now.isBefore(cachedAt.plusSeconds(cacheSeconds))) {
                return current;
            }
            Summary fresh = load();
            cached = fresh;
            cachedAt = now;
            return fresh;
        }
    }

    private Summary load() {
        List<ProjectSummary> result = new ArrayList<>();
        long totalIssues = 0;
        long totalEvents = 0;
        boolean available = true;
        String message = null;

        for (ProjectConfig project : projects) {
            try {
                List<Map<String, Object>> issues = client.get()
                    .uri((builder) -> builder.path("/organizations/{org}/issues/")
                        .queryParam("query", "is:unresolved project:" + project.slug())
                        .queryParam("limit", 100)
                        .queryParam("sort", "freq")
                        .build(orgSlug))
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(ISSUES);
                List<Map<String, Object>> list = issues == null ? List.of() : issues;
                long events = list.stream().mapToLong((issue) -> number(issue.get("count"))).sum();
                List<Map<String, Object>> sorted = new ArrayList<>(list);
                sorted.sort(Comparator.comparingLong((Map<String, Object> issue) -> number(issue.get("count"))).reversed());
                List<IssueSummary> top = sorted.stream()
                    .limit(5)
                    .map((issue) -> new IssueSummary(
                        text(issue.get("shortId")),
                        text(issue.get("title")),
                        number(issue.get("count")),
                        text(issue.get("level")),
                        text(issue.get("lastSeen")),
                        text(issue.get("permalink"))))
                    .toList();
                result.add(new ProjectSummary(project.slug(), project.name(), list.size(), events, issuesUrl(project.slug()), top));
                totalIssues += list.size();
                totalEvents += events;
            } catch (RestClientException error) {
                available = false;
                message = "Não foi possível consultar o Sentry agora.";
                log.warn("Falha ao consultar o Sentry para o projeto {}: {}", project.slug(), error.getMessage());
                result.add(new ProjectSummary(project.slug(), project.name(), 0, 0, issuesUrl(project.slug()), List.of()));
            }
        }

        return new Summary(true, available, orgSlug, Instant.now().toString(), totalIssues, totalEvents, result, message);
    }

    private Summary unavailable(String message) {
        List<ProjectSummary> links = projects.stream()
            .map((project) -> new ProjectSummary(project.slug(), project.name(), 0, 0, issuesUrl(project.slug()), List.of()))
            .toList();
        return new Summary(false, false, orgSlug, Instant.now().toString(), 0, 0, links, message);
    }

    private String issuesUrl(String slug) {
        return "https://" + orgSlug + ".sentry.io/issues/?project=" + slug;
    }

    private static String projectName(String slug) {
        return switch (slug) {
            case "foodie-api" -> "Foodie API";
            case "foodie-web" -> "Foodie Web";
            default -> slug;
        };
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record ProjectConfig(String slug, String name) {}

    public record IssueSummary(String shortId, String title, long count, String level, String lastSeen, String url) {}

    public record ProjectSummary(String slug, String name, long issues, long events, String url, List<IssueSummary> topIssues) {}

    public record Summary(boolean configured, boolean available, String organization, String generatedAt,
                          long totalIssues, long totalEvents, List<ProjectSummary> projects, String message) {}
}
