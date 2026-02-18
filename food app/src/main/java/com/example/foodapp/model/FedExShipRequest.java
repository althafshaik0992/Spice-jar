package com.example.foodapp.model;

import lombok.Data;

import java.util.List;

@Data
public class FedExShipRequest {

    private AccountNumber accountNumber;
    private RequestedShipment requestedShipment;





    // ✅ REQUIRED by FedEx (you are missing)
    private String labelResponseOptions; // e.g. "URL_ONLY" or "LABEL"



    @Data
    public static class RequestedShipment {
        private Shipper shipper;
        private List<Recipient> recipients;
        private String serviceType;
        private String packagingType;

        // ✅ REQUIRED by FedEx (you are missing)
        private String pickupType; // e.g. "DROPOFF_AT_FEDEX_LOCATION"

        private ShippingChargesPayment shippingChargesPayment;
        private LabelSpecification labelSpecification;
        private List<RequestedPackageLineItem> requestedPackageLineItems;
    }

    @Data public static class AccountNumber { private String value; }


    @Data public static class Shipper { private Contact contact; private Address address; }
    @Data public static class Recipient { private Contact contact; private Address address; }

    @Data public static class Contact {
        private String personName;
        private String phoneNumber;
        private String companyName;
    }

    @Data public static class Address {
        private List<String> streetLines;
        private String city;
        private String stateOrProvinceCode;
        private String postalCode;
        private String countryCode;
    }

    @Data
    public static class ShippingChargesPayment {
        private String paymentType; // "SENDER"
    }

    @Data
    public static class LabelSpecification {
        private String imageType;   // "PDF"
        private String labelStockType; // "PAPER_85X11_TOP_HALF_LABEL"
    }

    @Data
    public static class RequestedPackageLineItem {
        private Weight weight;
    }

    @Data
    public static class Weight {
        private String units; // "LB"
        private double value; // 1.0
    }
}
