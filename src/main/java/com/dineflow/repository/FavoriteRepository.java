package com.dineflow.repository;

import com.dineflow.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByCustomerIdOrderByIdAsc(Long customerId);

    Optional<Favorite> findByCustomerIdAndFood_Id(Long customerId, Long foodId);

    void deleteByFood_Id(Long foodId);
}
