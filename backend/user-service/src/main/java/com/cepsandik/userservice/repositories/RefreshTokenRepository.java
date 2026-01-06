package com.cepsandik.userservice.repositories;

import com.cepsandik.userservice.models.RefreshToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {
    // @Indexed anotasyonu sayesinde bu metodlar çalışır:

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}