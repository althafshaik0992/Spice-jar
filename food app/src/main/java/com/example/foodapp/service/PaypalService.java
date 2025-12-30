// src/main/java/com/example/foodapp/service/PaypalService.java
package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class PaypalService {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final RestTemplate restTemplate = new RestTemplate();

    public record StartOut(boolean ok, String approvalUrl, Long paymentId) {}
    public record CaptureOut(boolean ok, Long orderId, Long paymentId) {}

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

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
            if (order == null) return new StartOut(false, null, null);

            BigDecimal amountToCharge = order.getRemainingToPay();
            if (amountToCharge == null || amountToCharge.compareTo(BigDecimal.ZERO) <= 0) {
                amountToCharge = order.getGrandTotal();
            }
            if (amountToCharge == null) amountToCharge = BigDecimal.ZERO;
            amountToCharge = amountToCharge.setScale(2, RoundingMode.HALF_UP);

            // ✅ create payment row FIRST
            Payment payment = paymentService.start(order, amountToCharge, currency, "PAYPAL");

            String accessToken = getAccessToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("intent", "CAPTURE");

            Map<String, Object> purchaseUnit = new HashMap<>();
            Map<String, String> amount = new HashMap<>();
            amount.put("currency_code", currency);
            amount.put("value", amountToCharge.toPlainString());
            purchaseUnit.put("amount", amount);

            requestBody.put("purchase_units", List.of(purchaseUnit));
            requestBody.put("application_context", Map.of(
                    "return_url", appUrl + "/payment/paypal/return",
                    "cancel_url", appUrl + "/orders"
            ));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            var response = restTemplate.exchange(
                    paypalApiUrl + "/v2/checkout/orders",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return new StartOut(false, null, null);

            String paypalOrderId = (String) responseBody.get("id");

            String approvalUrl = null;
            List<Map<String, Object>> links = (List<Map<String, Object>>) responseBody.get("links");
            if (links != null) {
                for (Map<String, Object> link : links) {
                    if ("approve".equals(link.get("rel"))) {
                        approvalUrl = (String) link.get("href");
                        break;
                    }
                }
            }

            // ✅ store PayPal order id
            paymentService.attachProviderPaymentId(payment.getId(), paypalOrderId);

            return new StartOut(true, approvalUrl, payment.getId());

        } catch (Exception e) {
            e.printStackTrace();
            return new StartOut(false, null, null);
        }
    }

    public CaptureOut capture(String paypalOrderId) {
        try {
            String accessToken = getAccessToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            var response = restTemplate.exchange(
                    paypalApiUrl + "/v2/checkout/orders/" + paypalOrderId + "/capture",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return new CaptureOut(false, null, null);

            String status = (String) responseBody.get("status");
            if (!"COMPLETED".equalsIgnoreCase(status)) {
                return new CaptureOut(false, null, null);
            }

            // captureId extraction
            List<Map<String, Object>> purchaseUnits = (List<Map<String, Object>>) responseBody.get("purchase_units");
            Map<String, Object> payments = (Map<String, Object>) purchaseUnits.get(0).get("payments");
            List<Map<String, Object>> captures = (List<Map<String, Object>>) payments.get("captures");
            String captureId = (String) captures.get(0).get("id");

            Payment p = paymentService.findByProviderPaymentId(paypalOrderId);
            if (p == null) return new CaptureOut(false, null, null);

            // ✅ mark SUCCEEDED
            paymentService.markSucceededByProviderPaymentId(paypalOrderId, captureId, "PAYPAL");

            Long orderId = p.getOrder().getId();
            return new CaptureOut(true, orderId, p.getId());

        } catch (Exception e) {
            e.printStackTrace();
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

    public String refundPaypalPayment(String captureId, BigDecimal amount) {
        try {
            String accessToken = getAccessToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "amount", Map.of(
                            "currency_code", "USD",
                            "value", amount.toPlainString()
                    )
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            var response = restTemplate.exchange(
                    paypalApiUrl + "/v2/payments/captures/" + captureId + "/refund",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map<String, Object> respBody = response.getBody();
            return respBody != null ? (String) respBody.get("id") : null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
