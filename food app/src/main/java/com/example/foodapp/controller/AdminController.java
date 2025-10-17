package com.example.foodapp.controller;

import com.example.foodapp.model.*;
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
    public String dashboard(@RequestParam(name = "range", defaultValue = "7d") String range,Model m ,HttpSession session) {

        int days = switch (range) {
            case "30d" -> 30;
            case "90d" -> 90;
            default -> 7;
        };

        LocalDate to   = LocalDate.now();          // inclusive on chart
        LocalDate from = to.minusDays(days - 1);   // e.g. 7d → include 7 days

        // #### KPIs you already showed (make sure these are set) ####
        m.addAttribute("ordersCount",     orderService.countAll()); // or your own method
        m.addAttribute("totalRevenue",    orderService.totalRevenue()); // BigDecimal
       //m.addAttribute("menuCount",       productService.count());  // long
        //m.addAttribute("categoriesCount", categoryService.count()); // long
        m.addAttribute("productsCount", productService.findAll().size());

        // optional: fill avgOrderValue7d / aovChangePct / repeatRate30d / refundRate30d too

        // #### Charts ####
        var series = analyticsService.revenueSeries(from, to);
        m.addAttribute("revLabels", series.labels());
        m.addAttribute("revValues", series.values());

        var top = analyticsService.topProducts(5);
        m.addAttribute("topProductNames", top.names());
        m.addAttribute("topProductQty",   top.qty());

        // recent orders & low stock if you use them
        m.addAttribute("recentOrders", orderService.recent(10));
        //m.addAttribute("lowStock",     productService.lowStock(5));
       // var daily = analyticsService.dailyOrdersLast7Days();
        DashboardMetrics dashboardMetrics = analyticsService.getDashboardMetrics();
        m.addAttribute("metrics", dashboardMetrics);

        // Give some initial data so the page looks complete without JS
        m.addAttribute("ordersByDay", analyticsService.ordersByDay(14));
        m.addAttribute("topProducts", analyticsService.topProducts(5));
        m.addAttribute("recentOrders", analyticsService.recentOrders(10));
       // m.addAttribute("labels", daily.keySet());
        //m.addAttribute("values", daily.values());
        m.addAttribute("ordersCount", orderService.findAll().size());
        m.addAttribute("productsCount", productService.findAll().size());
        m.addAttribute("categoriesCount", categoryService.findAll().size());
        m.addAttribute("totalRevenue" ,orderService.calculateTotalRevenue());
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
    @GetMapping("/menu")
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

    @PostMapping("/menu/add")
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

    @PostMapping("/products/update")
    public String updateProduct(@RequestParam Long id,
                                @RequestParam String name,
                                @RequestParam(required = false) String description,
                                @RequestParam BigDecimal price,
                                @RequestParam Long categoryId) {

        // Find the existing product by ID
        Product product = productService.findById(id);

        // Check if the product exists before attempting to update
        if (product == null) {
            // You could redirect with an error message, but throwing an exception is clear for a developer
            throw new IllegalArgumentException("Invalid product ID: " + id);
        }

        // Find the category
        Category cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid category ID: " + categoryId));

        // Update fields
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setCategory(cat);
        // Note: Image is not updated via this form to keep the process simple.

        productService.save(product);
        return "redirect:/admin/catalog";
    }



    /* -------------------- ORDERS -------------------- */
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

    @GetMapping("/api/metrics")
    @ResponseBody
    public DashboardMetrics metrics() {
        return analyticsService.getDashboardMetrics();
    }

    @GetMapping("/api/orders-by-day")
    @ResponseBody
    public List<DayBucket> ordersByDay(@RequestParam(defaultValue = "14") int days) {
        return analyticsService.ordersByDay(Math.max(1, Math.min(days, 60)));
    }

    @GetMapping("/api/top-products")
    @ResponseBody
    public AnalyticsService.TopProducts topProducts(@RequestParam(defaultValue = "5") int limit) {
        return analyticsService.topProducts(Math.max(1, Math.min(limit, 20)));
    }

    @GetMapping("/api/recent-orders")
    @ResponseBody
    public List<RecentOrderDTO> recentOrders(@RequestParam(defaultValue = "10") int limit) {
        return analyticsService.recentOrders(Math.max(1, Math.min(limit, 50)));
    }
}


