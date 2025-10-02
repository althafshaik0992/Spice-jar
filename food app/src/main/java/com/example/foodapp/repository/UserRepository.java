package com.example.foodapp.repository;

import aj.org.objectweb.asm.commons.InstructionAdapter;
import com.example.foodapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    void deleteById(Long id);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
    Optional<User> findByUsernameIgnoreCase(String username);

}
