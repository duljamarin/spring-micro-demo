package com.demo.review.scoring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Answers "is the local model actually usable right now?" for the UI banner.
 *
 * It hits ollama's own {@code /api/tags} rather than attempting a generation,
 * because the two failure modes a developer running this locally will
 * actually hit need to be told apart:
 *
 *   1. ollama isn't running at all        -> start it
 *   2. ollama is running, model not pulled -> `ollama pull <model>`
 *
 * A scoring attempt fails identically in both cases; /api/tags distinguishes
 * them, and does it in milliseconds instead of seconds.
 *
 * Uses the JDK HTTP client with a hard 2s timeout, on purpose: this probe is
 * called from a request thread serving the UI, so it has to come back fast
 * even when nothing is listening on the port.
 */
@Component
public class OllamaHealthProbe {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String baseUrl;

    public OllamaHealthProbe(@Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Status probe(String configuredModel) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(baseUrl) + "/api/tags"))
                .timeout(TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new Status(false, "ollama answered HTTP " + response.statusCode() + " at " + baseUrl);
            }
            // Substring match rather than parsing the tag list: ollama reports
            // models as "llama3.2:latest" while the configured name is usually
            // "llama3.2", so an equality check on a parsed list would report a
            // pulled model as missing.
            if (!response.body().contains(bareName(configuredModel))) {
                return new Status(false, "ollama is running but '" + configuredModel
                        + "' is not pulled - run: ollama pull " + configuredModel);
            }
            return new Status(true, "ollama is running and '" + configuredModel + "' is available");
        } catch (Exception ex) {
            return new Status(false, "ollama not reachable at " + baseUrl + " (" + ex.getClass().getSimpleName() + ")");
        }
    }

    private static String bareName(String model) {
        int colon = model.indexOf(':');
        return colon < 0 ? model : model.substring(0, colon);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record Status(boolean reachable, String detail) {
    }
}
