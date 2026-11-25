package com.dineflow.repository;

import com.dineflow.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    Optional<Promotion> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Promotion> findAllByOrderByValidFromDesc();

    /** Voucher đang trong thời gian hiệu lực. */
    @Query("select p from Promotion p where p.isActive = true and p.validFrom <= :now and p.validTo >= :now order by p.validTo")
    List<Promotion> findValid(@Param("now") Instant now);
}
