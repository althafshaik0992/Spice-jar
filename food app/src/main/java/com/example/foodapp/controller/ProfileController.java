// com.example.foodapp.controller.ProfileController
package com.example.foodapp.controller;

import com.example.foodapp.model.User;
import com.example.foodapp.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Controller
public class ProfileController {

    private final UserService userService;
    public ProfileController(UserService userService) { this.userService = userService; }

    // ---- helpers ----
    private User requireUser(HttpSession session) {
        Object sess = session.getAttribute("USER");
        if (sess == null) return null;
        if (sess instanceof User u) {
            return userService.findById(u.getId()).orElse(u);
        }
        try {
            var m = sess.getClass().getMethod("getId");
            Long id = ((Number)m.invoke(sess)).longValue();
            return userService.findById(id).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /* ---------- PAGES ---------- */

    @GetMapping("/profile")
    public String editProfile(HttpSession session, Model m,
                              @RequestParam(required = false) String msg,
                              @RequestParam(required = false) String error) {
        User u = requireUser(session);
        if (u == null) return "redirect:/login";
        m.addAttribute("user", u);
        if (msg != null) m.addAttribute("msg", msg);
        if (error != null) m.addAttribute("error", error);
        return "profile"; // edit form
    }

    @GetMapping("/profile/view")
    public String viewProfile(HttpSession session, Model m,
                              @RequestParam(required = false) String msg) {
        User u = requireUser(session);
        if (u == null) return "redirect:/login";
        m.addAttribute("user", u);
        if (msg != null) m.addAttribute("msg", msg);
        return "profile_view"; // read-only
    }

    /* ---------- ACTIONS ---------- */

    // Save basic fields
    @PostMapping("/profile")
    public String saveProfile(@ModelAttribute("user") User form, HttpSession session) {
        User u = requireUser(session);
        if (u == null) return "redirect:/login";

        u.setFirstName(form.getFirstName());
        u.setLastName(form.getLastName());
        u.setEmail(form.getEmail());
        u.setPhone(form.getPhone());
        u.setAddress(form.getAddress());
        userService.save(u);

        session.setAttribute("USER", u);
        return "redirect:/profile/view?msg=Profile+updated";
    }

    // Upload avatar
    @PostMapping("/profile/avatar")
    public String uploadAvatar(@RequestParam("avatar") MultipartFile file,
                               HttpSession session) {
        User u = requireUser(session);
        if (u == null) return "redirect:/login";
        if (file == null || file.isEmpty()) {
            return "redirect:/profile?error=Please+choose+an+image";
        }

        try {
            // Save under src/main/resources/static/uploads/avatars/<uuid>.<ext>
            Path root = Paths.get("src/main/resources/static/uploads/avatars");
            Files.createDirectories(root);

            String ext = "";
            String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
            int dot = original.lastIndexOf('.');
            if (dot > 0 && dot < original.length()-1) ext = original.substring(dot); // includes .

            String name = UUID.randomUUID() + ext.toLowerCase();
            Path dest = root.resolve(name);

            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

            // Public URL
            String publicUrl = "/uploads/avatars/" + name;

            // Update user
            u.setAvatarUrl(publicUrl);
            userService.save(u);
            session.setAttribute("USER", u);
            return "redirect:/profile?msg=Photo+updated";
        } catch (IOException e) {
            e.printStackTrace();
            return "redirect:/profile?error=Upload+failed";
        }
    }


    @DeleteMapping("profile/delete/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        // In a real application, you would also need to add authentication
        // and authorization checks here to ensure the requesting user is
        // authorized to delete this account (e.g., they are an admin or the user themselves).

        boolean deleted = userService.deleteUser(userId);
        if (deleted) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT); // 204 No Content for successful deletion.
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 404 Not Found if the user doesn't exist.
        }
    }
}
