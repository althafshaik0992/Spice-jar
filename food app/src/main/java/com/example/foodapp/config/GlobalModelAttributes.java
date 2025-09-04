package com.example.foodapp.config;

import com.example.foodapp.service.CategoryService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice
@Component
public class GlobalModelAttributes {
    private final CategoryService categoryService;
    public GlobalModelAttributes(CategoryService categoryService) {
        this.categoryService = categoryService;
    }
    @ModelAttribute
    public void addCategories(Model model) {
        try {
            model.addAttribute("categories", categoryService.findAll());
        } catch (Exception e) {
            // ignore
        }
    }
}
