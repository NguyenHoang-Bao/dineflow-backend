package com.dineflow.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tiện ích khóa / đọc lại dòng dữ liệu (lockForUpdate() và fresh() của Laravel).
 * Dùng refresh để chắc chắn lấy dữ liệu MỚI NHẤT từ DB, kể cả khi entity đã được đọc trước đó
 * trong cùng request hoặc vừa bị câu UPDATE hàng loạt (vd expireStale) thay đổi.
 */
@Component
public class Db {
    @PersistenceContext
    private EntityManager em;

    /** SELECT ... FOR UPDATE và nạp lại dữ liệu mới nhất (khóa giữ tới hết transaction đang chạy). */
    @Transactional
    public <T> T lock(T entity) {
        em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return entity;
    }

    /** Đọc lại dữ liệu mới nhất từ DB. */
    @Transactional
    public <T> T refresh(T entity) {
        em.refresh(entity);
        return entity;
    }

}
