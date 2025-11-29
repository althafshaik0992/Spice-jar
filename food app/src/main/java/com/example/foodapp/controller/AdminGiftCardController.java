// src/main/java/com/example/foodapp/controller/AdminGiftCardController.java
package com.example.foodapp.controller;

import com.example.foodapp.model.GiftCard;
import com.example.foodapp.repository.GiftCardRepository;
import com.example.foodapp.service.GiftCardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Controller
@RequestMapping("/admin/gift-cards")
public class AdminGiftCardController {

    private final GiftCardService service;
    private final GiftCardRepository repository;

    public AdminGiftCardController(GiftCardService service, GiftCardRepository repository) { this.service = service;
        this.repository = repository;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("cards", service.findAll());
        return "admin/gift-cards";
    }

    @PostMapping
    public String create(@RequestParam String code,
                         @RequestParam BigDecimal amount,
                         @RequestParam(required=false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expires,
                         RedirectAttributes ra) {
        try {
            OffsetDateTime exp = (expires == null) ? null : expires.atStartOfDay().atOffset(ZoneOffset.UTC);
            service.create(code.trim().toUpperCase(), amount, exp);
            ra.addFlashAttribute("flashOk", true);
            ra.addFlashAttribute("flashMsg", "Gift card created.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("flashOk", false);
            ra.addFlashAttribute("flashMsg", ex.getMessage());
        }
        return "redirect:/admin/gift-cards";
    }



    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.toggleActive(id);
            ra.addFlashAttribute("flashOk", true);
            ra.addFlashAttribute("flashMsg", "Gift card status updated.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("flashOk", false);
            ra.addFlashAttribute("flashMsg", ex.getMessage());
        }
        return "redirect:/admin/gift-cards";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        service.delete(id);
        ra.addFlashAttribute("flashOk", true);
        ra.addFlashAttribute("flashMsg", "Gift card deleted.");
        return "redirect:/admin/gift-cards";
    }

    public String generateSpiceJarCode() {
        String year = String.valueOf(java.time.Year.now().getValue());
        return "SJ-" + year + "-" + randomBlock(4) + "-" + randomBlock(4);
    }


    private String randomBlock(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        SecureRandom rnd = new SecureRandom();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }


}
