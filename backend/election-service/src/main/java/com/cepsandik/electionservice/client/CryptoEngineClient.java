package com.cepsandik.electionservice.client;

import com.cepsandik.electionservice.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Crypto-Engine gRPC istemcisi.
 * Election Service → Crypto-Engine arasındaki tüm kriptografik iletişimi yönetir.
 */
@Component
@Slf4j
public class CryptoEngineClient {

    @Value("${app.crypto-engine.host:localhost}")
    private String host;

    @Value("${app.crypto-engine.port:50051}")
    private int port;

    private ManagedChannel channel;
    private CryptoServiceGrpc.CryptoServiceBlockingStub blockingStub;

    @PostConstruct
    public void init() {
        log.info("Crypto-Engine gRPC bağlantısı kuruluyor: {}:{}", host, port);
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()         // Internal network — TLS yok
                .maxInboundMessageSize(50 * 1024 * 1024)  // 50MB
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .build();
        blockingStub = CryptoServiceGrpc.newBlockingStub(channel);
        log.info("Crypto-Engine gRPC bağlantısı hazır.");
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            log.info("Crypto-Engine gRPC bağlantısı kapatılıyor...");
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // ==================== Setup Election ====================

    /**
     * Seçim için ElectionGuard key ceremony yapar.
     * Dönen context, manifest ve guardian record'ları Java tarafında PostgreSQL'e kaydedilir.
     */
    public SetupElectionResponse setupElection(
            String electionId,
            int numberOfGuardians,
            int quorum,
            List<ContestInfo> contests,
            String startDate,
            String endDate) {

        log.info("SetupElection gRPC çağrısı: election={}, N={}, Q={}",
                electionId, numberOfGuardians, quorum);

        try {
            SetupElectionRequest request = SetupElectionRequest.newBuilder()
                    .setElectionId(electionId)
                    .setNumberOfGuardians(numberOfGuardians)
                    .setQuorum(quorum)
                    .addAllContests(contests)
                    .setStartDate(startDate != null ? startDate : "")
                    .setEndDate(endDate != null ? endDate : "")
                    .build();

            SetupElectionResponse response = blockingStub
                    .withDeadlineAfter(60, TimeUnit.SECONDS)
                    .setupElection(request);

            log.info("SetupElection başarılı: election={}, guardian_count={}",
                    electionId, response.getGuardianRecordsCount());

            return response;

        } catch (StatusRuntimeException e) {
            log.error("SetupElection gRPC hatası: status={}, desc={}",
                    e.getStatus().getCode(), e.getStatus().getDescription());
            throw new RuntimeException("Crypto-Engine SetupElection hatası: " + e.getMessage(), e);
        }
    }

    // ==================== Validate Ballot (E2E-V) ====================

    /**
     * Mobile'da client-side ElGamal ile şifrelenmiş ballot'u doğrular.
     * Server hiçbir aşamada plaintext oyu görmez.
     */
    public ValidateBallotResponse validateBallot(
            String electionId,
            String electionGuardContext,
            String electionManifest,
            String ballotId,
            String ciphertextBallot,
            String zkpProof,
            String trackingCode,
            String ballotHash) {

        log.info("ValidateBallot gRPC çağrısı: election={}, ballot={}", electionId, ballotId);

        try {
            ValidateBallotRequest request = ValidateBallotRequest.newBuilder()
                    .setElectionId(electionId)
                    .setElectionGuardContext(electionGuardContext)
                    .setElectionManifest(electionManifest)
                    .setBallotId(ballotId)
                    .setCiphertextBallot(ciphertextBallot)
                    .setZkpProof(zkpProof != null ? zkpProof : "")
                    .setTrackingCode(trackingCode != null ? trackingCode : "")
                    .setBallotHash(ballotHash != null ? ballotHash : "")
                    .build();

            return blockingStub
                    .withDeadlineAfter(30, TimeUnit.SECONDS)
                    .validateBallot(request);

        } catch (StatusRuntimeException e) {
            log.error("ValidateBallot gRPC hatası: status={}, desc={}",
                    e.getStatus().getCode(), e.getStatus().getDescription());
            throw new RuntimeException("Crypto-Engine ValidateBallot hatası: " + e.getMessage(), e);
        }
    }

    // ==================== Tally Election ====================

    /**
     * Seçim sonuçlarını threshold decryption ile çözer.
     */
    public TallyElectionResponse tallyElection(
            String electionId,
            String electionGuardContext,
            String electionManifest,
            List<String> ciphertextBallots,
            List<GuardianRecord> guardianRecords,
            int quorum) {

        log.info("TallyElection gRPC çağrısı: election={}, ballot_count={}, guardian_count={}",
                electionId, ciphertextBallots.size(), guardianRecords.size());

        try {
            TallyElectionRequest request = TallyElectionRequest.newBuilder()
                    .setElectionId(electionId)
                    .setElectionGuardContext(electionGuardContext)
                    .setElectionManifest(electionManifest)
                    .addAllCiphertextBallots(ciphertextBallots)
                    .addAllGuardianRecords(guardianRecords)
                    .setQuorum(quorum)
                    .build();

            TallyElectionResponse response = blockingStub
                    .withDeadlineAfter(120, TimeUnit.SECONDS)  // Tally uzun sürebilir
                    .tallyElection(request);

            log.info("TallyElection başarılı: election={}, contest_count={}",
                    electionId, response.getResultsCount());

            return response;

        } catch (StatusRuntimeException e) {
            log.error("TallyElection gRPC hatası: status={}, desc={}",
                    e.getStatus().getCode(), e.getStatus().getDescription());
            throw new RuntimeException("Crypto-Engine TallyElection hatası: " + e.getMessage(), e);
        }
    }

    /**
     * Bireysel kamu anahtarlarını birleştirip ortak anahtar ve context üretir.
     */
    public CreateJointKeyResponse createJointKey(
            String electionId,
            int numberOfGuardians,
            int quorum,
            List<GuardianPublicKey> publicKeys,
            List<ContestInfo> contests,
            String startDate,
            String endDate) {

        log.info("CreateJointKey gRPC çağrısı: election={}, guardians={}", electionId, publicKeys.size());

        try {
            CreateJointKeyRequest request = CreateJointKeyRequest.newBuilder()
                    .setElectionId(electionId)
                    .setNumberOfGuardians(numberOfGuardians)
                    .setQuorum(quorum)
                    .addAllPublicKeys(publicKeys)
                    .addAllContests(contests)
                    .setStartDate(startDate != null ? startDate : "")
                    .setEndDate(endDate != null ? endDate : "")
                    .build();

            return blockingStub
                    .withDeadlineAfter(60, TimeUnit.SECONDS)
                    .createJointKey(request);

        } catch (StatusRuntimeException e) {
            log.error("CreateJointKey gRPC hatası: {}", e.getMessage());
            throw new RuntimeException("Joint key üretilemedi: " + e.getMessage(), e);
        }
    }

    /**
     * @deprecated Sprint 5.C.2: gerçek E2E-V için 5-stage akışı (start +
     * partial + getChallenges + response + finalize) kullan. Bu metot
     * sunucunun guardian private key share'ini görmesini gerektirdiği için
     * E2E-V garantisini bozar.
     */
    @Deprecated
    public TallyElectionResponse decryptWithShares(
            String electionId,
            String context,
            String manifest,
            List<String> ballots,
            List<DecryptionShare> shares) {

        log.warn("decryptWithShares DEPRECATED: election={} — distributed 5-stage flow'a geç", electionId);
        try {
            DecryptWithSharesRequest request = DecryptWithSharesRequest.newBuilder()
                    .setElectionId(electionId)
                    .setElectionGuardContext(context)
                    .setElectionManifest(manifest)
                    .addAllCiphertextBallots(ballots)
                    .addAllShares(shares)
                    .build();
            return blockingStub.withDeadlineAfter(120, TimeUnit.SECONDS).decryptWithShares(request);
        } catch (StatusRuntimeException e) {
            throw new RuntimeException("Deşifre işlemi başarısız: " + e.getMessage(), e);
        }
    }

    // ===== Sprint 5.C.2 — Distributed 3-round threshold decryption =====

    public com.cepsandik.electionservice.grpc.StartTallyDecryptionSessionResponse startTallyDecryptionSession(
            String electionId,
            String electionGuardContext,
            String electionManifest,
            List<String> ciphertextBallots,
            List<String> participatingGuardianIds) {
        log.info("startTallyDecryptionSession: election={}, ballots={}, guardians={}",
                electionId, ciphertextBallots.size(), participatingGuardianIds.size());
        try {
            var request = com.cepsandik.electionservice.grpc.StartTallyDecryptionSessionRequest.newBuilder()
                    .setElectionId(electionId)
                    .setElectionGuardContext(electionGuardContext)
                    .setElectionManifest(electionManifest)
                    .addAllCiphertextBallots(ciphertextBallots)
                    .addAllParticipatingGuardianIds(participatingGuardianIds)
                    .build();
            return blockingStub.withDeadlineAfter(60, TimeUnit.SECONDS)
                    .startTallyDecryptionSession(request);
        } catch (StatusRuntimeException e) {
            log.error("startTallyDecryptionSession FAIL: {}", e.getMessage());
            throw new RuntimeException("Tally session başlatılamadı: " + e.getMessage(), e);
        }
    }

    public com.cepsandik.electionservice.grpc.SessionAckResponse submitPartialDecryption(
            String sessionId, String guardianId, String partialsJson) {
        try {
            var request = com.cepsandik.electionservice.grpc.SubmitPartialDecryptionRequest.newBuilder()
                    .setSessionId(sessionId)
                    .setGuardianId(guardianId)
                    .setPartialDecryptionsJson(partialsJson)
                    .build();
            return blockingStub.withDeadlineAfter(30, TimeUnit.SECONDS)
                    .submitPartialDecryption(request);
        } catch (StatusRuntimeException e) {
            log.error("submitPartialDecryption FAIL: session={}, guardian={}: {}",
                    sessionId, guardianId, e.getMessage());
            throw new RuntimeException("Partial decryption submit hatası: " + e.getMessage(), e);
        }
    }

    public com.cepsandik.electionservice.grpc.GetChallengesResponse getChallenges(
            String sessionId, String guardianId) {
        try {
            var request = com.cepsandik.electionservice.grpc.GetChallengesRequest.newBuilder()
                    .setSessionId(sessionId)
                    .setGuardianId(guardianId)
                    .build();
            // Long-poll: server'da DecryptorDoerre Fiat-Shamir bitene kadar bekler (~30s).
            return blockingStub.withDeadlineAfter(45, TimeUnit.SECONDS)
                    .getChallenges(request);
        } catch (StatusRuntimeException e) {
            log.error("getChallenges FAIL: session={}, guardian={}: {}",
                    sessionId, guardianId, e.getMessage());
            throw new RuntimeException("Challenges alınamadı: " + e.getMessage(), e);
        }
    }

    public com.cepsandik.electionservice.grpc.SessionAckResponse submitChallengeResponse(
            String sessionId, String guardianId, String responsesJson) {
        try {
            var request = com.cepsandik.electionservice.grpc.SubmitChallengeResponseRequest.newBuilder()
                    .setSessionId(sessionId)
                    .setGuardianId(guardianId)
                    .setChallengeResponsesJson(responsesJson)
                    .build();
            return blockingStub.withDeadlineAfter(30, TimeUnit.SECONDS)
                    .submitChallengeResponse(request);
        } catch (StatusRuntimeException e) {
            log.error("submitChallengeResponse FAIL: session={}, guardian={}: {}",
                    sessionId, guardianId, e.getMessage());
            throw new RuntimeException("Challenge response submit hatası: " + e.getMessage(), e);
        }
    }

    public TallyElectionResponse finalizeTallyDecryption(String sessionId) {
        log.info("finalizeTallyDecryption: session={}", sessionId);
        try {
            var request = com.cepsandik.electionservice.grpc.FinalizeTallyDecryptionRequest.newBuilder()
                    .setSessionId(sessionId)
                    .build();
            // Long-poll: DecryptorDoerre background thread bitsin (~60s).
            return blockingStub.withDeadlineAfter(90, TimeUnit.SECONDS)
                    .finalizeTallyDecryption(request);
        } catch (StatusRuntimeException e) {
            log.error("finalizeTallyDecryption FAIL: session={}: {}", sessionId, e.getMessage());
            throw new RuntimeException("Tally finalize edilemedi: " + e.getMessage(), e);
        }
    }
}
