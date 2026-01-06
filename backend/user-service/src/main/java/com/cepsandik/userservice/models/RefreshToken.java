package com.cepsandik.userservice.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;


@RedisHash(value = "refresh_tokens", timeToLive = 60 * 60 * 24 * 30)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken implements Serializable {

    @Id
    private String id;

    @Indexed
    private String token;

    @Indexed
    private UUID userId;

}