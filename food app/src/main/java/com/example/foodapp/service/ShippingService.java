package com.example.foodapp.service;


import com.example.foodapp.model.FedExShipRequest;
import com.example.foodapp.model.FedExShipResponse;
import com.example.foodapp.model.Order;
import com.example.foodapp.model.Product;
import com.example.foodapp.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingService {

    private final FedExShipClient fedExShipClient;
    private final FedExProperties props;
    private final OrderRepository orderRepository;
    private final ProductService productService;

    /**
     * Creates a FedEx shipment via Ship API, saves tracking number + status = SHIPPED.
     */
    public void createFedExShipment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // Validate early so you don’t waste FedEx calls and you don’t get random NPEs
        validateOrderForShipping(order);

        FedExShipRequest req = buildRequest(order);

        FedExShipResponse resp = fedExShipClient.createShipment(req);

        String trackingNumber = extractTrackingNumber(resp);
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalStateException("FedEx did not return a tracking number.");
        }

        order.setCarrier("FEDEX");
        order.setTrackingNumber(trackingNumber);

        // Your Order has field "ShippingService" (capital S) — Lombok usually still generates setShippingService()
        order.setShippingService(req.getRequestedShipment().getServiceType());

        order.setStatus("SHIPPED");
        order.setShippedAt(LocalDateTime.now());

        orderRepository.save(order);
    }

    // -------------------- Request builder --------------------

    private FedExShipRequest buildRequest(Order order) {

        FedExShipRequest req = new FedExShipRequest();

        // ✅ REQUIRED BY FEDEX (missing earlier)
        req.setLabelResponseOptions("LABEL"); // or "URL_ONLY" depending on your preference

        // account number (must be configured)
        FedExShipRequest.AccountNumber an = new FedExShipRequest.AccountNumber();
        an.setValue(required(trimToNull(props.getAccountNumber()),
                "FedEx account number (shipping.fedex.account-number)"));
        req.setAccountNumber(an);

        FedExShipRequest.RequestedShipment rs = new FedExShipRequest.RequestedShipment();

        // ✅ REQUIRED BY FEDEX (missing earlier)
        rs.setPickupType("DROPOFF_AT_FEDEX_LOCATION");

        // shipper = YOUR business address (from properties)
        FedExShipRequest.Shipper shipper = new FedExShipRequest.Shipper();
        shipper.setContact(shipperContact());
        shipper.setAddress(shipperAddress());
        rs.setShipper(shipper);

        // recipient = customer address (from Order)
        FedExShipRequest.Recipient recipient = new FedExShipRequest.Recipient();
        recipient.setContact(recipientContact(order));
        recipient.setAddress(recipientAddress(order));
        rs.setRecipients(List.of(recipient));

        rs.setServiceType(required(trimToNull(props.getDefaultServiceType()),
                "FedEx default service type (shipping.fedex.default-service-type)"));
        rs.setPackagingType("YOUR_PACKAGING");

        FedExShipRequest.ShippingChargesPayment pay = new FedExShipRequest.ShippingChargesPayment();
        pay.setPaymentType("SENDER");
        rs.setShippingChargesPayment(pay);

        FedExShipRequest.LabelSpecification label = new FedExShipRequest.LabelSpecification();
        label.setImageType("PDF");
        label.setLabelStockType("PAPER_85X11_TOP_HALF_LABEL");
        rs.setLabelSpecification(label);

        // package info (keep simple for now; FedEx requires a weight)
        FedExShipRequest.RequestedPackageLineItem pkg = new FedExShipRequest.RequestedPackageLineItem();

        double totalWeightLb = calculateTotalWeightLb(order);

        FedExShipRequest.Weight w = new FedExShipRequest.Weight();
        w.setUnits("LB");
        w.setValue(totalWeightLb);
        pkg.setWeight(w);

        rs.setRequestedPackageLineItems(List.of(pkg));

        req.setRequestedShipment(rs);
        return req;
    }


    private FedExShipRequest.Contact shipperContact() {
        FedExShipRequest.Contact c = new FedExShipRequest.Contact();
        c.setPersonName(required(trimToNull(props.getShipperName()), "Shipper name (shipping.fedex.shipper-name)"));
        c.setCompanyName(required(trimToNull(props.getShipperCompany()), "Shipper company (shipping.fedex.shipper-company)"));
        c.setPhoneNumber(required(normalizePhone(props.getShipperPhone()), "Shipper phone (shipping.fedex.shipper-phone)"));
        return c;
    }

    private FedExShipRequest.Address shipperAddress() {
        FedExShipRequest.Address a = new FedExShipRequest.Address();

        String street1 = required(trimToNull(props.getShipperStreet1()), "Shipper street (shipping.fedex.shipper-street1)");
        String city    = required(trimToNull(props.getShipperCity()), "Shipper city (shipping.fedex.shipper-city)");
        String state   = required(trimToNull(props.getShipperState()), "Shipper state (shipping.fedex.shipper-state)");
        String zip     = required(trimToNull(props.getShipperZip()), "Shipper zip (shipping.fedex.shipper-zip)");
        String country = required(trimToNull(props.getShipperCountry()), "Shipper country (shipping.fedex.shipper-country)");

        a.setStreetLines(List.of(street1));
        a.setCity(city);
        a.setStateOrProvinceCode(state);
        a.setPostalCode(zip);
        a.setCountryCode(country);
        return a;
    }

    private FedExShipRequest.Contact recipientContact(Order o) {
        FedExShipRequest.Contact c = new FedExShipRequest.Contact();

        String name = trimToNull(o.getCustomerName());
        c.setPersonName(name != null ? name : "Customer");

        // FedEx generally expects phone; validate and normalize
        String phone = normalizePhone(o.getPhone());
        if (phone == null) {
            throw new IllegalArgumentException("Order is missing phone number (required for shipping). OrderId=" + o.getId());
        }
        c.setPhoneNumber(phone);

        c.setCompanyName("HOME");
        return c;
    }

    private FedExShipRequest.Address recipientAddress(Order o) {

        // Your Order fields are: street, city, state, zip, country
        String street = trimToNull(o.getStreet());
        String city   = trimToNull(o.getCity());
        String state  = trimToNull(o.getState());
        String zip    = trimToNull(o.getZip());


        // FedEx requires these minimum address fields
        if (street == null || city == null || state == null || zip == null) {
            throw new IllegalArgumentException(
                    "Order shipping address is missing. Required: street, city, state, zip. " +
                            "OrderId=" + o.getId() +
                            " street=" + street + ", city=" + city + ", state=" + state + ", zip=" + zip
            );
        }
        String country = normalizeCountryCode(o.getCountry()); // ✅ fix here
        FedExShipRequest.Address a = new FedExShipRequest.Address();
        a.setStreetLines(List.of(street)); // ✅ safe (not null)
        a.setCity(city);
        a.setStateOrProvinceCode(state);
        a.setPostalCode(zip);
        a.setCountryCode(country);
        return a;
    }

    // -------------------- Response parsing --------------------

    private String extractTrackingNumber(FedExShipResponse resp) {
        if (resp == null || resp.getOutput() == null) return null;
        if (resp.getOutput().getTransactionShipments() == null || resp.getOutput().getTransactionShipments().isEmpty()) return null;

        var ts = resp.getOutput().getTransactionShipments().get(0);
        if (ts.getPieceResponses() == null || ts.getPieceResponses().isEmpty()) return null;

        return ts.getPieceResponses().get(0).getTrackingNumber();
    }


    private String normalizeCountryCode(String country) {
        String c = trimToNull(country);
        if (c == null) return "US";

        c = c.trim().toUpperCase();

        // Common user inputs -> ISO2
        if (c.equals("UNITED STATES") || c.equals("UNITED STATES OF AMERICA") || c.equals("USA")) return "US";
        if (c.equals("INDIA")) return "IN";

        // If already ISO2
        if (c.length() == 2) return c;

        // Default
        return "US";
    }

    // -------------------- Validation helpers --------------------

    private void validateOrderForShipping(Order o) {
        // Address fields (your Order model)
        if (trimToNull(o.getStreet()) == null ||
                trimToNull(o.getCity()) == null ||
                trimToNull(o.getState()) == null ||
                trimToNull(o.getZip()) == null) {
            throw new IllegalArgumentException(
                    "Order shipping address incomplete. Please fill street/city/state/zip. OrderId=" + o.getId()
            );
        }

        if (normalizePhone(o.getPhone()) == null) {
            throw new IllegalArgumentException("Order phone is missing/invalid. OrderId=" + o.getId());
        }
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Keep only digits. Return null if nothing usable.
     * FedEx typically accepts 10 digits for US. If you store +1XXXXXXXXXX it’ll still normalize.
     */
    private String normalizePhone(String phone) {
        String p = trimToNull(phone);
        if (p == null) return null;

        String digits = p.replaceAll("\\D+", "");
        if (digits.isBlank()) return null;

        // If user stored 11 digits starting with 1, keep it; else keep last 10 for US-style numbers
        if (digits.length() == 11 && digits.startsWith("1")) return digits;
        if (digits.length() >= 10) return digits.substring(digits.length() - 10);

        // too short
        return null;
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required config: " + name);
        }
        return value;
    }



    private double calculateTotalWeightLb(Order order) {
        // If no items, fallback to 1 lb (FedEx requires weight)
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return 1.0;
        }

        double totalGrams = 0.0;

        for (var item : order.getItems()) {
            if (item == null) continue;

            Long productId = item.getProductId();
            int qty = item.getQuantity() != null ? item.getQuantity() : 1;
            if (qty <= 0) qty = 1;

            // If productId is null (gift card / special rows), skip it for shipping
            if (productId == null || productId < 0) {
                continue;
            }

            Product p = productService.findById(productId);
            if (p == null) {
                // If product missing, skip (or you can throw)
                continue;
            }

            Integer grams = p.getWeight(); // ✅ your Product has weight
            if (grams == null || grams <= 0) {
                continue;
            }

            totalGrams += (double) grams * qty;
        }

        // If still 0, fallback to 1 lb
        if (totalGrams <= 0.0) {
            return 1.0;
        }

        double lb = totalGrams / 453.59237;

        // FedEx doesn’t like tiny weights; set a minimum like 0.2 lb
        if (lb < 0.2) lb = 0.2;

        // Round to 2 decimals
        lb = Math.round(lb * 100.0) / 100.0;

        return lb;
    }

}
