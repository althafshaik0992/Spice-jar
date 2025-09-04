package com.example.foodapp.service;

import com.example.foodapp.model.Category;
import com.example.foodapp.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service public class CategoryService { private final CategoryRepository repo; public CategoryService(CategoryRepository repo){this.repo=repo;} public List<Category> findAll(){return repo.findAll();} public Category save(Category c){return repo.save(c);} public void delete(Long id){repo.deleteById(id);} }