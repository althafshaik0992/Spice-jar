package com.example.foodapp.controller;

import com.example.foodapp.model.Coupon;
import com.example.foodapp.repository.CouponRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/coupons")
public class AdminCouponController {

    private final CouponRepository repo;

    public AdminCouponController(CouponRepository repo) {
        this.repo = repo;
    }

    // LIST + filters (code + status)
    // handles /admin/coupons and /admin/coupons/
    @GetMapping({"", "/"})
    public String list(@RequestParam(required = false) String code,
                       @RequestParam(required = false) String status,
                       Model model) {

        List<Coupon> all = repo.findAll();

        // simple in-memory filtering
        List<Coupon> filtered = all.stream()
                .filter(c -> {
                    if (code == null || code.isBlank()) return true;
                    String cCode = c.getCode() != null ? c.getCode() : "";
                    return cCode.toLowerCase(Locale.ROOT)
                            .contains(code.toLowerCase(Locale.ROOT).trim());
                })
                .filter(c -> {
                    if (status == null || status.isBlank()) return true;
                    boolean active = Boolean.TRUE.equals(c.getActive());
                    if ("active".equalsIgnoreCase(status)) {
                        return active;
                    } else if ("inactive".equalsIgnoreCase(status)) {
                        return !active;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        model.addAttribute("coupons", filtered);
        model.addAttribute("code", code);       // keep filters in the UI
        model.addAttribute("status", status);

        return "admin/coupons/list";
    }

    // CREATE form
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("coupon", new Coupon());
        model.addAttribute("types", Coupon.Type.values());
        return "admin/coupons/form";
    }

    // EDIT form – matches link: /admin/coupons/{id}
    @GetMapping("/{id}")
    public String editFormRoot(@PathVariable Long id, Model model) {
        Coupon coupon = repo.findById(id).orElseThrow();
        model.addAttribute("coupon", coupon);
        model.addAttribute("types", Coupon.Type.values());
        return "admin/coupons/form";
    }

    // Optional: also support /admin/coupons/{id}/edit
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        return editFormRoot(id, model);
    }

    // SAVE (create + update)
    @PostMapping
    public String save(@ModelAttribute("coupon") Coupon coupon,
                       BindingResult br,
                       Model model) {
        if (br.hasErrors()) {
            model.addAttribute("types", Coupon.Type.values());
            return "admin/coupons/form";
        }
        coupon.setCode(coupon.getCode().trim().toUpperCase());
        repo.save(coupon);
        return "redirect:/admin/coupons";   // ✅ redirect to list
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        repo.deleteById(id);
        return "redirect:/admin/coupons";   // ✅ redirect to list
    }
}
