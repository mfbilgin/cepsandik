package com.cepsandik.userservice.controllers;

import com.cepsandik.userservice.exceptions.ApiException;
import com.cepsandik.userservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * E-posta doğrulama için HTML sayfası döndüren controller.
 * Bu controller @RestController değil @Controller kullanır çünkü
 * Thymeleaf template'lerini render etmesi gerekiyor.
 */
@Controller
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Verification Pages", description = "E-posta doğrulama sayfaları")
public class VerificationController {

    private final AuthService authService;

    @Operation(summary = "Kullanıcının e-posta adresini doğrular ve sonuç sayfası gösterir")
    @GetMapping("/verify/{token}")
    public String verifyEmail(@PathVariable String token, Model model) {
        try {
            authService.verifyEmail(token);
            log.info("E-posta başarıyla doğrulandı: token={}", token.substring(0, 8) + "...");
            return "verification-success";
        } catch (ApiException e) {
            log.warn("E-posta doğrulama başarısız: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            return "verification-error";
        } catch (Exception e) {
            log.error("E-posta doğrulama sırasında beklenmeyen hata", e);
            model.addAttribute("errorMessage", "Beklenmeyen bir hata oluştu. Lütfen daha sonra tekrar deneyin.");
            return "verification-error";
        }
    }
}
