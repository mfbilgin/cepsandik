package com.cepsandik.bulletinboard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppendRecordRequest {
    @NotBlank
    private String electionId;
    @NotBlank
    private String recordType;
    private String trackingCode;
    private String ballotHash;
    @NotBlank
    private String payload;
}
