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
            List<ContestInfo> contests) {

        log.info("SetupElection gRPC çağrısı: election={}, N={}, Q={}",
                electionId, numberOfGuardians, quorum);

        try {
            SetupElectionRequest request = SetupElectionRequest.newBuilder()
                    .setElectionId(electionId)
                    .setNumberOfGuardians(numberOfGuardians)
                    .setQuorum(quorum)
                    .addAllContests(contests)
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

    // ==================== Encrypt Ballot ====================

    /**
     * Tek bir oyu şifreler.
     * Context ve manifest PostgreSQL'den çekilip gönderilir (stateless).
     */
    public EncryptBallotResponse encryptBallot(
            String electionId,
            String electionGuardContext,
            String electionManifest,
            byte[] rsaEncryptedPayload,
            String ballotId,
            String ballotStyleId) {

        log.info("EncryptBallot gRPC çağrısı: election={}, ballot={}",
                electionId, ballotId);

        try {
            EncryptBallotRequest.Builder requestBuilder = EncryptBallotRequest.newBuilder()
                    .setElectionId(electionId)
                    .setElectionGuardContext(electionGuardContext)
                    .setElectionManifest(electionManifest)
                    .setBallotId(ballotId)
                    .setBallotStyleId(ballotStyleId != null ? ballotStyleId : "ballot-style-1");

            if (rsaEncryptedPayload != null) {
                requestBuilder.setRsaEncryptedPayload(
                        com.google.protobuf.ByteString.copyFrom(rsaEncryptedPayload));
            }

            EncryptBallotResponse response = blockingStub
                    .withDeadlineAfter(30, TimeUnit.SECONDS)
                    .encryptBallot(requestBuilder.build());

            log.info("EncryptBallot başarılı: ballot={}, tracking_code={}",
                    ballotId, response.getTrackingCode().substring(0, Math.min(16, response.getTrackingCode().length())));

            return response;

        } catch (StatusRuntimeException e) {
            log.error("EncryptBallot gRPC hatası: status={}, desc={}",
                    e.getStatus().getCode(), e.getStatus().getDescription());
            throw new RuntimeException("Crypto-Engine EncryptBallot hatası: " + e.getMessage(), e);
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

    // ==================== Get Public Key ====================

    /**
     * Crypto-Engine'in RSA public key'ini döner.
     */
    public String getPublicKey() {
        log.info("GetPublicKey gRPC çağrısı");

        try {
            GetPublicKeyResponse response = blockingStub
                    .withDeadlineAfter(10, TimeUnit.SECONDS)
                    .getPublicKey(GetPublicKeyRequest.newBuilder().build());

            return response.getRsaPublicKeyPem();

        } catch (StatusRuntimeException e) {
            log.error("GetPublicKey gRPC hatası: status={}, desc={}",
                    e.getStatus().getCode(), e.getStatus().getDescription());
            throw new RuntimeException("Crypto-Engine GetPublicKey hatası: " + e.getMessage(), e);
        }
    }
}
