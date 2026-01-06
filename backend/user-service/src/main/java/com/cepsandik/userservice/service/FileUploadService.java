package com.cepsandik.userservice.service;

import com.cepsandik.userservice.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * S3 file upload service for profile images
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket:cepsandik-uploads}")
    private String bucketName;

    @Value("${aws.s3.region:eu-north-1}")
    private String region;

    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    /**
     * Uploads profile image to S3 and returns the URL
     */
    public String uploadProfileImage(String userId, MultipartFile file) {
        // Validate file
        validateFile(file);

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String key = "profile-images/" + userId + "/" + UUID.randomUUID() + extension;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));

            // Return the S3 URL
            String url = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);

            log.info("Profile image uploaded: userId={}, url={}", userId, url);
            return url;

        } catch (IOException e) {
            log.error("Failed to upload profile image: userId={}, error={}", userId, e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Dosya yüklenemedi");
        }
    }

    /**
     * Deletes profile image from S3
     */
    public void deleteProfileImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        try {
            // Extract key from URL
            String key = extractKeyFromUrl(imageUrl);
            if (key == null) {
                return;
            }

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Profile image deleted: url={}", imageUrl);

        } catch (Exception e) {
            log.error("Failed to delete profile image: url={}, error={}", imageUrl, e.getMessage());
            // Don't throw - deletion failure shouldn't block the operation
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dosya seçilmedi");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dosya boyutu 5MB'ı aşamaz");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Sadece JPEG, PNG, GIF ve WebP formatları desteklenir");
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    private String extractKeyFromUrl(String url) {
        // URL format: https://bucket.s3.region.amazonaws.com/key
        String prefix = String.format("https://%s.s3.%s.amazonaws.com/", bucketName, region);
        if (url.startsWith(prefix)) {
            return url.substring(prefix.length());
        }
        return null;
    }
}
