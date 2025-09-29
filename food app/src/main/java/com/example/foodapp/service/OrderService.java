package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.foodapp.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final String CONF_PREFIX = "TSJ-";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository repo;

    public OrderService(OrderRepository repo) {
        this.repo = repo;
    }

    /**
     * Save an order. If it's new (or missing fields), we will:
     *  - set createdAt if null
     *  - set confirmationNumber if blank (ensuring uniqueness when repository supports it)
     */
    public Order save(Order o) {
        if (o == null) return null;

        // createdAt fallback
        if (o.getCreatedAt() == null) {
            o.setCreatedAt(LocalDateTime.now());
        }

        // confirmation number fallback
        if (isBlank(o.getConfirmationNumber())) {
            o.setConfirmationNumber(generateUniqueConfirmationNumber());
        }

        return repo.save(o);
    }

    public List<Order> findAll() {
        return repo.findAll();
    }

    public Order findById(Long id) {
        return repo.findById(id).orElse(null);
    }

    /** Returns the newest order placed by the given user, or null if none. */
    public Order findLatestForUser(Long userId) {
        return repo.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
    }

    /** Finds, filters, and sorts all orders for the admin/orders page. */
    public List<Order> findOrders(String q, java.time.LocalDate from, java.time.LocalDate to, String sort) {
        return filterAndSort(findAll(), q, from, to, sort);
    }

    /** Filters and sorts a given list of orders. */
    public List<Order> filterAndSort(List<Order> orders, String q, java.time.LocalDate from, java.time.LocalDate to, String sort) {
        String needle = q != null ? q.toLowerCase() : "";

        return orders.stream()
                .filter(o -> {
                    boolean hit = true;
                    // date filters
                    if (from != null && o.getCreatedAt() != null && o.getCreatedAt().toLocalDate().isBefore(from)) hit = false;
                    if (to   != null && o.getCreatedAt() != null && o.getCreatedAt().toLocalDate().isAfter(to))   hit = false;

                    // search query: name, address, or item names
                    if (hit && !needle.isBlank()) {
                        boolean inName = o.getCustomerName() != null && o.getCustomerName().toLowerCase().contains(needle);
                        boolean inAddr = o.getAddress() != null && o.getAddress().toLowerCase().contains(needle);
                        boolean inItems = o.getItems() != null && o.getItems().stream()
                                .anyMatch(it -> it != null && it.getProductName() != null &&
                                        it.getProductName().toLowerCase().contains(needle));
                        hit = inName || inAddr || inItems;
                    }
                    return hit;
                })
                .sorted(getComparator(sort))
                .collect(Collectors.toList());
    }

    private Comparator<Order> getComparator(String sort) {
        Comparator<Order> byDateAsc = Comparator.comparing(
                Order::getCreatedAt,
                (a, b) -> {
                    if (Objects.equals(a, b)) return 0;
                    if (a == null) return 1;   // nulls last
                    if (b == null) return -1;
                    return a.compareTo(b);
                }
        );
        return "dateAsc".equalsIgnoreCase(sort) ? byDateAsc : byDateAsc.reversed();
    }

    public Order markPaid(Long orderId) {
        Order o = findById(orderId);
        if (o == null) return null;
        o.setStatus("PAID");
        return repo.save(o);
    }

    public void markPendingCod(Long orderId) {
        Order o = findById(orderId);
        if (o == null) return;
        o.setStatus("PENDING_COD");
        repo.save(o);
    }

    /* -------------------------
       Confirmation ID helpers
       ------------------------- */

    /** Public for controllers/tests: returns a unique, user-friendly confirmation number. */
    public String generateUniqueConfirmationNumber() {
        // Try a few times in the unlikely case of collision
        for (int i = 0; i < 5; i++) {
            String candidate = buildConfirmationNumber();
            // If the repository supports a uniqueness check, use it:
            if (!existsByConfirmation(candidate)) return candidate;
            // otherwise loop; collision is extremely unlikely with UUID
        }
        // Fallback: last attempt regardless
        return buildConfirmationNumber();
    }

    private String buildConfirmationNumber() {
        String datePart = LocalDateTime.now().format(DATE_FMT);           // e.g. 20250917
        String randomPart = UUID.randomUUID().toString()
                .replace("-", "").substring(0, 8).toUpperCase();          // 8 chars
        return CONF_PREFIX + datePart + "-" + randomPart;
    }

    private boolean existsByConfirmation(String conf) {
        try {
            // Prefer an existence check to avoid loading entire entity
            // Define in OrderRepository:
            //   boolean existsByConfirmationNumber(String confirmationNumber);
            return repo.existsByConfirmationNumber(conf);
        } catch (Throwable ignored) {
            // If the repo method doesn't exist, just assume it's unique.
            return false;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public long countAll() {
        return repo.count();
    }

    public double calculateTotalRevenue() {
        // Retrieve all orders from the database
        List<Order> orders = repo.findAll();

        // Use a Java Stream to filter out null totals and sum the rest
        return orders.stream()
                .map(Order::getTotal)
                .filter(total -> total != null)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
    }

    // Placeholder methods for chart data, replace with your actual implementation
    public List<String> getOrdersByDayLabels() {
        return List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");
    }

    public List<Double> getOrdersByDayValues() {
        return List.of(25.0, 30.0, 28.0, 45.0, 50.0, 35.0, 20.0);
    }




    public boolean canCancel(Order o) {
        if (o == null || o.getStatus() == null) return false;
        // Example rule: cancel only before shipment (tune as needed)
        return List.of("PENDING_PAYMENT","PENDING_COD","PAID").contains(o.getStatus());
    }

    public boolean canReturn(Order o) {
        if (o == null) return false;
        if (!"DELIVERED".equalsIgnoreCase(o.getStatus())) return false;
        // Example: 30-day window from delivery/createdAt
        LocalDateTime placed = o.getCreatedAt();
        return placed != null && Duration.between(placed, LocalDateTime.now()).toDays() <= 30;
    }

    public void markCancelled(Long orderId) {
        Order o = findById(orderId);
        if (o == null) return;
        o.setStatus("CANCELLED");
        save(o);
    }

    public void markReturnRequested(Long orderId, Long productId) {
        Order o = findById(orderId);
        if (o == null) return;
        // You might store a ReturnRequest entity instead.
        o.setStatus("RETURN_REQUESTED");
        save(o);
    }

    public List<String> getTrackingEvents(Order o) {
        // Demo stub. Replace with real carrier API or stored events.
        return List.of(
                "Label created",
                "Picked up by carrier",
                "In transit",
                "Out for delivery"
        );
    }

    public byte[] generateInvoicePdf(Order o) {
        StringBuilder invoiceText = new StringBuilder();
        invoiceText.append("==========================================\n");
        invoiceText.append("                  INVOICE                 \n");
        invoiceText.append("==========================================\n");
        invoiceText.append(String.format("ORDER ID:         #%d\n", o.getId()));
        invoiceText.append(String.format("CONFIRMATION #:   %s\n", o.getConfirmationNumber()));
        invoiceText.append(String.format("DATE:             %s\n", o.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a"))));
        invoiceText.append(String.format("STATUS:           %s\n", o.getStatus()));
        invoiceText.append("==========================================\n\n");

        invoiceText.append(String.format("%-30s %-5s %s\n", "ITEM", "QTY", "PRICE"));
        invoiceText.append("------------------------------------------\n");
        o.getItems().forEach(item -> {
            invoiceText.append(String.format("%-30s %-5d $%.2f\n",
                    item.getProductName(),
                    item.getQuantity(),
                    item.getPrice().multiply(new BigDecimal(item.getQuantity()))));
        });
        invoiceText.append("------------------------------------------\n\n");

        invoiceText.append(String.format("%30s  $%.2f\n", "SUBTOTAL:", o.getSubtotal()));
        invoiceText.append(String.format("%30s  $%.2f\n", "TAX (8%):", o.getTax()));
        invoiceText.append("                                   -------\n");
        invoiceText.append(String.format("%30s  $%.2f\n", "TOTAL PAID:", o.getGrandTotal()));

        return invoiceText.toString().getBytes(StandardCharsets.UTF_8);
    }
}
