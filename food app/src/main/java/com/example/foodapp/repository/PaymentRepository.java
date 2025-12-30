// src/main/java/com/example/foodapp/repository/PaymentRepository.java
package com.example.foodapp.repository;

import com.example.foodapp.model.Payment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // ✅ show refund history


    // ✅ THIS is critical: refund the latest SUCCEEDED non-refund payment
    Optional<Payment> findTopByOrder_IdAndRefundFalseAndStatusInOrderByIdDesc(
            Long orderId,
            Collection<String> statuses
    );

    // fallback (not recommended for refunds, ok for showing latest activity)
    Optional<Payment> findTopByOrder_IdOrderByIdDesc(Long orderId);


    Optional<Payment> findTopByOrder_IdAndRefundFalseOrderByIdDesc(Long orderId);

    Optional<Payment> findTopByOrder_IdAndRefundFalseAndStatusOrderByIdDesc(Long orderId, String status);

    List<Payment> findByOrder_IdAndRefundTrueOrderByIdDesc(Long orderId);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);





    @Query("""
  select p from Payment p
  where upper(p.status) = 'SUCCEEDED'
    and (p.refundExternalId is null or p.refundExternalId = '')
    and (p.refundReason is null or p.refundReason = '')
  order by p.createdAt desc
""")
    List<Payment> findRecentSuccessfulCharges(Pageable pageable);

    default List<Payment> findRecentSuccessfulCharges(int limit){
        return findRecentSuccessfulCharges(PageRequest.of(0, limit));
    }

    @Query("""
  select p from Payment p
  where p.refundExternalId is not null
  order by p.createdAt desc
""")
    List<Payment> findRecentRefunds(Pageable pageable);

    default List<Payment> findRecentRefunds(int limit){
        return findRecentRefunds(PageRequest.of(0, limit));
    }

}
