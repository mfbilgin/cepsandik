package com.cepsandik.communityservice.service;

import com.cepsandik.communityservice.exception.ApiException;
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
 * Topluluk logosu için S3 yükleme servisi (user-service FileUploadService kalıbı).
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
     * Topluluk logosunu S3'e yükler ve public URL döner.
     */
    public String uploadCommunityLogo(Long communityId, MultipartFile file) {
        validateFile(file);

        String extension = getFileExtension(file.getOriginalFilename());
        String key = "community-logos/" + communityId + "/" + UUID.randomUUID() + extension;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));

            String url = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
            log.info("Topluluk logosu yüklendi: communityId={}, url={}", communityId, url);
            return url;

        } catch (IOException e) {
            log.error("Topluluk logosu yüklenemedi: communityId={}, error={}", communityId, e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Dosya yüklenemedi");
        }
    }

    /**
     * S3'ten eski logoyu siler (best-effort; hata atmaz).
     */
    public void deleteLogo(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        try {
            String prefix = String.format("https://%s.s3.%s.amazonaws.com/", bucketName, region);
            if (!imageUrl.startsWith(prefix)) {
                return; // S3 dışı (örn. Unsplash) URL — dokunma
            }
            String key = imageUrl.substring(prefix.length());
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
            log.info("Topluluk logosu silindi: url={}", imageUrl);
        } catch (Exception e) {
            log.error("Topluluk logosu silinemedi: url={}, error={}", imageUrl, e.getMessage());
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
}
