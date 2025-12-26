package com.example.foodapp.controller;

import com.example.foodapp.model.ContactForm;
import com.example.foodapp.repository.ContactMessageRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/support")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSupportController {

    private final ContactMessageRepository contactRepo;

    public AdminSupportController(ContactMessageRepository contactRepo) {
        this.contactRepo = contactRepo;
    }

    // ✅ list
    @GetMapping
    public String tickets(Model m) {
        m.addAttribute("tickets", contactRepo.findAllByOrderByCreatedAtDesc());
        return "admin/support_tickets";
    }

    // ✅ details
    @GetMapping("/{id}")
    public String ticketDetails(@PathVariable Long id, Model m) {
        ContactForm t = contactRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + id));

        m.addAttribute("ticket", t);
        return "admin/support_ticket_details";
    }

    // ✅ update status + admin notes
    @PostMapping("/{id}/update")
    public String updateTicket(@PathVariable Long id,
                               @RequestParam String status,
                               @RequestParam(required = false) String adminNotes,
                               RedirectAttributes ra) {

        ContactForm t = contactRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + id));

        t.setStatus(status);
        t.setAdminNotes(adminNotes);

        contactRepo.save(t);

        ra.addFlashAttribute("toast", "Ticket updated!");
        return "redirect:/admin/support/" + id;
    }
}
