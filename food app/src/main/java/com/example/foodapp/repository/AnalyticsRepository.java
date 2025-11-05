// src/main/java/com/example/foodapp/repository/AnalyticsRepository.java
package com.example.foodapp.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.foodapp.model.Order;

import java.math.BigDecimal;
import java.sql.Timestamp;

public interface AnalyticsRepository extends JpaRepository<Order, Long> {

    // Average order value (AOV) for paid-like orders since :since
    @Query("""
           select coalesce(avg(o.total), 0)
           from Order o
           where o.status in ('PAID','SHIPPED','DELIVERED')
             and o.createdAt >= :since
           """)
    BigDecimal avgOrderValueSince(@Param("since") java.time.LocalDateTime since);

    // Distinct customers in last window (based on email)
    @Query("""
           select coalesce(count(distinct o.email), 0)
           from Order o
           where o.status in ('PAID','SHIPPED','DELIVERED')
             and o.createdAt >= :since
           """)
    Long distinctCustomersSince(@Param("since") java.time.LocalDateTime since);

    // Repeat customers (>= 2 orders) in window – native is easiest
    @Query(value = """
            select coalesce(count(*),0) from (
              select email, count(*) c
              from orders
              where status in ('PAID','SHIPPED','DELIVERED')
                and created_at >= :since
              group by email
              having count(*) >= 2
            ) t
            """, nativeQuery = true)
    Long repeatCustomersSince(@Param("since") Timestamp since);

    // Refunded and total orders in last 30d – for refund rate
    @Query("""
           select coalesce(count(o), 0)
           from Order o
           where o.status = 'REFUNDED'
             and o.createdAt >= :since
           """)
    Long refundedOrdersSince(@Param("since") java.time.LocalDateTime since);

    @Query("""
           select coalesce(count(o), 0)
           from Order o
           where o.createdAt >= :since
             and o.status in ('PAID','SHIPPED','DELIVERED','REFUNDED')
           """)
    Long totalOrdersSince(@Param("since") java.time.LocalDateTime since);
}
