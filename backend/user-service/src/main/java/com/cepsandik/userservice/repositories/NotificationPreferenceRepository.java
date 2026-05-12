package com.cepsandik.userservice.repositories;

import com.cepsandik.userservice.models.NotificationPreference;
import com.cepsandik.userservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    List<NotificationPreference> findByUser(User user);
    Optional<NotificationPreference> findByUserAndCategoryAndChannel(
            User user, 
            NotificationPreference.NotificationCategory category, 
            NotificationPreference.NotificationChannel channel
    );
}
