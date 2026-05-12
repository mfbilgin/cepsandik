package com.cepsandik.electionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuardianAssignmentEvent implements Serializable {
    private Long electionId;
    private String electionTitle;
    private String communityName;
    private List<UUID> selectedGuardianIds;
    private String category; // e.g. "GUARDIAN_DUTY"
}
