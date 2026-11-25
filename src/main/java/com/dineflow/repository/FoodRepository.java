package com.dineflow.repository;

import com.dineflow.entity.Food;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface FoodRepository extends JpaRepository<Food, Long>, JpaSpecificationExecutor<Food> {
    long countByCategoryId(Long categoryId);

    Optional<Food> findByName(String name);
}
