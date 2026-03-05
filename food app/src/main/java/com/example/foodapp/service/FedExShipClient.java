package com.example.foodapp.service;

import com.example.foodapp.model.FedExShipRequest;
import com.example.foodapp.model.FedExShipResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class FedExShipClient {

    private final WebClient webClient;
    private final com.example.foodapp.service.FedExAuthService auth;

    public FedExShipClient(FedExProperties props, WebClient.Builder builder,
                           @org.springframework.beans.factory.annotation.Qualifier("fedExShipAuth")
                           FedExAuthService auth) {
        this.webClient = builder.baseUrl(props.getBaseUrl()).build();
        this.auth = auth;
    }

    public FedExShipResponse createShipment(FedExShipRequest req) {
        String token = auth.getValidToken();

        try {
            return webClient.post()
                    .uri("/ship/v1/shipments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .onStatus(status -> status.isError(), response ->
                            response.bodyToMono(String.class)
                                    .map(body -> new RuntimeException("FedEx Error: " + body))
                    )
                    .bodyToMono(FedExShipResponse.class)
                    .block();


        } catch (WebClientResponseException.Unauthorized ex) {
            auth.invalidateToken();
            String newToken = auth.getValidToken();

            return webClient.post()
                    .uri("/ship/v1/shipments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(FedExShipResponse.class)
                    .block();
        }
    }
}
