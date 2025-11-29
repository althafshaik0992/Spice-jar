// src/main/java/com/spicejar/account/AddressService.java
package com.example.foodapp.service;

import com.example.foodapp.model.Address;
import com.example.foodapp.model.User;
import com.example.foodapp.repository.AddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
public class AddressService {
    private final AddressRepository repo;

    public AddressService(AddressRepository repo) { this.repo = repo; }

    public List<Address> listForUser(User user) {
        return repo.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(user.getId());
    }

    public Optional<Address> findById(Long id) {
        return repo.findById(id);
    }

    public Address get(Long id) { return repo.findById(id).orElse(null); }

    @Transactional
    public Address save(Address a, User user) {
        a.setUser(user);
        // if first address, make default
        if (listForUser(user).isEmpty()) a.setDefaultAddress(true);
        return repo.save(a);
    }

    @Transactional
    public void delete(Address a) { repo.delete(a); }

    @Transactional
    public void makeDefault(Address a, User user) {
        listForUser(user).forEach(x -> {
            x.setDefaultAddress(x.getId().equals(a.getId()));
            repo.save(x);
        });
    }

    /** Return all addresses belonging to the user, default first if available. */
    public List<Address> addressesForUser(Long userId) {
        return repo.findByUserIdOrderByDefaultAddressDescIdDesc(userId);
    }
}
