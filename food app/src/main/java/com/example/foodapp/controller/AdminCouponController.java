package com.example.foodapp.controller;

import com.example.foodapp.model.Coupon;
import com.example.foodapp.repository.CouponRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/coupons")
public class AdminCouponController {

    private final CouponRepository repo;

    public AdminCouponController(CouponRepository repo){ this.repo = repo; }

    // Handle both /admin/coupons and /admin/coupons/
    @GetMapping({"", "/"})
    public String list(Model model){
        model.addAttribute("coupons", repo.findAll());
        return "admin/coupons/list";
    }

    @GetMapping("/new")
    public String createForm(Model model){
        model.addAttribute("coupon", new Coupon());
        model.addAttribute("types", Coupon.Type.values());
        return "admin/coupons/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model){
        var coupon = repo.findById(id).orElseThrow();
        model.addAttribute("coupon", coupon);
        model.addAttribute("types", Coupon.Type.values());
        return "admin/coupons/form";
    }

    @PostMapping
    public String save(@ModelAttribute("coupon") Coupon coupon, BindingResult br, Model model){
        if (br.hasErrors()){
            model.addAttribute("types", Coupon.Type.values());
            return "admin/coupons/form";
        }
        coupon.setCode(coupon.getCode().trim().toUpperCase());
        repo.save(coupon);
        // ✅ redirect to the list route that actually exists
        return "admin/coupons/list";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id){
        repo.deleteById(id);
        return "admin/coupons/list";
    }
}
