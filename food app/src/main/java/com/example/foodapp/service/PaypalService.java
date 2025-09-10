package com.example.foodapp.service;

import com.example.foodapp.model.Payment;
import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.orders.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaypalService {

    private final PayPalHttpClient client;

    public PaypalService(
            @Value("${paypal.client.id}") String clientId,
            @Value("${paypal.client.secret}") String clientSecret,
            @Value("${paypal.mode:sandbox}") String mode) {

        PayPalEnvironment env = "live".equalsIgnoreCase(mode)
                ? new PayPalEnvironment.Live(clientId, clientSecret)
                : new PayPalEnvironment.Sandbox(clientId, clientSecret);

        this.client = new PayPalHttpClient(env);
    }

    /** Create a PayPal Order for the given Payment row */
    public Map<String,String> createOrder(Payment p) throws Exception {
        OrdersCreateRequest req = new OrdersCreateRequest();
        req.header("prefer", "return=representation");

        String amount = money(p.getAmount());

        OrderRequest order = new OrderRequest();
        order.checkoutPaymentIntent("CAPTURE");
        PurchaseUnitRequest unit = new PurchaseUnitRequest()
                .referenceId("pay-" + p.getId())
                .amountWithBreakdown(new AmountWithBreakdown().currencyCode("USD").value(amount));
        order.purchaseUnits(List.of(unit));

        req.requestBody(order);

        HttpResponse<Order> resp = client.execute(req);
        Map<String,String> out = new HashMap<>();
        out.put("id", resp.result().id());
        out.put("status", resp.result().status());
        return out;
    }

    /** Capture a PayPal Order */
    public Map<String,String> captureOrder(String paypalOrderId) throws Exception {
        OrdersCaptureRequest req = new OrdersCaptureRequest(paypalOrderId);
        req.requestBody(new OrderRequest());

        HttpResponse<Order> resp = client.execute(req);
        var capture = resp.result().purchaseUnits().get(0).payments().captures().get(0);

        Map<String,String> out = new HashMap<>();
        out.put("status", capture.status());
        out.put("captureId", capture.id());
        out.put("amount", capture.amount().value());
        return out;
    }

    private String money(BigDecimal v){
        return v.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString();
    }
}
