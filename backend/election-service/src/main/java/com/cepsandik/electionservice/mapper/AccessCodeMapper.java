package com.cepsandik.electionservice.mapper;

import com.cepsandik.electionservice.dto.response.AccessCodeResponse;
import com.cepsandik.electionservice.entity.AccessCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AccessCodeMapper {

    public AccessCodeResponse toResponse(AccessCode accessCode) {
        return AccessCodeResponse.builder()
                .id(accessCode.getId())
                .code(accessCode.getCode())
                .maxUses(accessCode.getMaxUses())
                .currentUses(accessCode.getCurrentUses())
                .isActive(accessCode.getIsActive())
                .expiresAt(accessCode.getExpiresAt())
                .createdBy(accessCode.getCreatedBy())
                .createdAt(accessCode.getCreatedAt())
                .build();
    }

    public List<AccessCodeResponse> toResponseList(List<AccessCode> accessCodes) {
        return accessCodes.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
