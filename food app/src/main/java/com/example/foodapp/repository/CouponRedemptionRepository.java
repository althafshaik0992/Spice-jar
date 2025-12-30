package com.example.foodapp.repository;

import com.example.foodapp.model.Coupon;
import com.example.foodapp.model.CouponRedemption;
import com.example.foodapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    boolean existsByUserAndCoupon(User user, Coupon coupon);

    List<CouponRedemption> findByUser(User user);

    @Query("select cr.coupon.code from CouponRedemption cr where cr.user = :user")
    List<String> findCodesUsedByUser(@Param("user") User user);
}
