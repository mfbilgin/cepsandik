package com.cepsandik.userservice.repositories;

import com.cepsandik.userservice.models.TwoFactorAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TwoFactorAuthRepository extends JpaRepository<TwoFactorAuth, Long> {

    Optional<TwoFactorAuth> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
