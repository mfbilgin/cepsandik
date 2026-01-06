package com.cepsandik.communityservice.dto.request;

import com.cepsandik.communityservice.enums.MemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMemberRoleRequest {

    @NotNull(message = "Rol alanı zorunludur")
    private MemberRole role;
}
