package com.example.foodapp.controller;
import com.example.foodapp.model.Admin;
import com.example.foodapp.service.AdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminLoginController {



    @Autowired
    private AdminService adminService;

    @GetMapping("/admin/login")
    public String adminLogin() {
        return "admin/login";
    }


    @PostMapping("/admin/login")
    public String doAdminLogin(@RequestParam String username,
                               @RequestParam String password,
                               HttpSession session,
                               RedirectAttributes ra) {

        Admin admin = adminService.authenticate(username, password);

        if (admin == null) {
            ra.addFlashAttribute("error", "Invalid credentials");
            return "redirect:/admin/login";
        }

        // Clear any existing USER session
        session.removeAttribute("USER");

        // Store admin only
        session.setAttribute("ADMIN_USER", admin);

        return "redirect:/admin/dashboard";
    }


    @GetMapping("/admin/logout")
    public String adminLogout(HttpSession session) {
        session.removeAttribute("ADMIN_USER");  // ✅ only logs out admin
        return "redirect:/admin/login";
    }



}



