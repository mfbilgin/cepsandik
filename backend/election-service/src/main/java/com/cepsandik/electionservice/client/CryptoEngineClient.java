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
     * Dağıtık deşifre paylarını toplayıp sonucu açıklar.
     */
    public TallyElectionResponse decryptWithShares(
            String electionId,
            String context,
            String manifest,
            List<String> ballots,
            List<DecryptionShare> shares) {

        log.info("DecryptWithShares gRPC çağrısı: election={}, shares={}", electionId, shares.size());

        try {
            DecryptWithSharesRequest request = DecryptWithSharesRequest.newBuilder()
                    .setElectionId(electionId)
                    .setElectionGuardContext(context)
                    .setElectionManifest(manifest)
                    .addAllCiphertextBallots(ballots)
                    .addAllShares(shares)
                    .build();

            return blockingStub
                    .withDeadlineAfter(120, TimeUnit.SECONDS)
                    .decryptWithShares(request);

        } catch (StatusRuntimeException e) {
            log.error("DecryptWithShares gRPC hatası: {}", e.getMessage());
            throw new RuntimeException("Deşifre işlemi başarısız: " + e.getMessage(), e);
        }
    }
}
