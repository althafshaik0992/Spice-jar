// src/main/java/com/spicejar/account/AddressRepository.java
package com.example.foodapp.repository;

import com.example.foodapp.model.Address;
import com.example.foodapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserIdOrderByDefaultAddressDescCreatedAtDesc(Long userId);
    List<Address> findByUser(User user);
}
