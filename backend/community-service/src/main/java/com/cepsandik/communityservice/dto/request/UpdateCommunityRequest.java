package com.cepsandik.communityservice.dto.request;

import com.cepsandik.communityservice.enums.CommunityVisibility;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCommunityRequest {

    @Size(min = 3, max = 100, message = "Topluluk adı 3 ile 100 karakter arasında olmalıdır")
    private String name;

    @Size(max = 500, message = "Açıklama 500 karakteri geçemez")
    private String description;

    private CommunityVisibility visibility;
}
