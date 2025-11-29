package com.example.foodapp.controller;

import com.example.foodapp.model.Category;
import com.example.foodapp.model.Order;
import com.example.foodapp.model.Product;
import com.example.foodapp.model.ProductVariant;
import com.example.foodapp.service.*;
import com.example.foodapp.repository.CategoryRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Objects;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final AnalyticsService analyticsService;
    private final CategoryService categoryService;
    private final OrderService orderService;
    private final EmailService emailService;




    public AdminController(ProductService productService, CategoryRepository categoryRepository, AnalyticsService analyticsService, CategoryService categoryService, OrderService orderService, EmailService emailService) {
        this.productService = productService;
        this.categoryRepository = categoryRepository;
        this.analyticsService = analyticsService;
        this.categoryService = categoryService;
        this.orderService = orderService;
        this.emailService = emailService;
    }


    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple DTO used only for mapping variantsJson
    public static class VariantDto {
        public Integer weight;
        public BigDecimal price;
        public Integer stock;
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

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String createProduct(@RequestParam String name,
                                @RequestParam(required = false) String description,
                                @RequestParam(required = false) BigDecimal price,
                                @RequestParam(required = false) Integer weight,
                                @RequestParam(required = false) Integer stock,
                                @RequestParam Long categoryId,
                                @RequestParam("imageFile") MultipartFile imageFile,
                                @RequestParam(name = "useVariants", defaultValue = "false") boolean useVariants,
                                @RequestParam(name = "variantsJson", required = false) String variantsJson,
                                RedirectAttributes ra) throws IOException {

        Category cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid category: " + categoryId));

        // --- image upload (as you had) ---
        String imageUrl = null;
        if (imageFile != null && !imageFile.isEmpty()) {
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
        p.setCategory(cat);
        p.setImageUrl(imageUrl);

        // ---------- VARIANTS ----------
        if (useVariants && variantsJson != null && !variantsJson.isBlank()) {

            List<VariantDto> dtos =
                    objectMapper.readValue(variantsJson, new TypeReference<List<VariantDto>>() {});

            if (dtos.isEmpty()) {
                throw new IllegalArgumentException("At least one variant is required when using variants.");
            }

            for (VariantDto dto : dtos) {
                ProductVariant v = new ProductVariant();
                v.setWeight(dto.weight);
                v.setPrice(dto.price);
                v.setStock(dto.stock != null ? dto.stock : 0);
                p.addVariant(v);            // sets product + adds to list
            }

            // derive base fields from variants so menu has a default
            ProductVariant first = p.getVariants().get(0);
            BigDecimal basePrice = first.getPrice();
            Integer baseWeight   = first.getWeight();
            Integer totalStock   = p.getVariants().stream()
                    .map(ProductVariant::getStock)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();

            p.setPrice(price != null ? price : basePrice);
            p.setWeight(weight != null ? weight : baseWeight);
            p.setStock(stock != null ? stock : totalStock);

        } else {
            // ---------- NO VARIANTS: behave like before ----------
            if (price == null || weight == null || stock == null) {
                throw new IllegalArgumentException("Price, weight and stock are required when not using variants.");
            }
            p.setPrice(price);
            p.setWeight(weight);
            p.setStock(stock);
        }

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

    @PostMapping(value = "/products/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String updateProduct(@RequestParam Long id,
                                @RequestParam String name,
                                @RequestParam(required = false) String description,
                                @RequestParam(required = false) BigDecimal price,
                                @RequestParam(required = false) Integer weight,
                                @RequestParam(required = false) Integer stock,
                                @RequestParam Long categoryId,
                                @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                @RequestParam(name = "useVariants", defaultValue = "false") boolean useVariants,
                                @RequestParam(name = "variantsJson", required = false) String variantsJson,
                                RedirectAttributes ra) throws IOException {

        Product existing = productService.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Invalid product ID: " + id);
        }

        Category cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid category: " + categoryId));

        existing.setName(name);
        existing.setDescription(description);
        existing.setCategory(cat);

        // image
        if (imageFile != null && !imageFile.isEmpty()) {
            Path uploadsDir = Paths.get("uploads");
            Files.createDirectories(uploadsDir);

            String fileName = System.currentTimeMillis() + "-" +
                    imageFile.getOriginalFilename().replaceAll("\\s+", "_");
            Path dest = uploadsDir.resolve(fileName);
            Files.copy(imageFile.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

            existing.setImageUrl("/uploads/" + fileName);
        }

        // ===== VARIANTS HANDLING =====
        if (useVariants && variantsJson != null && !variantsJson.isBlank()) {

            List<VariantDto> dtos =
                    objectMapper.readValue(variantsJson, new TypeReference<List<VariantDto>>() {});

            if (dtos.isEmpty()) {
                throw new IllegalArgumentException("At least one variant is required when using variants.");
            }

            List<ProductVariant> newVariants = new java.util.ArrayList<>();
            for (VariantDto dto : dtos) {
                ProductVariant v = new ProductVariant();
                v.setWeight(dto.weight);
                v.setPrice(dto.price);
                v.setStock(dto.stock != null ? dto.stock : 0);
                newVariants.add(v);
            }

            // This clears old variants + sets product on each new one
            existing.replaceVariants(newVariants);

            ProductVariant first = existing.getVariants().get(0);
            BigDecimal basePrice = first.getPrice();
            Integer baseWeight   = first.getWeight();
            Integer totalStock   = existing.getVariants().stream()
                    .map(ProductVariant::getStock)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();

            existing.setPrice(price != null ? price : basePrice);
            existing.setWeight(weight != null ? weight : baseWeight);
            existing.setStock(stock != null ? stock : totalStock);

        } else {
            // Not using variants → treat as simple product.
            // If you want to *keep* old variants when the checkbox is not sent,
            // comment out the next line.
            existing.getVariants().clear();

            if (price == null || weight == null || stock == null) {
                throw new IllegalArgumentException("Price, weight and stock are required when not using variants.");
            }
            existing.setPrice(price);
            existing.setWeight(weight);
            existing.setStock(stock);
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

    @PostMapping("/orders/{id}/send-survey")
    public String sendSurvey(@PathVariable Long id,
                             RedirectAttributes ra) {

        Order order = orderService.findById(id);
        if (order == null) {
            ra.addFlashAttribute("alertType", "error");
            ra.addFlashAttribute("alertMsg", "Order not found.");
            return "redirect:/admin/orders";
        }

        if (order.getEmail() == null || order.getEmail().isBlank()) {
            ra.addFlashAttribute("alertType", "error");
            ra.addFlashAttribute("alertMsg", "This order has no email address to send the survey.");
            return "redirect:/admin/orders";
        }

        try {
            // 🔔 call your email logic here
            emailService.sendOrderSurveyEmail(order);

            ra.addFlashAttribute("alertType", "success");
            ra.addFlashAttribute("alertMsg",
                    "Survey email sent to " + order.getEmail());
        } catch (Exception ex) {
            ra.addFlashAttribute("alertType", "error");
            ra.addFlashAttribute("alertMsg",
                    "Could not send survey email: " + ex.getMessage());
        }

        return "redirect:/admin/orders";
    }
}
