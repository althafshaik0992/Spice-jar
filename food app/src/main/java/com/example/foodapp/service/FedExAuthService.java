package com.example.foodapp.service;

import lombok.Getter;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

public class FedExAuthService {

    private final String clientId;
    private final String clientSecret;
    private final WebClient webClient;

    private final ReentrantLock lock = new ReentrantLock();

    @Getter
    private volatile String accessToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public FedExAuthService(String baseUrl, String clientId, String clientSecret, WebClient.Builder builder) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public String getValidToken() {
        if (accessToken != null && Instant.now().isBefore(expiresAt.minusSeconds(60))) {
            return accessToken;
        }

        lock.lock();
        try {
            if (accessToken != null && Instant.now().isBefore(expiresAt.minusSeconds(60))) {
                return accessToken;
            }

            TokenResponse resp = webClient.post()
                    .uri("oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue("grant_type=client_credentials"
                            + "&client_id=" + clientId
                            + "&client_secret=" + clientSecret)
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            if (resp == null || resp.access_token == null) {
                throw new IllegalStateException("FedEx OAuth token response was empty");
            }

            this.accessToken = resp.access_token;
            this.expiresAt = Instant.now().plusSeconds(resp.expires_in);
            return accessToken;

        } finally {
            lock.unlock();
        }
    }

    public void invalidateToken() {
        this.accessToken = null;
        this.expiresAt = Instant.EPOCH;
    }

    static class TokenResponse {
        public String access_token;
        public String token_type;
        public long expires_in;
        public String scope;
    }
}
