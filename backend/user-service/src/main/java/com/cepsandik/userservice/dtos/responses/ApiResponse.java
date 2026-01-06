package com.cepsandik.userservice.dtos.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ApiResponse<T> {
    @Schema(description = "İşlemin başarılı olup olmadığını belirtir.", example = "true")
    private boolean success;

    @Schema(description = "İşlem sonucu veya hata mesajı.", example = "Kayıt başarılı.")
    private String message;

    @Schema(description = "İşlemden dönen veri (örneğin kullanıcı, token, vs.)")
    private T data;

    @Schema(description = "Yanıtın üretildiği zaman damgası.", example = "2025-11-12T12:50:30Z")
    private Instant timestamp;

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }
    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static ApiResponse<Void> ok(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static ApiResponse<Void> fail(String message) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
