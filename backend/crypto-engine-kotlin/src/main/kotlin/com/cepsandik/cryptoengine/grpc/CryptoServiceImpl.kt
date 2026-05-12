package com.cepsandik.cryptoengine.grpc

import com.cepsandik.cryptoengine.service.BallotValidationService
import com.cepsandik.cryptoengine.service.ElectionSetupService
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

    override fun createJointKey(
        request: CreateJointKeyRequest,
        responseObserver: StreamObserver<CreateJointKeyResponse>
    ) {
        log.info("createJointKey STUB called: election={}", request.electionId)
        responseObserver.onError(
            Status.UNIMPLEMENTED.withDescription("createJointKey — Faz 2 POC v2'de port edilecek").asRuntimeException()
        )
    }

    override fun decryptWithShares(
        request: DecryptWithSharesRequest,
        responseObserver: StreamObserver<TallyElectionResponse>
    ) {
        log.info("decryptWithShares STUB called: election={}", request.electionId)
        responseObserver.onError(
            Status.UNIMPLEMENTED.withDescription("decryptWithShares — Faz 2 POC v2'de port edilecek").asRuntimeException()
        )
    }
}
