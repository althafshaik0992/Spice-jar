package com.example.foodapp.service;

import com.example.foodapp.model.FedExTrackRequest;
import com.example.foodapp.model.FedExTrackResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;


@Component
public class FedExTrackClient {

    private final WebClient webClient;
    private final  FedExAuthService auth;

    public FedExTrackClient(FedExProperties props, WebClient.Builder builder,
                            @org.springframework.beans.factory.annotation.Qualifier("fedExTrackAuth")
                            FedExAuthService auth) {
        this.webClient = builder.baseUrl(props.getBaseUrl()).build();
        this.auth = auth;
    }


    public FedExTrackResponse trackByTrackingNumber(String trackingNumber) {
        String token = auth.getValidToken();

        FedExTrackRequest req = FedExTrackRequest.single(trackingNumber);

        try {
            return webClient.post()
                    .uri("/track/v1/trackingnumbers")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("X-locale", "en_US")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .onStatus(s -> s.isError(), resp ->
                            resp.bodyToMono(String.class)
                                    .map(body -> new RuntimeException("FedEx Track Error: " + body))
                    )
                    .bodyToMono(FedExTrackResponse.class)
                    .block();


        } catch (WebClientResponseException.Unauthorized ex) {
            // Best practice: refresh token on 401 :contentReference[oaicite:11]{index=11}
            auth.invalidateToken();
            String newToken = auth.getValidToken();

            return webClient.post()
                    .uri("track/v1/trackingnumbers")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken)
                    .header("X-locale", "en_US")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(FedExTrackResponse.class)
                    .block();
        }
    }
}
