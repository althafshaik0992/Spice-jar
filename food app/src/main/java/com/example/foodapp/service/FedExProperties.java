package com.example.foodapp.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "shipping.fedex")
public class FedExProperties {
    private boolean enabled = false;
    private String baseUrl;
    // Ship credentials
    private String shipClientId;
    private String shipClientSecret;

    // Track credentials
    private String trackClientId;
    private String trackClientSecret;
    private int timeoutMs = 8000;

    private String accountNumber;

    private String defaultServiceType; // FEDEX_GROUND

    private String shipperName;
    private String shipperCompany;
    private String shipperPhone;
    private String shipperStreet1;
    private String shipperCity;
    private String shipperState;
    private String shipperZip;
    private String shipperCountry;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }


    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
}
