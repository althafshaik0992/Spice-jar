package com.example.foodapp.service;

import com.example.foodapp.model.Admin;
import com.example.foodapp.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    @Autowired
    private AdminRepository adminRepository;

    public Admin authenticate(String username, String password) {
        return adminRepository
                .findByUsernameAndPassword(username, password)
                .orElse(null);
    }
}
