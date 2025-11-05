package com.example.foodapp.repository;

import com.example.foodapp.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeIgnoreCase(String code);


    List<Coupon> findByActiveTrueOrderByStartsOnAscCodeAsc();



    @Query("""
      select c from Coupon c
      where c.active = true
        and (c.startsOn  is null or c.startsOn  <= :today)
        and (c.expiresOn is null or c.expiresOn >= :today)
      order by c.startsOn asc, c.code asc
    """)
    List<Coupon> findActiveCurrentlyValid(@Param("today") LocalDate today);
}
