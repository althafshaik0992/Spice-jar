package com.example.foodapp.model;

import lombok.Data;
import java.util.List;

@Data
public class FedExShipResponse {

    private Output output;

    @Data
    public static class Output {
        private List<TransactionShipment> transactionShipments;
    }

    @Data
    public static class TransactionShipment {
        private List<PieceResponse> pieceResponses;
        private List<Document> shipmentDocuments;
    }

    @Data
    public static class PieceResponse {
        private String trackingNumber;
    }

    @Data
    public static class Document {
        private String contentType;  // application/pdf
        private String encodedLabel; // base64 string (name may vary)
    }
}
