package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@Service
public class PaypalService {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final RestTemplate restTemplate = new RestTemplate();

    public record StartOut(boolean ok, String approvalUrl, Long paymentId) {}
    public record CaptureOut(boolean ok, Long orderId, Long paymentId) {}

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    // your client/secret used in your internal HTTP client to PayPal REST
    @Value("${paypal.client.id}")
    private String clientId;
    @Value("${paypal.client.secret}")
    private String secret;
    @Value("${paypal.api.url:https://api-m.sandbox.paypal.com}")
    private String paypalApiUrl;

    public PaypalService(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    public StartOut createOrder(Long orderId, String currency) {
        try {
            Order order = orderService.findById(orderId);
            if (order == null) {
                return new StartOut(false, null, null);
            }

            // ---------- IMPORTANT: use remainingToPay (after discount + gift card) ----------
            BigDecimal amountToCharge = order.getRemainingToPay();
            if (amountToCharge == null || amountToCharge.compareTo(BigDecimal.ZERO) <= 0) {
                // fallback if no gift card applied yet
                amountToCharge = order.getGrandTotal();
            }
            if (amountToCharge == null) {
                amountToCharge = BigDecimal.ZERO;
            }
            amountToCharge = amountToCharge.setScale(2, RoundingMode.HALF_UP);
            // ------------------------------------------------------------------------ //

            // create our internal payment row with the same amount
            var payment = paymentService.start(orderId, amountToCharge, currency, "PAYPAL");

            // --- REAL PAYPAL API INTEGRATION ---
            // 1. Get an access token from PayPal
            String accessToken = getAccessToken();

            // 2. Build the PayPal order request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("intent", "CAPTURE");

            Map<String, Object> purchaseUnit = new HashMap<>();
            Map<String, String> amount = new HashMap<>();
            amount.put("currency_code", currency);
            // use the same remainingToPay amount we computed above
            amount.put("value", amountToCharge.toPlainString());
            purchaseUnit.put("amount", amount);
            requestBody.put("purchase_units", List.of(purchaseUnit));

            Map<String, Object> applicationContext = new HashMap<>();
            applicationContext.put("return_url", appUrl + "/payment/paypal/return");
            applicationContext.put("cancel_url", appUrl + "/orders");
            requestBody.put("application_context", applicationContext);

            // 3. Set up HTTP headers for the API call
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 4. Make the API call to create the order
            var response = restTemplate.exchange(
                    paypalApiUrl + "/v2/checkout/orders",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            // 5. Extract the PayPal order ID and approval URL from the response
            Map<String, Object> responseBody = response.getBody();
            String paypalOrderId = (String) responseBody.get("id");

            String approvalUrl = null;
            List<Map<String, Object>> links = (List<Map<String, Object>>) responseBody.get("links");
            for (Map<String, Object> link : links) {
                if ("approve".equals(link.get("rel"))) {
                    approvalUrl = (String) link.get("href");
                    break;
                }
            }

            // Store the real PayPal order ID in our database
            paymentService.attachProviderPaymentId(payment.getId(), paypalOrderId);
            // --- END REAL PAYPAL API INTEGRATION ---

            return new StartOut(true, approvalUrl, payment.getId());
        } catch (Exception e) {
            System.err.println("Error creating PayPal order: " + e.getMessage());
            return new StartOut(false, null, null);
        }
    }

    public CaptureOut capture(String paypalOrderId) {
        try {
            // --- REAL PAYPAL API INTEGRATION ---
            // 1. Get a new access token
            String accessToken = getAccessToken();

            // 2. Set up HTTP headers
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 3. Call PayPal's capture API
            var response = restTemplate.exchange(
                    paypalApiUrl + "/v2/checkout/orders/" + paypalOrderId + "/capture",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            // 4. Get the capture ID from the response
            Map<String, Object> responseBody = response.getBody();
            List<Map<String, Object>> purchaseUnits = (List<Map<String, Object>>) responseBody.get("purchase_units");
            Map<String, Object> payments = (Map<String, Object>) purchaseUnits.get(0).get("payments");
            List<Map<String, Object>> captures = (List<Map<String, Object>>) payments.get("captures");
            String captureId = (String) captures.get(0).get("id");

            // 5. Check if the payment was successful
            String status = (String) responseBody.get("status");
            if (!"COMPLETED".equals(status)) {
                return new CaptureOut(false, null, null);
            }

            var p = paymentService.findByProviderPaymentId(paypalOrderId);
            if (p == null) return new CaptureOut(false, null, null);

            paymentService.markSucceededByProviderPaymentId(paypalOrderId, captureId, "PAYPAL");
            return new CaptureOut(true, p.getOrderId(), p.getId());

        } catch (Exception e) {
            System.err.println("Error capturing PayPal payment: " + e.getMessage());
            return new CaptureOut(false, null, null);
        }
    }

    private String getAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, secret);
        String body = "grant_type=client_credentials";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        var response = restTemplate.exchange(
                paypalApiUrl + "/v1/oauth2/token",
                HttpMethod.POST,
                entity,
                Map.class
        );

        return (String) response.getBody().get("access_token");
    }
}
