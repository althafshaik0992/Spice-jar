package com.example.foodapp.repository;

import com.example.foodapp.util.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartRepository  extends JpaRepository<Cart, Long> {


    @Query("select c from Cart c where c.userId = :userId and c.status = 'OPEN'")
    Optional<Cart> findActiveCartByUserId(@Param("userId") Long userId);

    Optional<Cart> findByUserIdAndStatus(Long userId, Cart.Status status);

    // If you want all carts for user:
    List<Cart> findByUserId(Long userId);
}
