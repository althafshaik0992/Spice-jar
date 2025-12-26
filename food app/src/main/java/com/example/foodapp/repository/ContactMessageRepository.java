
package com.example.foodapp.repository;

import com.example.foodapp.model.ContactForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface ContactMessageRepository extends JpaRepository<ContactForm, Long> {

    ContactForm findByEmailIgnoreCase(String email);

    Boolean existsByEmail(String email);
    Optional<ContactForm> findByTicketId(String ticketId);



    List<ContactForm> findAllByOrderByCreatedAtDesc();
}
