package com.example.foodapp.controller;

import com.example.foodapp.model.Category;
import com.example.foodapp.model.Order;
import com.example.foodapp.model.Product;
import com.example.foodapp.repository.CategoryRepository;
import com.example.foodapp.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AnalyticsService analyticsService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final OrderService orderService;
    private final CategoryRepository categoryRepository;

    public AdminController(AnalyticsService analyticsService,
                           CategoryService categoryService,
                           ProductService productService,
                           OrderService orderService,
                           CategoryRepository categoryRepository) {
        this.analyticsService = analyticsService;
        this.categoryService = categoryService;
        this.productService = productService;
        this.orderService = orderService;
        this.categoryRepository = categoryRepository;
    }

    /* -------------------- DASHBOARD -------------------- */

    @GetMapping({"", "/"})
    public String dashboard(Model m, HttpSession session) {
        var daily = analyticsService.dailyOrdersLast7Days();
        m.addAttribute("labels", daily.keySet());
        m.addAttribute("values", daily.values());
        m.addAttribute("ordersCount", orderService.findAll().size());
        m.addAttribute("productsCount", productService.findAll().size());
        m.addAttribute("categoriesCount", categoryService.findAll().size());
        return "admin/dashboard";
    }

    /* -------------------- CATALOG (Products + Categories) -------------------- */

    // Single page for managing both
    @GetMapping("/catalog")
    public String catalog(Model model) {
        model.addAttribute("products", productService.findAll());      // should eager-load category (findAllWithCategory)
        model.addAttribute("categories", categoryService.findAll());
        return "admin/catalog";                                        // your combined page
    }

    // Backward-compat: redirect old endpoints to catalog
    @GetMapping("/products")
    public String productsRedirect() {
        return "redirect:/admin/catalog";
    }

    @GetMapping("/categories")
    public String categoriesRedirect() {
        return "redirect:/admin/catalog";
    }

    /* -------------------- CATEGORY actions -------------------- */

    @PostMapping("/categories")
    public String addCategory(@RequestParam String name,
                              @RequestParam(required = false) String description) {
        categoryService.save(new Category(name, description));         // if you added description to Category
        return "redirect:/admin/catalog";
    }

    @PostMapping("/categories/delete")
    public String deleteCategory(@RequestParam Long id) {
        categoryService.delete(id);
        return "redirect:/admin/catalog";
    }

    /* -------------------- PRODUCT actions -------------------- */

    @PostMapping("/products")
    public String createProduct(@RequestParam String name,
                                @RequestParam(required = false) String description,
                                @RequestParam BigDecimal price,
                                @RequestParam Long categoryId,
                                @RequestParam("imageFile") MultipartFile imageFile) throws IOException {

        Category cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid category: " + categoryId));

        String imageUrl = null;
        if (!imageFile.isEmpty()) {
            Path uploadsDir = Paths.get("uploads");
            Files.createDirectories(uploadsDir);
            String fileName = System.currentTimeMillis() + "-" +
                    imageFile.getOriginalFilename().replaceAll("\\s+", "_");
            Path dest = uploadsDir.resolve(fileName);
            Files.copy(imageFile.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            imageUrl = "/uploads/" + fileName;
        }

        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(price);
        p.setImageUrl(imageUrl);
        p.setCategory(cat);

        productService.save(p);
        return "redirect:/admin/catalog";
    }


    @PostMapping("/products/delete")
    public String deleteProduct(@RequestParam Long id) {
        productService.delete(id);
        return "redirect:/admin/catalog";
    }

    /* -------------------- ORDERS -------------------- */

    @GetMapping("/orders")
    public String orders(@RequestParam(required = false) String q,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(required = false) String sort,
                         Model m) {

        // Use the new service method that handles both finding and filtering/sorting
        List<Order> filteredOrders = orderService.findOrders(q, from, to, sort);
        m.addAttribute("orders", filteredOrders);

        // Keep current params so your form can reflect them
        m.addAttribute("q", q);
        m.addAttribute("from", from);
        m.addAttribute("to", to);
        m.addAttribute("sort", sort);

        return "admin/orders";
    }

    /** Optional: reset endpoint, but you can also just link to /admin/orders with no params. */
    @PostMapping("/orders/reset")
    public String reset() {
        return "redirect:/admin/orders";
    }
}
