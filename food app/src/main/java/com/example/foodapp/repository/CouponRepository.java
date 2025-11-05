package com.example.foodapp.repository;

import com.example.foodapp.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeIgnoreCase(String code);


    List<Coupon> findByActiveTrueOrderByStartsOnAscCodeAsc();
}
