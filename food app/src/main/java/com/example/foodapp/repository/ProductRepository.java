package com.example.foodapp.repository;

import com.example.foodapp.model.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Finder by relation id (for filtering)
    List<Product> findByCategory_Id(Long categoryId);

    // Use this to load category eagerly for the admin table to avoid lazy issues
    @EntityGraph(attributePaths = "category")
    @Query("select p from Product p")
    List<Product> findAllWithCategory();


    List<Product> findTop5ByNameContainingIgnoreCase(String q);

    // If you have Category relation (Product.category.name)
    List<Product> findTop5ByCategory_NameContainingIgnoreCase(String q);

    List<Product> findAll(Specification<Product> spec, Sort s);

    @Query("""
      select p from Product p
      where (:q is null or lower(p.name) like lower(concat('%', :q, '%')))
        and (:min is null or p.price >= :min)
        and (:max is null or p.price <= :max)
        and (:categoryId is null or p.category.id = :categoryId)
    """)
    List<Product> filter(
            @Param("q") String q,
            @Param("min") BigDecimal min,
            @Param("max") BigDecimal max,
            @Param("categoryId") Long categoryId
    );

    @Query("""
           SELECT p FROM Product p
           WHERE LOWER(p.name)        LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(p.category.name) LIKE LOWER(CONCAT('%', :q, '%'))
           ORDER BY p.name ASC
           """)
    List<Product> searchByKeyword(@Param("q") String q, Pageable pageable);

    // For top or random fallback
    @Query(value = "SELECT p FROM Product p ORDER BY p.id DESC LIMIT :limit",
            nativeQuery = true)
    List<Product> findTopNProducts(@Param("limit") int limit);


    @Query("select distinct p from Product p " +
            "left join fetch p.category " +
            "left join fetch p.variants")
    List<Product> findAllWithCategoryAndVariants();


    @Query("""
       select distinct p
       from Product p
       left join p.variants v
       where (p.variants is empty and (p.stock is not null and p.stock <= :threshold))
          or (v.id is not null and v.stock is not null and v.stock <= :threshold)
       order by p.name asc
       """)
    List<Product> findLowStock(int threshold);
}

