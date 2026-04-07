package com.cepsandik.electionservice.dto.response;

import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.enums.ElectionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyElectionResponse {

    private Long electionId;
    private String electionTitle;
    private String electionDescription;
    private Long communityId;
    private ElectionStatus status;
    private ElectionType type;
    private Instant startTime;
    private Instant endTime;
    private Integer candidateCount;

    /** Kullanıcı oy kullanmış mı */
    private Boolean hasVoted;

    /** Oy kullanma zamanı (oy kullanmışsa) */
    private Instant votedAt;

    /** Kullanıcı bu seçimin sahibi mi */
    private Boolean isOwner;

    /** Katılım oranı (sadece sahip veya kapanmış seçimler için) */
    private Long totalVotes;
}
