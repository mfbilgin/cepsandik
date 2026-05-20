package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.CryptoEngineClient;
import com.cepsandik.electionservice.client.CommunityServiceClient;
import com.cepsandik.electionservice.client.UserServiceClient;
import com.cepsandik.electionservice.config.AccessCodeConfig;
import org.slf4j.MDC;
import com.cepsandik.electionservice.config.UtcClock;
import com.cepsandik.electionservice.dto.request.CreateAccessCodeRequest;
import com.cepsandik.electionservice.dto.request.CreateCandidateRequest;
import com.cepsandik.electionservice.dto.request.CreateElectionRequest;
import com.cepsandik.electionservice.dto.request.UpdateElectionRequest;
import com.cepsandik.electionservice.dto.response.*;
import com.cepsandik.electionservice.entity.AccessCode;
import com.cepsandik.electionservice.entity.Candidate;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.entity.ElectionGuardian;
import com.cepsandik.electionservice.entity.Vote;
import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.exception.ApiException;
import com.cepsandik.electionservice.grpc.*;
import com.cepsandik.electionservice.dto.GuardianAssignmentEvent;
import com.cepsandik.electionservice.config.NotificationRabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.cepsandik.electionservice.repository.ElectionGuardianRepository;
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

import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElectionService {

    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final AccessCodeRepository accessCodeRepository;
    private final VoteRepository voteRepository;
    private final ElectionGuardianRepository guardianRepository;
    private final ElectionMapper electionMapper;
    private final AccessCodeMapper accessCodeMapper;
    private final AccessCodeConfig accessCodeConfig;
    private final CryptoEngineClient cryptoEngineClient;
    private final CommunityServiceClient communityServiceClient;
    private final UserServiceClient userServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final UtcClock utcClock;
    private final BulletinOutboxService bulletinOutbox;

    @Value("${app.guardian.count:5}")
    private int guardianCount;

    @Value("${app.guardian.quorum:3}")
    private int guardianQuorum;

    /**
     * Dev/test bayrağı: Eğer aktif gönüllü emanetçi bulunamazsa publish hata
     * fırlatmasın, distributed ceremony'yi atlasın. SCHEDULED→ACTIVE geçişinde
     * ElectionLifecycleService.setupElectionGuard() (auto SetupElection RPC) yine
     * sahte guardian'ları + joint key'i üretir. Production'da false olmalı.
     */
    @Value("${app.guardian.dev-bypass:false}")
    private boolean guardianDevBypass;

    /** Key ceremony süresi (saat) — publish'ten itibaren setup_deadline. */
    @Value("${app.guardian.ceremony-hours:4}")
    private int ceremonyHours;

    // ==================== ELECTION CRUD ====================

    @Transactional
    public ElectionResponse createElection(CreateElectionRequest request, String userId) {
        Election election = Election.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .communityId(request.getCommunityId())
                .createdBy(userId)
                .type(request.getType())
                .participantType(request.getParticipantType())
                .maxSelections(request.getMaxSelections())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .resultsPublic(request.getResultsPublic() != null ? request.getResultsPublic() : true)
                .build();

        Election saved = electionRepository.save(election);
        log.info("Seçim oluşturuldu: id={}, title={}", saved.getId(), saved.getTitle());

        // Her seçim için otomatik erişim kodu oluştur
        String code;
        do {
            code = accessCodeConfig.generateCode();
        } while (accessCodeRepository.existsByCode(code));
        accessCodeRepository.save(AccessCode.builder()
                .election(saved)
                .code(code)
                .createdBy(userId)
                .build());
        log.info("Seçim için erişim kodu oluşturuldu: electionId={}, code={}", saved.getId(), code);

        return electionMapper.toDetailedResponse(saved);
    }

    @Transactional(readOnly = true)
    public ElectionResponse getElectionById(Long id) {
        Election election = findElectionOrThrow(id);
        return electionMapper.toDetailedResponse(election);
    }

    @Transactional(readOnly = true)
    public PageResponse<ElectionResponse> getElectionsByCommunity(Long communityId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Election> electionPage = electionRepository.findByCommunityIdAndIsDeletedFalse(communityId, pageable);
        return buildPageResponse(electionPage);
    }

    @Transactional
    public ElectionResponse updateElection(Long id, UpdateElectionRequest request, String userId) {
        Election election = findElectionOrThrow(id);
        checkOwnership(election, userId);

        if (!election.isEditable()) {
            throw ApiException.badRequest("Sadece taslak seçimler güncellenebilir");
        }

        if (request.getTitle() != null) election.setTitle(request.getTitle());
        if (request.getDescription() != null) election.setDescription(request.getDescription());
        if (request.getStartTime() != null) election.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) election.setEndTime(request.getEndTime());
        if (request.getResultsPublic() != null) election.setResultsPublic(request.getResultsPublic());
        if (request.getMaxSelections() != null) election.setMaxSelections(request.getMaxSelections());

        Election saved = electionRepository.save(election);
        log.info("Seçim güncellendi: id={}", id);
        return electionMapper.toDetailedResponse(saved);
    }

    @Transactional
    public void deleteElection(Long id, String userId) {
        Election election = findElectionOrThrow(id);
        checkOwnership(election, userId);

        if (!election.isEditable()) {
            throw ApiException.badRequest("Sadece taslak seçimler silinebilir");
        }

        election.setIsDeleted(true);
        electionRepository.save(election);
        log.info("Seçim silindi (soft delete): id={}", id);
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

        // === Dağıtık Emanetçi Seçimi ===
        List<ElectionGuardian> preselectedGuardians = guardianRepository.findAllByElectionId(id);
        if (preselectedGuardians.isEmpty()) {
            selectGuardians(election);
        } else if (preselectedGuardians.size() < guardianQuorum) {
            throw ApiException.badRequest("Manuel emanetçi listesi quorum için yetersiz. Gerekli: "
                    + guardianQuorum + ", mevcut: " + preselectedGuardians.size());
        } else {
            log.info("Seçim {} için önceden atanmış {} manuel emanetçi korunuyor.",
                    id, preselectedGuardians.size());
            notifyGuardianAssignments(election, preselectedGuardians.stream()
                    .map(guardian -> guardian.getUserId().toString())
                    .toList());
        }

        election.setStatus(ElectionStatus.SCHEDULED);
        election.setMinGuardiansThreshold(guardianQuorum);
        // Key ceremony deadline — publish + ceremonyHours (adaptif yedek formülü taban alır)
        election.setSetupDeadline(java.time.Instant.now().plusSeconds(ceremonyHours * 3600L));
        election.setSubstitutionCount(0);
        Election saved = electionRepository.save(election);

        log.info("Seçim yayınlandı: id={}, status=SCHEDULED, guardians_selected={}", id, guardianCount);

        return electionMapper.toDetailedResponse(saved);
    }

    /**
     * Seçim için rastgele emanetçileri seçer ve davet eder.
     */
    private void selectGuardians(Election election) {
        List<String> eligibleUserIds;

        if (election.getCommunityId() != null) {
            // 1. Topluluk seçimi: Topluluk üyelerini al
            List<String> memberIds = communityServiceClient.getMemberUserIds(election.getCommunityId());
            
            // 2. Uygun olanları filtrele (Opt-in yapmış ve aktif olanlar)
            eligibleUserIds = userServiceClient.filterEligibleGuardians(memberIds);
        } else {
            // Bağımsız seçim: Tüm sistemden rastgele uygun kişileri al
            eligibleUserIds = userServiceClient.getRandomEligibleGuardians(guardianCount);
        }

        if (eligibleUserIds.size() < guardianQuorum) {
            if (guardianDevBypass) {
                log.warn("[DEV-BYPASS] Yeterli gönüllü emanetçi yok ({}/{}), distributed ceremony atlandı; " +
                        "scheduler ACTIVE'e çekerken auto-ceremony (SetupElection RPC) tetiklenecek.",
                        eligibleUserIds.size(), guardianQuorum);
                return;
            }
            throw ApiException.badRequest("Seçim başlatmak için yeterli (en az " + guardianQuorum +
                    ") gönüllü emanetçi bulunamadı. Mevcut uygun üye: " + eligibleUserIds.size());
        }

        // Maksimum gardiyan sayısı kadar karıştır ve seç
        java.util.Collections.shuffle(eligibleUserIds);
        List<String> selectedIds = eligibleUserIds.stream()
                .limit(guardianCount)
                .toList();

        // ElectionGuardian kayıtlarını oluştur — xCoordinate 1-indexed, KMP
        // polinom x'i. KMP guardianId = "trustee{xCoordinate}" (mobile bu
        // şemayı kullanır; ceremony boyunca tutarlı olmalı).
        int xCoord = 1;
        for (String gUserId : selectedIds) {
            ElectionGuardian guardian = ElectionGuardian.builder()
                    .election(election)
                    .userId(java.util.UUID.fromString(gUserId))
                    .xCoordinate(xCoord++)
                    .status(ElectionGuardian.GuardianStatus.PENDING)
                    .build();
            guardianRepository.save(guardian);
        }
        
        log.info("Seçim {} için {} adet emanetçi seçildi.", election.getId(), selectedIds.size());

        notifyGuardianAssignments(election, selectedIds);
    }

    private void notifyGuardianAssignments(Election election, List<String> selectedIds) {
        try {
            String communityName = election.getCommunityId() != null 
                    ? communityServiceClient.getCommunityName(election.getCommunityId()) 
                    : "Bağımsız Seçim";

            GuardianAssignmentEvent event = GuardianAssignmentEvent.builder()
                    .electionId(election.getId())
                    .electionTitle(election.getTitle())
                    .communityName(communityName)
                    .selectedGuardianIds(selectedIds.stream().map(java.util.UUID::fromString).toList())
                    .category("GUARDIAN_DUTY")
                    .build();

            rabbitTemplate.convertAndSend(
                    NotificationRabbitConfig.NOTIFICATION_EXCHANGE,
                    NotificationRabbitConfig.ELECTION_NOTIFICATION_ROUTING_KEY + ".guardian",
                    event
            );
            log.info("Sandık görevlisi atama bildirimi kuyruğa gönderildi. ElectionId={}", election.getId());
        } catch (Exception e) {
            log.error("Bildirim gönderimi sırasında hata: ", e);
        }
    }

    @Transactional
    public ElectionResponse assignManualGuardians(Long electionId, String userId, List<String> guardianUserIds) {
        Election election = findElectionOrThrow(electionId);
        checkOwnership(election, userId);

        if (!election.isDraft()) {
            throw ApiException.badRequest("Emanetçiler sadece DRAFT durumundaki seçimlerde manuel atanabilir");
        }
        if (guardianUserIds == null || guardianUserIds.isEmpty()) {
            throw ApiException.badRequest("guardianUserIds zorunludur");
        }

        List<String> selectedIds = guardianUserIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        if (selectedIds.size() < guardianQuorum) {
            throw ApiException.badRequest("Manuel emanetçi sayısı quorum için yetersiz. Gerekli: "
                    + guardianQuorum + ", mevcut: " + selectedIds.size());
        }

        for (String id : selectedIds) {
            try {
                UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Geçersiz kullanıcı UUID: " + id);
            }
        }

        List<ElectionGuardian> existing = guardianRepository.findAllByElectionId(electionId);
        if (!existing.isEmpty()) {
            guardianRepository.deleteAll(existing);
        }

        int xCoord = 1;
        for (String gUserId : selectedIds) {
            ElectionGuardian guardian = ElectionGuardian.builder()
                    .election(election)
                    .userId(UUID.fromString(gUserId))
                    .xCoordinate(xCoord++)
                    .status(ElectionGuardian.GuardianStatus.PENDING)
                    .build();
            guardianRepository.save(guardian);
        }

        log.info("Seçim {} için manuel emanetçiler atandı: {}", electionId, selectedIds);
        enqueueManualGuardianSelectionProof(election, userId, selectedIds);
        return electionMapper.toDetailedResponse(election);
    }

    private void enqueueManualGuardianSelectionProof(Election election, String assignedByUserId, List<String> selectedIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("guardianSelectionMethod", "MANUAL");
        payload.put("electionId", election.getId());
        payload.put("electionTitle", election.getTitle());
        payload.put("assignedByUserId", assignedByUserId);
        payload.put("guardianCount", selectedIds.size());
        payload.put("guardianQuorum", guardianQuorum);
        payload.put("guardianUserIds", selectedIds);
        payload.put("recordedAt", Instant.now().toString());

        bulletinOutbox.enqueue(
                String.valueOf(election.getId()),
                "GUARDIANS_SELECTED_MANUAL",
                "",
                "",
                toJson(payload),
                true);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Bulletin payload serialize hatası", e);
        }
    }

    @Transactional
    public ElectionResponse startElection(Long id, String userId) {
        Election election = findElectionOrThrow(id);
        checkOwnership(election, userId);

        if (!election.isScheduled()) {
            throw ApiException.badRequest("Sadece planlanmış seçimler başlatılabilir");
        }

        // Dağıtık yapıda seçim, tüm emanetçiler anahtarını yüklediğinde OTOMATİK başlar (ACTIVE olur).
        // Bu metod sadece manuel zorlama için (isteğe bağlı) veya statü kontrolü için kalabilir.
        if (!election.getStatus().equals(ElectionStatus.ACTIVE)) {
            throw ApiException.badRequest("Seçimin başlaması için tüm emanetçilerin anahtarlarını yüklemesi bekleniyor");
        }

        return electionMapper.toDetailedResponse(election);
    }

    @Transactional
    public ElectionResponse endElection(Long id, String userId) {
        Election election = findElectionOrThrow(id);
        checkOwnership(election, userId);

        if (!election.isActive()) {
            throw ApiException.badRequest("Sadece aktif seçimler sonlandırılabilir");
        }

        // Seçimi kapat
        election.setStatus(ElectionStatus.CLOSED);
        election.setEndTime(utcClock.instant());
        Election saved = electionRepository.save(election);

        log.info("Seçim sonlandırıldı: id={}, status=CLOSED. Artık emanetçi deşifre payları bekleniyor.", id);

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

    // ==================== DISTRIBUTED SETUP (Sprint 5.A leader-mode) ====================

    /**
     * Leader-mode distributed setup. Owner (election creator) cihazında lokal
     * KMP ile N trustee yaratıp publicKeysJsons'ı toplu olarak buraya yollar;
     * server CreateJointKey RPC ile joint key + ElectionInitialized üretir,
     * election ACTIVE'e geçer.
     *
     * Sunucu private polynomial katsayılarını VEYA key share'leri ASLA görmez.
     * Tüm private state mobile SecureStore'da kalır.
     */
    @Transactional
    public ElectionResponse completeDistributedSetup(Long electionId, String userId,
                                                      List<String> publicKeysJsons,
                                                      List<String> guardianIds) {
        Election election = findElectionOrThrow(electionId);
        checkOwnership(election, userId);

        if (!election.isScheduled()) {
            throw ApiException.badRequest("Sadece SCHEDULED durumdaki seçimler için distributed setup yapılabilir");
        }
        if (publicKeysJsons == null || publicKeysJsons.isEmpty()) {
            throw ApiException.badRequest("publicKeysJsons gerekli");
        }
        if (guardianIds == null || guardianIds.size() != publicKeysJsons.size()) {
            throw ApiException.badRequest("guardianIds size publicKeysJsons size ile eşleşmeli");
        }

        int n = publicKeysJsons.size();
        int q = election.getMinGuardiansThreshold() != null ? election.getMinGuardiansThreshold() : guardianQuorum;
        if (q < 1 || q > n) {
            throw ApiException.badRequest("Geçersiz quorum: q=" + q + ", n=" + n);
        }

        // Candidates → ContestInfo
        List<Candidate> candidates = candidateRepository
                .findByElectionIdAndIsDeletedFalseOrderByDisplayOrderAsc(electionId);
        if (candidates.isEmpty()) {
            throw ApiException.badRequest("Seçim için aday/seçenek tanımlı değil");
        }
        com.cepsandik.electionservice.grpc.ContestInfo contestInfo =
                com.cepsandik.electionservice.grpc.ContestInfo.newBuilder()
                        .setContestId("contest_" + electionId)
                        .addAllSelectionIds(candidates.stream().map(c -> "candidate_" + c.getId()).toList())
                        .setNumberElected(election.getMaxSelections() != null ? election.getMaxSelections() : 1)
                        .setName(election.getTitle())
                        .build();

        // PublicKeys proto build — guardian_id + coefficient_proofs (KMP PublicKeysJson serialize).
        // public_key + commitments alanları KMP'de coefficient_proofs[0].public_key tarafından
        // taşındığı için boş bırakılır.
        List<com.cepsandik.electionservice.grpc.GuardianPublicKey> publicKeys = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            publicKeys.add(com.cepsandik.electionservice.grpc.GuardianPublicKey.newBuilder()
                    .setGuardianId(guardianIds.get(i))
                    .setPublicKey("")
                    .setCommitments("")
                    .setCoefficientProofs(extractCoefficientProofsFromPublicKeysJson(publicKeysJsons.get(i)))
                    .build());
        }

        com.cepsandik.electionservice.grpc.CreateJointKeyResponse response = cryptoEngineClient.createJointKey(
                String.valueOf(electionId),
                n, q, publicKeys, List.of(contestInfo),
                election.getStartTime() != null ? election.getStartTime().toString() : "",
                election.getEndTime() != null ? election.getEndTime().toString() : "");

        election.setElectionGuardContext(response.getElectionGuardContext());
        election.setElectionManifest(response.getElectionManifest());
        election.setElectionPublicKey(response.getJointPublicKey());
        try {
            election.setParticipatingGuardianIds(new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(guardianIds));
        } catch (Exception e) {
            throw new RuntimeException("participatingGuardianIds JSON serialize hatası", e);
        }
        election.setStatus(ElectionStatus.ACTIVE);
        Election saved = electionRepository.save(election);

        log.info("Distributed setup tamamlandı: electionId={}, n={}, q={}, jointPublicKey={}...",
                electionId, n, q, response.getJointPublicKey().substring(0, Math.min(40, response.getJointPublicKey().length())));

        return electionMapper.toDetailedResponse(saved);
    }

    /**
     * PublicKeysJson string'inden `coefficient_proofs` alanını ayıklar.
     * KMP `PublicKeysJson`: { guardianId, guardianXCoordinate, coefficientProofs: [...] }
     * Bizim CreateJointKeyService bunu yine PublicKeysJson olarak kullanmak
     * isteyebilir; mevcut proto sözleşmesi `coefficient_proofs` (List<SchnorrProofJson>)
     * JSON string bekliyor. Burada full PublicKeysJson'dan coefficientProofs alt-array'ini
     * çıkarırız.
     */
    private String extractCoefficientProofsFromPublicKeysJson(String publicKeysJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(publicKeysJson);
            com.fasterxml.jackson.databind.JsonNode coefArray = root.get("coefficientProofs");
            if (coefArray == null || coefArray.isNull()) {
                throw ApiException.badRequest("PublicKeysJson içinde coefficientProofs eksik");
            }
            return coefArray.toString();
        } catch (Exception e) {
            throw new RuntimeException("PublicKeysJson parse hatası: " + e.getMessage(), e);
        }
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
            accessCode.setExpiresAt(utcClock.instant().plus(request.getExpiresInHours(), ChronoUnit.HOURS));
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
        if (election.getStartTime() != null && election.getStartTime().isBefore(utcClock.instant())) {
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
                .resultsPublic(election.getResultsPublic())
                .candidateCount((int) candidateCount)
                .accessCodeCount((int) accessCodeCount)
                .candidates(candidates)
                .readyToPublish(readyToPublish)
                .warnings(warnings)
                .build();
    }

    // ==================== ENCRYPTION PARAMS (E2E-V) ====================

    @Transactional(readOnly = true)
    public EncryptionParamsResponse getEncryptionParams(Long id) {
        Election election = findElectionOrThrow(id);

        if (election.getElectionPublicKey() == null
                || election.getElectionGuardContext() == null
                || election.getElectionManifest() == null) {
            throw ApiException.badRequest(
                    "Seçim henüz şifreleme parametrelerine hazır değil — guardian key ceremony tamamlanmamış"
            );
        }

        return EncryptionParamsResponse.builder()
                .electionId(election.getId())
                .specVersion("v2.0")  // Faz 2: KMP 2.0 spec; mobile native modülü bu sürümü bekliyor
                .electionGuardContext(election.getElectionGuardContext())
                .electionManifest(election.getElectionManifest())
                .jointPublicKey(election.getElectionPublicKey())
                .build();
    }

    // ==================== PROOFS ====================

    @Transactional(readOnly = true)
    public ElectionProofResponse getElectionProofs(Long id) {
        Election election = findElectionOrThrow(id);

        List<ElectionProofResponse.BallotProofResponse> ballots = voteRepository.findByElectionId(id).stream()
                .map(v -> ElectionProofResponse.BallotProofResponse.builder()
                        .ballotId(v.getBallotId())
                        .trackingCode(v.getTrackingCode())
                        .encryptedBallot(v.getEncryptedBallot())
                        .zkpProof(v.getZkpProof())
                        .ballotHash(v.getBallotHash())
                        .build())
                .collect(Collectors.toList());

        return ElectionProofResponse.builder()
                .electionId(election.getId())
                .electionGuardContext(election.getElectionGuardContext())
                .electionManifest(election.getElectionManifest())
                .guardianRecords(election.getGuardianRecords())
                .tallyProof(election.getTallyProof())
                .ballots(ballots)
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

    // ==================== GLOBAL BY-CODE VERIFY ====================

    /**
     * Erişim kodunu electionId olmadan doğrular. Seçim bir topluluğa aitse
     * ve kullanıcı üye değilse requiresMembership=true döner; aksi hâlde
     * normal seçim verisi döner.
     */
    @Transactional
    public AccessVerificationResponse verifyByCode(String code, String userId) {
        AccessCode accessCode = accessCodeRepository.findByCodeAndIsActiveTrue(code.toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Geçersiz veya süresi dolmuş erişim kodu"));

        Election election = accessCode.getElection();
        if (!election.isActive()) {
            throw ApiException.badRequest("Bu seçim şu an aktif değil");
        }

        Instant now = utcClock.instant();
        if (!accessCode.isValid(now)) {
            throw ApiException.badRequest("Bu erişim kodu aktif değil, süresi dolmuş veya kullanım limiti dolmuş");
        }

        // Topluluk seçimine erişim: üyelik kontrolü
        if (election.getCommunityId() != null) {
            List<String> memberIds = communityServiceClient.getMemberUserIds(election.getCommunityId());
            if (!memberIds.contains(userId)) {
                String communityName = communityServiceClient.getCommunityName(election.getCommunityId());
                return AccessVerificationResponse.builder()
                        .requiresMembership(true)
                        .communityId(election.getCommunityId())
                        .communityName(communityName != null ? communityName : "Topluluk #" + election.getCommunityId())
                        .build();
            }
        }

        accessCode.incrementUsage();
        accessCodeRepository.save(accessCode);

        List<CandidateResponse> candidates = candidateRepository
                .findByElectionIdAndIsDeletedFalseOrderByDisplayOrderAsc(election.getId())
                .stream()
                .map(c -> CandidateResponse.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.getDescription())
                        .imageUrl(c.getImageUrl())
                        .displayOrder(c.getDisplayOrder())
                        .candidateType(c.getCandidateType())
                        .memberUserId(c.getMemberUserId())
                        .createdAt(c.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return AccessVerificationResponse.builder()
                .requiresMembership(false)
                .electionId(election.getId())
                .electionTitle(election.getTitle())
                .electionDescription(election.getDescription())
                .status(election.getStatus())
                .type(election.getType())
                .maxSelections(election.getMaxSelections())
                .startTime(election.getStartTime())
                .endTime(election.getEndTime())
                .candidateCount(candidates.size())
                .candidates(candidates)
                .build();
    }
}
