package com.example.foodapp.config;


import com.example.foodapp.service.FedExAuthService;
import com.example.foodapp.service.FedExProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class FedExClientConfig {

    private final FedExProperties props;

    @Bean("fedExShipAuth")
    public FedExAuthService fedExShipAuth(WebClient.Builder builder) {
        return new FedExAuthService(
                props.getBaseUrl(),
                props.getShipClientId(),
                props.getShipClientSecret(),
                builder
        );
    }

    @Bean("fedExTrackAuth")
    public FedExAuthService fedExTrackAuth(WebClient.Builder builder) {
        return new FedExAuthService(
                props.getBaseUrl(),
                props.getTrackClientId(),
                props.getTrackClientSecret(),
                builder
        );
    }
}
