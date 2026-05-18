package com.cepsandik.cryptoengine.grpc

import com.cepsandik.cryptoengine.service.BallotValidationService
import com.cepsandik.cryptoengine.service.CreateJointKeyService
import com.cepsandik.cryptoengine.service.DistributedTallySessionService
import com.cepsandik.cryptoengine.service.ElectionSetupService
import com.cepsandik.cryptoengine.service.SpoiledBallotService
import com.cepsandik.cryptoengine.service.TallyDecryptionService
import com.cepsandik.electionservice.grpc.CryptoServiceGrpc
import com.cepsandik.electionservice.grpc.SetupElectionRequest
import com.cepsandik.electionservice.grpc.SetupElectionResponse
import com.cepsandik.electionservice.grpc.ValidateBallotRequest
import com.cepsandik.electionservice.grpc.ValidateBallotResponse
import com.cepsandik.electionservice.grpc.TallyElectionRequest
import com.cepsandik.electionservice.grpc.TallyElectionResponse
import com.cepsandik.electionservice.grpc.CreateJointKeyRequest
import com.cepsandik.electionservice.grpc.CreateJointKeyResponse
import com.cepsandik.electionservice.grpc.DecryptWithSharesRequest
import com.cepsandik.electionservice.grpc.FinalizeTallyDecryptionRequest
import com.cepsandik.electionservice.grpc.GetChallengesRequest
import com.cepsandik.electionservice.grpc.GetChallengesResponse
import com.cepsandik.electionservice.grpc.SessionAckResponse
import com.cepsandik.electionservice.grpc.StartTallyDecryptionSessionRequest
import com.cepsandik.electionservice.grpc.StartTallyDecryptionSessionResponse
import com.cepsandik.electionservice.grpc.SubmitChallengeResponseRequest
import com.cepsandik.electionservice.grpc.SubmitPartialDecryptionRequest
import com.cepsandik.electionservice.grpc.VerifySpoiledBallotRequest
import com.cepsandik.electionservice.grpc.VerifySpoiledBallotResponse
import io.grpc.Status
import io.grpc.stub.StreamObserver
import net.devh.boot.grpc.server.service.GrpcService
import org.slf4j.LoggerFactory

/**
 * gRPC CryptoService — POC v1 stub.
 *
 * Tüm RPC'ler şu an `UNIMPLEMENTED` dönüyor; tek istisna `validateBallot`,
 * basit bir sanity check yapıp valid=true döner ki gRPC zincirinin uçtan uca
 * çalıştığını grpcurl ile test edebilelim.
 *
 * Faz 2 — POC v2: KMP API'leri (CiphertextBallot, isValidEncryption, vs.) ile
 * gerçek implementasyon eklenecek.
 */
