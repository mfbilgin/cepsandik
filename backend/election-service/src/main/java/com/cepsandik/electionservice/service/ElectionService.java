package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.CryptoEngineClient;
import com.cepsandik.electionservice.config.AccessCodeConfig;
import com.cepsandik.electionservice.dto.request.CreateAccessCodeRequest;
import com.cepsandik.electionservice.dto.request.CreateCandidateRequest;
import com.cepsandik.electionservice.dto.request.CreateElectionRequest;
import com.cepsandik.electionservice.dto.request.UpdateElectionRequest;
import com.cepsandik.electionservice.dto.response.*;
import com.cepsandik.electionservice.entity.AccessCode;
import com.cepsandik.electionservice.entity.Candidate;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.entity.Vote;
import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.exception.ApiException;
import com.cepsandik.electionservice.grpc.*;
import com.cepsandik.electionservice.mapper.AccessCodeMapper;
import com.cepsandik.electionservice.mapper.ElectionMapper;
import com.cepsandik.electionservice.repository.AccessCodeRepository;
import com.cepsandik.electionservice.repository.CandidateRepository;
import com.cepsandik.electionservice.repository.ElectionRepository;
import com.cepsandik.electionservice.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElectionService {

    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final AccessCodeRepository accessCodeRepository;
    private final VoteRepository voteRepository;
    private final ElectionMapper electionMapper;
    private final AccessCodeMapper accessCodeMapper;
    private final AccessCodeConfig accessCodeConfig;
    private final CryptoEngineClient cryptoEngineClient;

    @Value("${app.guardian.count:3}")
    private int guardianCount;

    @Value("${app.guardian.quorum:2}")
    private int guardianQuorum;

    // ==================== ELECTION CRUD ====================

    @Transactional
    public ElectionResponse createElection(CreateElectionRequest request, String userId) {
        // Bitiş zamanı başlangıçtan sonra olmalı
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw ApiException.badRequest("Bitiş zamanı başlangıç zamanından sonra olmalıdır");
        }

        Election election = electionMapper.toEntity(request, userId);
        Election saved = electionRepository.save(election);

        log.info("Seçim oluşturuldu: id={}, title={}, createdBy={}",
                saved.getId(), saved.getTitle(), userId);

        return electionMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ElectionResponse getElectionById(Long id) {
        Election election = findElectionOrThrow(id);
        return electionMapper.toDetailedResponse(election);
    }

    @Transactional(readOnly = true)
    public PageResponse<ElectionResponse> getElectionsByCommunity(Long communityId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Election> electionPage = electionRepository.findByCommunityIdAndIsDeletedActive(communityId,
                LocalDateTime.now(), pageable);

        return buildPageResponse(electionPage);
    }

    @Transactional
    public ElectionResponse updateElection(Long id, UpdateElectionRequest request, String userId) {
        Election election = findElectionOrThrow(id);

        // Yetki kontrolü
        checkOwnership(election, userId);

        // Sadece DRAFT durumunda güncellenebilir
        if (!election.isEditable()) {
            throw ApiException.badRequest("Sadece taslak durumundaki seçimler düzenlenebilir");
        }

        // Güncelleme
        if (request.getTitle() != null) {
            election.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            election.setDescription(request.getDescription());
        }
        if (request.getType() != null) {
            election.setType(request.getType());
        }
        if (request.getMaxSelections() != null) {
            election.setMaxSelections(request.getMaxSelections());
        }
        if (request.getStartTime() != null) {
            election.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            election.setEndTime(request.getEndTime());
        }
        if (request.getResultsPublic() != null) {
            election.setResultsPublic(request.getResultsPublic());
        }
        if (request.getAnonymousVoting() != null) {
            election.setAnonymousVoting(request.getAnonymousVoting());
        }

        // Tarih kontrolü
        if (election.getEndTime().isBefore(election.getStartTime())) {
            throw ApiException.badRequest("Bitiş zamanı başlangıç zamanından sonra olmalıdır");
        }

        Election saved = electionRepository.save(election);
        log.info("Seçim güncellendi: id={}", id);

        return electionMapper.toDetailedResponse(saved);
    }

    @Transactional
    public void deleteElection(Long id, String userId) {
        Election election = findElectionOrThrow(id);
        checkOwnership(election, userId);

        // Aktif seçim silinemez
        if (election.isActive()) {
            throw ApiException.badRequest("Aktif seçimler silinemez");
        }

        election.setIsDeleted(true);
        electionRepository.save(election);

        log.info("Seçim silindi: id={}", id);
    }

    // ==================== STATUS MANAGEMENT ====================

    @Transactional
    public ElectionResponse publishElection(Long id, String userId) {
        Election election = findElectionOrThrow(id);
        checkOwnership(election, userId);

        if (!election.isDraft()) {
            throw ApiException.badRequest("Sadece taslak seçimler yayınlanabilir");
        }

        // En az 2 aday olmalı
        long candidateCount = candidateRepository.countByElectionIdAndIsDeletedFalse(id);
        if (candidateCount < 2) {
            throw ApiException.badRequest("Seçim yayınlamak için en az 2 aday gereklidir");
        }

        election.setStatus(ElectionStatus.SCHEDULED);
        Election saved = electionRepository.save(election);

        log.info("Seçim yayınlandı: id={}, status=SCHEDULED", id);

        return electionMapper.toDetailedResponse(saved);
    }

    @Transactional
    public ElectionResponse startElection(Long id, String userId) {
        Election election = findElectionOrThrow(id);
        checkOwnership(election, userId);

        if (!election.isScheduled()) {
            throw ApiException.badRequest("Sadece planlanmış seçimler başlatılabilir");
        }

        // === Crypto-Engine: ElectionGuard Key Ceremony ===
        try {
            List<Candidate> candidates = candidateRepository
                    .findByElectionIdAndIsDeletedFalseOrderByDisplayOrderAsc(id);

            // Contest bilgisini hazırla (tek contest = bu seçim)
            ContestInfo contestInfo = ContestInfo.newBuilder()
                    .setContestId("contest_" + id)
                    .addAllSelectionIds(
                            candidates.stream()
                                    .map(c -> "candidate_" + c.getId())
                                    .toList()
                    )
                    .setNumberElected(election.getMaxSelections() != null ? election.getMaxSelections() : 1)
                    .setName(election.getTitle())
                    .build();

            SetupElectionResponse cryptoResponse = cryptoEngineClient.setupElection(
                    String.valueOf(id),
                    guardianCount,
                    guardianQuorum,
                    List.of(contestInfo)
            );

            // Context, manifest ve guardian record'ları PostgreSQL'e kaydet
            election.setElectionGuardContext(cryptoResponse.getElectionGuardContext());
            election.setElectionManifest(cryptoResponse.getElectionManifest());
            election.setElectionPublicKey(cryptoResponse.getJointPublicKey());

            // Guardian record'ları JSON array olarak sakla
            StringBuilder guardianJson = new StringBuilder("[");
            for (int i = 0; i < cryptoResponse.getGuardianRecordsCount(); i++) {
                GuardianRecord gr = cryptoResponse.getGuardianRecords(i);
                if (i > 0) guardianJson.append(",");
                guardianJson.append("{\"guardian_id\":\"").append(gr.getGuardianId())
                        .append("\",\"serialized_guardian\":")
                        .append(gr.getSerializedGuardian())
                        .append("}");
            }
            guardianJson.append("]");
            election.setGuardianRecords(guardianJson.toString());

            log.info("ElectionGuard key ceremony tamamlandı: electionId={}", id);

        } catch (Exception e) {
            log.error("Crypto-Engine SetupElection hatası: electionId={}", id, e);
            throw ApiException.badRequest("Kriptografik altyapı kurulamadı: " + e.getMessage());
        }

        election.setStatus(ElectionStatus.ACTIVE);
        election.setStartTime(LocalDateTime.now());
        Election saved = electionRepository.save(election);

        log.info("Seçim başlatıldı: id={}, status=ACTIVE", id);

        return electionMapper.toDetailedResponse(saved);
    }

    @Transactional
    public ElectionResponse endElection(Long id, String userId) {
        Election election = findElectionOrThrow(id);
        checkOwnership(election, userId);

        if (!election.isActive()) {
            throw ApiException.badRequest("Sadece aktif seçimler sonlandırılabilir");
        }

        // === Crypto-Engine: Threshold Decryption (Tally) ===
        try {
            if (election.getElectionGuardContext() != null) {
                // Tüm şifreli oyları topla
                List<Vote> votes = voteRepository.findByElectionId(id);
                List<String> ciphertextBallots = votes.stream()
                        .filter(v -> v.getEncryptedBallot() != null)
                        .map(Vote::getEncryptedBallot)
                        .toList();

                if (!ciphertextBallots.isEmpty()) {
                    // Guardian record'ları parse et
                    List<GuardianRecord> guardianRecordsList = parseGuardianRecords(
                            election.getGuardianRecords()
                    );

                    TallyElectionResponse tallyResponse = cryptoEngineClient.tallyElection(
                            String.valueOf(id),
                            election.getElectionGuardContext(),
                            election.getElectionManifest(),
                            ciphertextBallots,
                            guardianRecordsList,
                            guardianQuorum
                    );

                    election.setTallyProof(tallyResponse.getTallyProof());

                    log.info("Tally çözümlemesi tamamlandı: electionId={}, contest_count={}",
                            id, tallyResponse.getResultsCount());
                } else {
                    log.info("Seçim sonlandırıldı (şifreli oy yok): id={}", id);
                }
            }
        } catch (Exception e) {
            log.error("Crypto-Engine TallyElection hatası: electionId={}", id, e);
            // Tally hatası seçimi sonlandırmayı engellemez
            log.warn("Tally hatası nedeniyle sonuçlar kriptografik çözümlemesiz kaydedilecek.");
        }

        election.setStatus(ElectionStatus.CLOSED);
        election.setEndTime(LocalDateTime.now());
        Election saved = electionRepository.save(election);

        log.info("Seçim sonlandırıldı: id={}, status=CLOSED", id);

        return electionMapper.toDetailedResponse(saved);
    }

    @Transactional
    public ElectionResponse cancelElection(Long id, String userId) {
        Election election = findElectionOrThrow(id);
        checkOwnership(election, userId);

        if (election.isClosed() || election.getStatus() == ElectionStatus.ARCHIVED) {
            throw ApiException.badRequest("Kapanmış veya arşivlenmiş seçimler iptal edilemez");
        }

        election.setStatus(ElectionStatus.CANCELLED);
        Election saved = electionRepository.save(election);

        log.info("Seçim iptal edildi: id={}, status=CANCELLED", id);

        return electionMapper.toDetailedResponse(saved);
    }

    // ==================== CANDIDATE MANAGEMENT ====================

    @Transactional
    public CandidateResponse addCandidate(Long electionId, CreateCandidateRequest request, String userId) {
        Election election = findElectionOrThrow(electionId);
        checkOwnership(election, userId);

        if (!election.isEditable()) {
            throw ApiException.badRequest("Sadece taslak seçimlere aday eklenebilir");
        }

        // Aynı isimde aday var mı kontrol et
        if (candidateRepository.existsByElectionIdAndNameAndIsDeletedFalse(electionId, request.getName())) {
            throw ApiException.conflict("Bu isimde bir aday zaten mevcut");
        }

        Candidate candidate = Candidate.builder()
                .election(election)
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        Candidate saved = candidateRepository.save(candidate);

        log.info("Aday eklendi: electionId={}, candidateId={}, name={}",
                electionId, saved.getId(), saved.getName());

        return electionMapper.toCandidateResponse(saved);
    }

    @Transactional
    public CandidateResponse updateCandidate(Long electionId, Long candidateId,
            CreateCandidateRequest request, String userId) {
        Election election = findElectionOrThrow(electionId);
        checkOwnership(election, userId);

        if (!election.isEditable()) {
            throw ApiException.badRequest("Sadece taslak seçimlerde aday düzenlenebilir");
        }

        Candidate candidate = candidateRepository.findByIdAndElectionIdAndIsDeletedFalse(candidateId, electionId)
                .orElseThrow(() -> ApiException.notFound("Aday bulunamadı"));

        candidate.setName(request.getName());
        candidate.setDescription(request.getDescription());
        candidate.setImageUrl(request.getImageUrl());
        if (request.getDisplayOrder() != null) {
            candidate.setDisplayOrder(request.getDisplayOrder());
        }

        Candidate saved = candidateRepository.save(candidate);

        log.info("Aday güncellendi: electionId={}, candidateId={}", electionId, candidateId);

        return electionMapper.toCandidateResponse(saved);
    }

    @Transactional
    public void removeCandidate(Long electionId, Long candidateId, String userId) {
        Election election = findElectionOrThrow(electionId);
        checkOwnership(election, userId);

        if (!election.isEditable()) {
            throw ApiException.badRequest("Sadece taslak seçimlerden aday silinebilir");
        }

        Candidate candidate = candidateRepository.findByIdAndElectionIdAndIsDeletedFalse(candidateId, electionId)
                .orElseThrow(() -> ApiException.notFound("Aday bulunamadı"));

        candidate.setIsDeleted(true);
        candidateRepository.save(candidate);

        log.info("Aday silindi: electionId={}, candidateId={}", electionId, candidateId);
    }

    public List<CandidateResponse> getCandidates(Long electionId) {
        findElectionOrThrow(electionId);
        List<Candidate> candidates = candidateRepository
                .findByElectionIdAndIsDeletedFalseOrderByDisplayOrderAsc(electionId);
        return candidates.stream()
                .map(electionMapper::toCandidateResponse)
                .toList();
    }

    // ==================== ACCESS CODE MANAGEMENT ====================

    @Transactional
    public AccessCodeResponse createAccessCode(Long electionId, CreateAccessCodeRequest request, String userId) {
        Election election = findElectionOrThrow(electionId);
        checkOwnership(election, userId);

        // Benzersiz kod üret
        String code;
        do {
            code = accessCodeConfig.generateCode();
        } while (accessCodeRepository.existsByCode(code));

        AccessCode accessCode = AccessCode.builder()
                .election(election)
                .code(code)
                .maxUses(request.getMaxUses())
                .createdBy(userId)
                .build();

        if (request.getExpiresInHours() != null) {
            accessCode.setExpiresAt(LocalDateTime.now().plusHours(request.getExpiresInHours()));
        }

        AccessCode saved = accessCodeRepository.save(accessCode);

        log.info("Erişim kodu oluşturuldu: electionId={}, code={}", electionId, code);

        return accessCodeMapper.toResponse(saved);
    }

    public List<AccessCodeResponse> getAccessCodes(Long electionId, String userId) {
        Election election = findElectionOrThrow(electionId);
        checkOwnership(election, userId);

        List<AccessCode> codes = accessCodeRepository.findByElectionIdAndIsActiveTrue(electionId);
        return accessCodeMapper.toResponseList(codes);
    }

    @Transactional
    public void deactivateAccessCode(Long electionId, Long codeId, String userId) {
        Election election = findElectionOrThrow(electionId);
        checkOwnership(election, userId);

        AccessCode accessCode = accessCodeRepository.findById(codeId)
                .orElseThrow(() -> ApiException.notFound("Erişim kodu bulunamadı"));

        if (!accessCode.getElection().getId().equals(electionId)) {
            throw ApiException.badRequest("Bu erişim kodu bu seçime ait değil");
        }

        accessCode.setIsActive(false);
        accessCodeRepository.save(accessCode);

        log.info("Erişim kodu deaktive edildi: electionId={}, codeId={}", electionId, codeId);
    }

    // ==================== ARCHIVE & PREVIEW ====================

    /**
     * Topluluk bazlı arşivlenmiş seçimleri listeler (CLOSED + ARCHIVED).
     */
    @Transactional(readOnly = true)
    public PageResponse<ElectionResponse> getArchivedElections(Long communityId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("endTime").descending());
        Page<Election> electionPage = electionRepository.findArchivedByCommunityId(communityId, pageable);
        return buildPageResponse(electionPage);
    }

    /**
     * Seçim önizleme – yayınlamadan önce tüm bilgileri ve eksikleri gösterir.
     */
    @Transactional(readOnly = true)
    public ElectionPreviewResponse previewElection(Long id, String userId) {
        Election election = findElectionOrThrow(id);
        checkOwnership(election, userId);

        long candidateCount = candidateRepository.countByElectionIdAndIsDeletedFalse(id);
        long accessCodeCount = accessCodeRepository.countByElectionIdAndIsActiveTrue(id);
        List<CandidateResponse> candidates = candidateRepository
                .findByElectionIdAndIsDeletedFalseOrderByDisplayOrderAsc(id).stream()
                .map(electionMapper::toCandidateResponse)
                .toList();

        // Yayınlama gereksinimleri kontrolü
        List<String> warnings = new java.util.ArrayList<>();
        boolean readyToPublish = true;

        if (candidateCount < 2) {
            warnings.add("En az 2 aday gereklidir (mevcut: " + candidateCount + ")");
            readyToPublish = false;
        }
        if (election.getStartTime() == null) {
            warnings.add("Başlangıç zamanı belirtilmelidir");
            readyToPublish = false;
        }
        if (election.getEndTime() == null) {
            warnings.add("Bitiş zamanı belirtilmelidir");
            readyToPublish = false;
        }
        if (election.getStartTime() != null && election.getStartTime().isBefore(LocalDateTime.now())) {
            warnings.add("Başlangıç zamanı geçmiş, güncellenmesi gerekebilir");
        }
        if (election.getTitle() == null || election.getTitle().isBlank()) {
            warnings.add("Seçim başlığı boş olamaz");
            readyToPublish = false;
        }

        return ElectionPreviewResponse.builder()
                .electionId(election.getId())
                .title(election.getTitle())
                .description(election.getDescription())
                .status(election.getStatus())
                .type(election.getType())
                .participantType(election.getParticipantType())
                .startTime(election.getStartTime())
                .endTime(election.getEndTime())
                .anonymousVoting(election.getAnonymousVoting())
                .resultsPublic(election.getResultsPublic())
                .candidateCount((int) candidateCount)
                .accessCodeCount((int) accessCodeCount)
                .candidates(candidates)
                .readyToPublish(readyToPublish)
                .warnings(warnings)
                .build();
    }

    // ==================== PROOFS ====================

    @Transactional(readOnly = true)
    public ElectionProofResponse getElectionProofs(Long id) {
        Election election = findElectionOrThrow(id);

        return ElectionProofResponse.builder()
                .electionId(election.getId())
                .electionGuardContext(election.getElectionGuardContext())
                .electionManifest(election.getElectionManifest())
                .guardianRecords(election.getGuardianRecords())
                .tallyProof(election.getTallyProof())
                .build();
    }

    // ==================== HELPER METHODS ====================

    private Election findElectionOrThrow(Long id) {
        return electionRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
    }

    private void checkOwnership(Election election, String userId) {
        if (!election.getCreatedBy().equals(userId)) {
            throw ApiException.forbidden("Bu işlem için yetkiniz yok");
        }
    }

    /**
     * Guardian records JSON string'ini gRPC GuardianRecord listesine parse eder.
     */
    private List<GuardianRecord> parseGuardianRecords(String guardianRecordsJson) {
        List<GuardianRecord> records = new java.util.ArrayList<>();
        if (guardianRecordsJson == null || guardianRecordsJson.isEmpty()) {
            return records;
        }
        try {
            // Basit JSON array parse — jackson kullanılabilir
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(guardianRecordsJson);
            for (com.fasterxml.jackson.databind.JsonNode node : root) {
                records.add(GuardianRecord.newBuilder()
                        .setGuardianId(node.get("guardian_id").asText())
                        .setSerializedGuardian(node.get("serialized_guardian").toString())
                        .build());
            }
        } catch (Exception e) {
            log.error("Guardian records parse hatası: {}", e.getMessage());
        }
        return records;
    }

    private PageResponse<ElectionResponse> buildPageResponse(Page<Election> page) {
        return PageResponse.<ElectionResponse>builder()
                .content(electionMapper.toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
