package com.example.foodapp.repository;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    List<Order> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);


    Optional<Order> findByIdAndUserId(Long id, Long userId);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    // src/main/java/com/example/foodapp/repository/OrderRepository.java

    Optional<Order> findTopByUserIdOrderByCreatedAtDesc(Long userId);


    Optional<Order> findById(Long id);
    void deleteAllByUserId(Long userId);
    boolean existsByConfirmationNumber(String confirmationNumber);
    // Revenue only for paid (or shipped/delivered) orders.
    @Query("select coalesce(sum(o.total), 0) from Order o where o.status in ('PAID','SHIPPED','DELIVERED')")
    BigDecimal totalRevenue();

    List<Order> findTop15ByOrderByCreatedAtDesc();

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Daily buckets (last N days) — MySQL/MariaDB version
    @Query(value = """
            select date(o.created_at) as d, count(*) as c
            from orders o
            where o.created_at >= :from
            group by date(o.created_at)
            order by d asc
            """, nativeQuery = true)
    List<Object[]> dailyCountsFrom(@Param("from") LocalDateTime from);

    @Query(value = """
        select oi.product_id, p.name, sum(oi.quantity) as qty
        from order_item oi
        join orders o on o.id = oi.order_id
        join product p on p.id = oi.product_id
        where o.status in ('PAID','SHIPPED','DELIVERED')
        group by oi.product_id, p.name
        order by qty desc
        limit :limit
        """, nativeQuery = true)
    List<Object[]> topProducts(@Param("limit") int limit);
    @Query(value = """
        SELECT DATE(o.created_at)        AS d,
               SUM(o.grand_total)        AS v
        FROM orders o
        WHERE o.status IN ('PAID','SHIPPED','DELIVERED')
          AND o.created_at >= :from AND o.created_at < :to
        GROUP BY DATE(o.created_at)
        ORDER BY d
        """, nativeQuery = true)
    List<Object[]> revenueByDay(@Param("from") LocalDateTime from,
                                @Param("to")   LocalDateTime to);

    @Query("select coalesce(sum(o.total), 0) " +
            "from Order o " +
            "where o.createdAt >= :start and o.createdAt < :end")
    Optional<BigDecimal> sumGrandTotalBetween(
            @Param("start") java.time.LocalDateTime start,
            @Param("end")   java.time.LocalDateTime end);





//    @Query("""
//        select oi.productName as product, sum(oi.quantity) as qty
//        from OrderItem oi
//        join oi.order o
//        where o.status in :statuses
//          and (:since is null or o.createdAt >= :since)
//        group by oi.productName
//        order by qty desc
//        """)
//    List<ProductSales> findTopSellingProducts(
//            @Param("statuses") Collection<OrderStatus> statuses,
//            @Param("since") Instant since,
//            Pageable pageable);


    // Native fallback (only if JPQL gives you trouble with entity name)
    @Query(value = """
    SELECT new com.example.foodapp.repository.ProductSales(oi.productName, SUM(oi.quantity))
    FROM Order o
      JOIN o.items oi
    WHERE o.status IN :statusNames
      AND (:since IS NULL OR o.createdAt >= :since)
    GROUP BY oi.productName
    ORDER BY SUM(oi.quantity) DESC
""")
    List<ProductSales> findTopSellingProductsNative(
            @Param("statusNames") Set<String> statusNames,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );


}