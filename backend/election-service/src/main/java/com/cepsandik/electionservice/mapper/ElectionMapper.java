package com.cepsandik.electionservice.mapper;

import com.cepsandik.electionservice.dto.request.CreateElectionRequest;
import com.cepsandik.electionservice.dto.response.CandidateResponse;
import com.cepsandik.electionservice.dto.response.ElectionResponse;
import com.cepsandik.electionservice.entity.Candidate;
import com.cepsandik.electionservice.entity.Election;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ElectionMapper {

    public Election toEntity(CreateElectionRequest request, String userId) {
        return Election.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .communityId(request.getCommunityId())
                .createdBy(userId)
                .type(request.getType())
                .participantType(request.getParticipantType())
                .maxSelections(request.getMaxSelections())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .resultsPublic(request.getResultsPublic() != null ? request.getResultsPublic() : false)
                .anonymousVoting(request.getAnonymousVoting() != null ? request.getAnonymousVoting() : true)
                .build();
    }

    public ElectionResponse toResponse(Election election) {
        return ElectionResponse.builder()
                .id(election.getId())
                .title(election.getTitle())
                .description(election.getDescription())
                .communityId(election.getCommunityId())
                .createdBy(election.getCreatedBy())
                .status(election.getStatus())
                .type(election.getType())
                .participantType(election.getParticipantType())
                .maxSelections(election.getMaxSelections())
                .startTime(election.getStartTime())
                .endTime(election.getEndTime())
                .resultsPublic(election.getResultsPublic())
                .anonymousVoting(election.getAnonymousVoting())
                .createdAt(election.getCreatedAt())
                .updatedAt(election.getUpdatedAt())
                .candidateCount(election.getCandidates() != null ? 
                        (int) election.getCandidates().stream().filter(c -> !c.getIsDeleted()).count() : 0)
                .build();
    }

    public ElectionResponse toDetailedResponse(Election election) {
        ElectionResponse response = toResponse(election);
        if (election.getCandidates() != null) {
            response.setCandidates(
                    election.getCandidates().stream()
                            .filter(c -> !c.getIsDeleted())
                            .map(this::toCandidateResponse)
                            .collect(Collectors.toList())
            );
        }
        return response;
    }

    public CandidateResponse toCandidateResponse(Candidate candidate) {
        return CandidateResponse.builder()
                .id(candidate.getId())
                .name(candidate.getName())
                .description(candidate.getDescription())
                .imageUrl(candidate.getImageUrl())
                .displayOrder(candidate.getDisplayOrder())
                .createdAt(candidate.getCreatedAt())
                .build();
    }

    public List<ElectionResponse> toResponseList(List<Election> elections) {
        return elections.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
