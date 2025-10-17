// src/main/java/com/example/foodapp/service/impl/AnalyticsServiceImpl.java
package com.example.foodapp.service;


import com.example.foodapp.model.*;
import com.example.foodapp.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final AnalyticsRepository repo;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public DashboardMetrics getDashboardMetrics() {
        long totalOrders    = orderRepository.count();
        BigDecimal revenue  = orderRepository.totalRevenue();
        long totalMenu      = productRepository.count();
        long totalCategories= categoryRepository.count();

        return new DashboardMetrics(totalOrders,
                revenue == null ? BigDecimal.ZERO : revenue,
                totalMenu,
                totalCategories);
    }

    @Override
    public List<DayBucket> ordersByDay(int lastNDays) {
        LocalDate today = LocalDate.now();
        LocalDate fromDate = today.minusDays(lastNDays - 1); // include today
        LocalDateTime from = fromDate.atStartOfDay();

        // DB buckets we have
        Map<String, Long> db = new HashMap<>();
        for (Object[] row : orderRepository.dailyCountsFrom(from)) {
            String day = row[0].toString(); // "2025-10-01"
            long cnt = ((Number) row[1]).longValue();
            db.put(day, cnt);
        }

        // Fill empty days with zero for a perfect chart
        List<DayBucket> out = new ArrayList<>(lastNDays);
        for (int i = 0; i < lastNDays; i++) {
            LocalDate d = fromDate.plusDays(i);
            String key = d.toString();
            out.add(new DayBucket(key, db.getOrDefault(key, 0L)));
        }
        return out;
    }



    @Override
    public TopProducts topProducts(int limit) {
        List<Object[]> rows = orderRepository.topProducts(limit);
        List<String> names = new ArrayList<>();
        List<Long> qty = new ArrayList<>();
        for (Object[] r : rows) {
            names.add(String.valueOf(r[0]));
            qty.add(Long.valueOf(String.valueOf(r[1])));
        }
        return new TopProducts(names, qty);
    }

    @Override
    public List<RecentOrderDTO> recentOrders(int limit) {
        return orderRepository.findTop15ByOrderByCreatedAtDesc()
                .stream()
                .limit(limit)
                .map(this::toRecent)
                .collect(Collectors.toList());
    }

    private RecentOrderDTO toRecent(Order o) {
        return new RecentOrderDTO(
                o.getId(),
                Optional.ofNullable(o.getCustomerName()).orElse("—"),
                o.getGrandTotal(),
                Optional.ofNullable(o.getStatus()).orElse("—"),
                o.getCreatedAt()
        );
    }


    @Override
    public Map<String, Object> kpis() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last7  = now.minusDays(7);
        LocalDateTime last30 = now.minusDays(30);

        BigDecimal aov = repo.avgOrderValueSince(last7);
        if (aov == null) aov = BigDecimal.ZERO;

        long distinct = safeLong(repo.distinctCustomersSince(last30));
        long repeat   = safeLong(repo.repeatCustomersSince(Timestamp.valueOf(last30)));

        double repeatRate = distinct == 0 ? 0d :
                (repeat * 100.0) / distinct;

        long refunded = safeLong(repo.refundedOrdersSince(last30));
        long total30  = safeLong(repo.totalOrdersSince(last30));
        double refundRate = total30 == 0 ? 0d :
                (refunded * 100.0) / total30;

        // Round for display
        BigDecimal aovRounded = aov.setScale(2, RoundingMode.HALF_UP);
        double repeatPctRounded = round2(repeatRate);
        double refundPctRounded = round2(refundRate);

        return Map.of(
                "avgOrderValue7d", aovRounded,
                "repeatRate30d",  repeatPctRounded,
                "refundRate30d",  refundPctRounded
        );
    }

    private long safeLong(Long v) { return v == null ? 0L : v; }
    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    @Override
    public Series revenueSeries(LocalDate from, LocalDate to) {
        // half-open [from, to)
        LocalDateTime f = from.atStartOfDay();
        LocalDateTime t = to.plusDays(1).atStartOfDay();

        // fetch rows from DB
        List<Object[]> rows = orderRepository.revenueByDay(f, t);

        // map to a yyyy-MM-dd -> value map
        Map<LocalDate, BigDecimal> byDay = new HashMap<>();
        for (Object[] r : rows) {
            LocalDate d = (r[0] instanceof java.sql.Date sd)
                    ? sd.toLocalDate()
                    : LocalDate.parse(String.valueOf(r[0]));
            BigDecimal v = new BigDecimal(String.valueOf(r[1]));
            byDay.put(d, v);
        }

        // fill every day in range (missing days = 0)
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            labels.add(d.toString());
            values.add(byDay.getOrDefault(d, BigDecimal.ZERO));
        }

        return new Series(labels, values);
    }


}
