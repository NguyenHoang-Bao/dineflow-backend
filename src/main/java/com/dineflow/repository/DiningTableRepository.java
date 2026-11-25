package com.dineflow.repository;

import com.dineflow.entity.DiningTable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {
    List<DiningTable> findAllByOrderByIdAsc();

    List<DiningTable> findByAreaOrderByIdAsc(String area);

    List<DiningTable> findByCurrentOrderId(Long orderId);

    Optional<DiningTable> findByNumber(String number);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DiningTable t where t.id = :id")
    Optional<DiningTable> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DiningTable t where t.id in :ids")
    List<DiningTable> lockByIds(@Param("ids") Collection<Long> ids);

    /** Danh sách khu vực theo thứ tự bàn đầu tiên được tạo (Tầng 1, Tầng 2, Ngoài trời...). */
    @Query("select t.area from DiningTable t group by t.area order by min(t.id)")
    List<String> findAreas();
}
