package com.example.foodapp.controller;

import com.example.foodapp.model.Category;
import com.example.foodapp.model.Product;
import com.example.foodapp.service.AnalyticsService;
import com.example.foodapp.service.CategoryService;
import com.example.foodapp.service.OrderService;
import com.example.foodapp.service.ProductService;
import com.example.foodapp.repository.CategoryRepository;

import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final AnalyticsService analyticsService;
    private final CategoryService categoryService;
    private final OrderService orderService;




    public AdminController(ProductService productService, CategoryRepository categoryRepository, AnalyticsService analyticsService, CategoryService categoryService, OrderService orderService) {
        this.productService = productService;
        this.categoryRepository = categoryRepository;
        this.analyticsService = analyticsService;
        this.categoryService = categoryService;
        this.orderService = orderService;
    }


    @GetMapping({"", "/"})
    public String dashboard(Model m) {
        // Get last 7 days revenue as an ordered map (label -> amount)
        LinkedHashMap<String, BigDecimal> rev = analyticsService.revenueByDay(7);

        // The dashboard.html expects revLabels and revValues
        m.addAttribute("revLabels", new java.util.ArrayList<>(rev.keySet()));
        m.addAttribute("revValues", new java.util.ArrayList<>(rev.values()));

        m.addAttribute("ordersCount", orderService.findAll().size());
        // ✅ Fetch last 5–10 orders for dashboard table
        m.addAttribute("recentOrders", orderService.recent(10));
        m.addAttribute("productsCount", productService.findAll().size());
        m.addAttribute("categoriesCount", categoryService.findAll().size());
        m.addAttribute("totalRevenue", orderService.calculateTotalRevenue());
        final int LOW_STOCK_THRESHOLD = 5; // tweak as you like or make it configurable
        m.addAttribute("lowStock", productService.findLowStock(LOW_STOCK_THRESHOLD));

        return "admin/dashboard";
    }


    // 🟢 Show catalog
    @GetMapping("/catalog")
    public String showCatalog(Model model,
                              @ModelAttribute("toast") String toast,
                              @ModelAttribute("error") String error) {
        model.addAttribute("products", productService.findAll());
        model.addAttribute("categories", categoryRepository.findAll());
        return "admin/catalog";
    }

    // 🟢 Add product (handles file upload + image URL)
    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String createProduct(@RequestParam String name,
                                @RequestParam(required = false) String description,
                                @RequestParam BigDecimal price,
                                @RequestParam Integer weight,
                                @RequestParam Integer stock,
                                @RequestParam Long categoryId,
                                @RequestParam("imageFile") MultipartFile imageFile,
                                RedirectAttributes ra) throws IOException {

        // ✅ Category lookup
        Category cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid category: " + categoryId));

        // ✅ Handle image upload
        String imageUrl = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            Path uploadsDir = Paths.get("uploads");
            Files.createDirectories(uploadsDir);

            String fileName = System.currentTimeMillis() + "-" +
                    imageFile.getOriginalFilename().replaceAll("\\s+", "_");
            Path dest = uploadsDir.resolve(fileName);
            Files.copy(imageFile.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

            imageUrl = "/uploads/" + fileName;  // ✅ Accessible via static mapping
        }

        // ✅ Build product
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(price);
        p.setWeight(weight);
        p.setStock(stock);
        p.setCategory(cat);
        p.setImageUrl(imageUrl);

        productService.save(p);

        ra.addFlashAttribute("toast", "Product added successfully!");
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

    // 🟡 Update product
    @PostMapping(value = "/products/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String updateProduct(@RequestParam Long id,
                                @RequestParam String name,
                                @RequestParam(required = false) String description,
                                @RequestParam BigDecimal price,
                                @RequestParam Integer weight,
                                @RequestParam Integer stock,
                                @RequestParam Long categoryId,
                                @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                RedirectAttributes ra) throws IOException {

        Product existing = productService.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Invalid product ID: " + id);
        }
        Category cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid category: " + categoryId));

        existing.setName(name);
        existing.setDescription(description);
        existing.setPrice(price);
        existing.setWeight(weight);
        existing.setStock(stock);
        existing.setCategory(cat);

        // Update image if new one provided
        if (imageFile != null && !imageFile.isEmpty()) {
            Path uploadsDir = Paths.get("uploads");
            Files.createDirectories(uploadsDir);

            String fileName = System.currentTimeMillis() + "-" +
                    imageFile.getOriginalFilename().replaceAll("\\s+", "_");
            Path dest = uploadsDir.resolve(fileName);
            Files.copy(imageFile.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

            existing.setImageUrl("/uploads/" + fileName);
        }

        productService.save(existing);
        ra.addFlashAttribute("toast", "Product updated successfully!");
        return "redirect:/admin/catalog";
    }

    // 🔴 Delete product
    @PostMapping("/products/delete")
    public String deleteProduct(@RequestParam Long id, RedirectAttributes ra) {
        productService.delete(id);
        ra.addFlashAttribute("toast", "Product deleted!");
        return "redirect:/admin/catalog";
    }

    // ⚠️ Handle missing params gracefully
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public String handleMissingParam(MissingServletRequestParameterException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", "Missing field: " + ex.getParameterName());
        return "redirect:/admin/catalog";
    }




    @GetMapping("/orders")
    public String orders(@RequestParam(required = false) String q,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(required = false) String sort,
                         Model m) {
        m.addAttribute("orders", orderService.findOrders(q, from, to, sort));
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