@GrpcService
class CryptoServiceImpl(
    private val electionSetupService: ElectionSetupService,
    private val ballotValidationService: BallotValidationService,
    private val tallyDecryptionService: TallyDecryptionService,
    private val createJointKeyService: CreateJointKeyService,
    private val distributedTallySessionService: DistributedTallySessionService,
    private val spoiledBallotService: SpoiledBallotService,
) : CryptoServiceGrpc.CryptoServiceImplBase() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun setupElection(
        request: SetupElectionRequest,
        responseObserver: StreamObserver<SetupElectionResponse>
    ) {
        try {
            val response = electionSetupService.setupElection(request)
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (t: Throwable) {
            log.error("setupElection FAILED: election={}", request.electionId, t)
            responseObserver.onError(
                Status.INTERNAL.withDescription("setupElection: ${t.message}").asRuntimeException()
            )
        }
    }

    override fun validateBallot(
        request: ValidateBallotRequest,
        responseObserver: StreamObserver<ValidateBallotResponse>
    ) {
        try {
            val response = ballotValidationService.validate(request)
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (t: Throwable) {
            log.error("validateBallot FAILED: election={}, ballot={}", request.electionId, request.ballotId, t)
            responseObserver.onError(
                Status.INTERNAL.withDescription("validateBallot: ${t.message}").asRuntimeException()
            )
        }
    }

    override fun tallyElection(
        request: TallyElectionRequest,
        responseObserver: StreamObserver<TallyElectionResponse>
    ) {
        try {
            val response = tallyDecryptionService.tally(request)
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (t: Throwable) {
            log.error("tallyElection FAILED: election={}", request.electionId, t)
            responseObserver.onError(
                Status.INTERNAL.withDescription("tallyElection: ${t.message}").asRuntimeException()
            )
        }
    }

    override fun verifySpoiledBallot(
        request: VerifySpoiledBallotRequest,
        responseObserver: StreamObserver<VerifySpoiledBallotResponse>
    ) {
        try {
            val response = spoiledBallotService.verifySpoiled(request)
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (t: Throwable) {
            log.error("verifySpoiledBallot FAILED: election={}", request.electionId, t)
            responseObserver.onError(
                Status.INTERNAL.withDescription("verifySpoiledBallot: ${t.message}").asRuntimeException()
            )
        }
    }

    override fun createJointKey(
        request: CreateJointKeyRequest,
        responseObserver: StreamObserver<CreateJointKeyResponse>
    ) {
        try {
            val response = createJointKeyService.createJointKey(request)
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (t: Throwable) {
            log.error("createJointKey FAILED: election={}", request.electionId, t)
            responseObserver.onError(
                Status.INTERNAL.withDescription("createJointKey: ${t.message}").asRuntimeException()
            )
        }
    }

    override fun decryptWithShares(
        request: DecryptWithSharesRequest,
        responseObserver: StreamObserver<TallyElectionResponse>
    ) {
        log.warn("decryptWithShares DEPRECATED: election={} — gerçek E2E-V için 5-stage akışı kullan", request.electionId)
        responseObserver.onError(
            Status.UNIMPLEMENTED.withDescription("decryptWithShares deprecated; StartTallyDecryptionSession ile başlayan 5-stage akışı kullan").asRuntimeException()
        )
    }

    // ===== Sprint 5.C.2 — 3-round distributed decryption =====

    override fun startTallyDecryptionSession(
        request: StartTallyDecryptionSessionRequest,
        responseObserver: StreamObserver<StartTallyDecryptionSessionResponse>
    ) {
        try {
            val response = distributedTallySessionService.startSession(request)
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (t: Throwable) {
            log.error("startTallyDecryptionSession FAIL: election={}", request.electionId, t)
            responseObserver.onError(
                Status.INTERNAL.withDescription("startSession: ${t.message}").asRuntimeException()
            )
        }
    }

    override fun submitPartialDecryption(
        request: SubmitPartialDecryptionRequest,
        responseObserver: StreamObserver<SessionAckResponse>
    ) {
        try {
            val response = distributedTallySessionService.submitPartialDecryption(request)
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (t: Throwable) {
            log.error("submitPartialDecryption FAIL: session={}, guardian={}", request.sessionId, request.guardianId, t)
            responseObserver.onError(
                Status.INTERNAL.withDescription("submitPartialDecryption: ${t.message}").asRuntimeException()
            )
        }
    }

    override fun getChallenges(
        request: GetChallengesRequest,
        responseObserver: StreamObserver<GetChallengesResponse>
    ) {
        try {
            val response = distributedTallySessionService.getChallenges(request)
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (t: Throwable) {
            log.error("getChallenges FAIL: session={}, guardian={}", request.sessionId, request.guardianId, t)
            responseObserver.onError(
                Status.INTERNAL.withDescription("getChallenges: ${t.message}").asRuntimeException()
            )
        }
    }

    override fun submitChallengeResponse(
        request: SubmitChallengeResponseRequest,
        responseObserver: StreamObserver<SessionAckResponse>
    ) {
        try {
            val response = distributedTallySessionService.submitChallengeResponse(request)
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (t: Throwable) {
            log.error("submitChallengeResponse FAIL: session={}, guardian={}", request.sessionId, request.guardianId, t)
            responseObserver.onError(
                Status.INTERNAL.withDescription("submitChallengeResponse: ${t.message}").asRuntimeException()
            )
        }
    }

    override fun finalizeTallyDecryption(
        request: FinalizeTallyDecryptionRequest,
        responseObserver: StreamObserver<TallyElectionResponse>
    ) {
        try {
            val response = distributedTallySessionService.finalizeTally(request)
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (t: Throwable) {
            log.error("finalizeTallyDecryption FAIL: session={}", request.sessionId, t)
            responseObserver.onError(
                Status.INTERNAL.withDescription("finalizeTallyDecryption: ${t.message}").asRuntimeException()
            )
        }
    }
}
