package com.cepsandik.userservice.repositories;

import com.cepsandik.userservice.models.EmailChangeToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailChangeTokenRepository extends JpaRepository<EmailChangeToken, Long> {

    Optional<EmailChangeToken> findByToken(String token);

    void deleteByUserId(UUID userId);
}
