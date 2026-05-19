package com.cepsandik.userservice.service;

import com.cepsandik.userservice.exceptions.ApiException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PasswordPolicyService {

    private static final int MIN_LENGTH = 12;
    private static final double MIN_EFFECTIVE_ENTROPY_BITS = 50.0;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final List<String> COMMON_WORDS = List.of(
            "password", "parola", "sifre", "qwerty", "admin", "welcome",
            "letmein", "monkey", "dragon", "master", "shadow", "superman", "batman",
            "cepsandik", "cep sandik", "sandik"
    );

    private PasswordPolicyService() {
    }

    public static void validateAcceptable(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            reject("Parola en az 12 karakter olmalidir.");
        }

        if (password.length() > 128) {
            reject("Parola 128 karakterden uzun olamaz.");
        }

        if (characterSetCount(password) < 3) {
            reject("Parola en az uc farkli karakter grubu icermelidir.");
        }

        String normalized = normalize(password);
        if (hasCommonWord(normalized) || hasSequentialPattern(normalized) || hasRepeatedPattern(normalized)) {
            reject("Parola yaygin kelime, sirali karakter veya tekrar eden desen iceremez.");
        }

        if (estimateEffectiveEntropy(password) < MIN_EFFECTIVE_ENTROPY_BITS) {
            reject("Parola tahmin edilmesi zor olacak kadar guclu degil.");
        }

        if (isPwned(password)) {
            reject("Bu parola veri ihlallerinde gorulmus. Farkli ve benzersiz bir parola secin.");
        }
    }

    private static int characterSetCount(String password) {
        int count = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) count++;
        if (password.chars().anyMatch(Character::isUpperCase)) count++;
        if (password.chars().anyMatch(Character::isDigit)) count++;
        if (password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) count++;
        return count;
    }

    private static boolean hasCommonWord(String normalized) {
        return COMMON_WORDS.stream().anyMatch(normalized::contains);
    }

    private static boolean hasSequentialPattern(String normalized) {
        List<String> sequences = List.of(
                "0123456789", "9876543210",
                "abcdefghijklmnopqrstuvwxyz", "zyxwvutsrqponmlkjihgfedcba",
                "qwertyuiop", "poiuytrewq", "asdfghjkl", "lkjhgfdsa", "zxcvbnm", "mnbvcxz"
        );

        for (String sequence : sequences) {
            for (int i = 0; i <= sequence.length() - 4; i++) {
                if (normalized.contains(sequence.substring(i, i + 4))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasRepeatedPattern(String normalized) {
        if (normalized.matches(".*(.)\\1{2,}.*")) {
            return true;
        }
        for (int size = 2; size <= 4; size++) {
            for (int i = 0; i <= normalized.length() - (size * 3); i++) {
                String chunk = normalized.substring(i, i + size);
                String repeated = chunk.repeat(3);
                if (normalized.substring(i).startsWith(repeated)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double estimateEffectiveEntropy(String password) {
        int poolSize = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) poolSize += 26;
        if (password.chars().anyMatch(Character::isUpperCase)) poolSize += 26;
        if (password.chars().anyMatch(Character::isDigit)) poolSize += 10;
        if (password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) poolSize += 32;

        double poolEntropy = password.length() * (Math.log(Math.max(poolSize, 1)) / Math.log(2));
        double shannonEntropy = shannonEntropy(password);
        double uniquenessRatio = (double) password.chars().distinct().count() / password.length();
        double penalty = uniquenessRatio < 0.55 ? 14.0 : uniquenessRatio < 0.7 ? 7.0 : 0.0;

        return ((poolEntropy * 0.55) + (shannonEntropy * 0.45)) - penalty;
    }

    private static double shannonEntropy(String password) {
        double entropy = 0.0;
        Set<Integer> chars = password.chars().boxed().collect(java.util.stream.Collectors.toSet());
        for (Integer ch : chars) {
            long count = password.chars().filter(c -> c == ch).count();
            double probability = (double) count / password.length();
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        return entropy * password.length();
    }

    private static boolean isPwned(String password) {
        try {
            String sha1 = sha1(password);
            String prefix = sha1.substring(0, 5);
            String suffix = sha1.substring(5);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.pwnedpasswords.com/range/" + prefix))
                    .timeout(Duration.ofSeconds(5))
                    .header("Add-Padding", "true")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                reject("Parola sizinti kontrolu yapilamadi. Lutfen daha sonra tekrar deneyin.");
            }

            return response.body().lines()
                    .map(line -> line.split(":", 2)[0])
                    .anyMatch(hashSuffix -> hashSuffix.equalsIgnoreCase(suffix));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reject("Parola sizinti kontrolu yapilamadi. Lutfen daha sonra tekrar deneyin.");
            return true;
        } catch (IOException e) {
            reject("Parola sizinti kontrolu yapilamadi. Lutfen daha sonra tekrar deneyin.");
            return true;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            reject("Parola sizinti kontrolu yapilamadi. Lutfen daha sonra tekrar deneyin.");
            return true;
        }
    }

    private static String sha1(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02X", b));
        }
        return hex.toString();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("@", "a")
                .replace("4", "a")
                .replace("3", "e")
                .replace("1", "i")
                .replace("!", "i")
                .replace("0", "o")
                .replace("$", "s")
                .replace("5", "s")
                .replace("7", "t");
    }

    private static void reject(String message) {
        throw new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
