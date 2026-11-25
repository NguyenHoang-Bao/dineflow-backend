package com.dineflow.repository;

import com.dineflow.entity.PersonalAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, Long> {
    Optional<PersonalAccessToken> findByToken(String token);

    @Modifying
    @Transactional
    @Query("delete from PersonalAccessToken t where t.tokenableId = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("delete from PersonalAccessToken t where t.tokenableId = :userId and t.id <> :keepId")
    void deleteOthersOfUser(@Param("userId") Long userId, @Param("keepId") Long keepId);
}
