package com.cepsandik.cryptoengine.service

import com.cepsandik.electionservice.grpc.CreateJointKeyRequest
import com.cepsandik.electionservice.grpc.CreateJointKeyResponse
import com.cepsandik.electionservice.grpc.GuardianPublicKey
import electionguard.ballot.makeElectionConfig
import electionguard.ballot.protocolVersion
import electionguard.core.GroupContext
import electionguard.core.getSystemDate
import electionguard.core.productionGroup
import electionguard.json2.PublicKeysJson
import electionguard.json2.SchnorrProofJson
import electionguard.json2.`import`
import electionguard.keyceremony.KeyCeremonyResults
import electionguard.keyceremony.PublicKeys
import electionguard.util.ErrorMessages
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Distributed key ceremony — sunucu N adet `PublicKeysJson` alır, hiçbir
 * private state'i görmeden joint public key + ElectionInitialized + Manifest
 * üretir. Private polynomial katsayıları guardian'ların kendi cihazlarında
 * kalır; sunucu sadece Schnorr proof'ları doğrular (`PublicKeys.validate()`
 * KeyCeremonyResults içinde dolaylı olarak çalışır) ve joint key'i
 * coefficientCommitments[0]'ların çarpımı olarak hesaplar (KMP yapar).
 *
 * Mobile / Java tarafı için sözleşme:
 *   GuardianPublicKey.coefficient_proofs == JSON serialized List<SchnorrProofJson>
 *   GuardianPublicKey.public_key, .commitments alanları artık tek başına yeterli
 *   değil — KMP coefficient_proofs[i].public_key zaten commitment'ı taşıyor.
 *   (Birinci elemanın public_key'i guardian'ın ElGamal public key'idir.)
 */
@Service
class CreateJointKeyService {

    private val log = LoggerFactory.getLogger(javaClass)
    private val group: GroupContext = productionGroup()
    private val json = ElectionGuardSerde.json

    fun createJointKey(request: CreateJointKeyRequest): CreateJointKeyResponse {
        val n = request.numberOfGuardians
        val q = request.quorum
        require(n >= 1) { "numberOfGuardians >= 1 olmalı" }
        require(q in 1..n) { "quorum 1..$n aralığında olmalı" }
        require(request.publicKeysCount == n) {
            "numberOfGuardians=$n ama gelen public_keys sayısı ${request.publicKeysCount}"
        }

        log.info("createJointKey: election={}, N={}, Q={}, contests={}",
            request.electionId, n, q, request.contestsCount)

        // 1. Manifest + Config (sunucu tarafı, public bilgi)
        val manifest = ManifestBuildHelper.build(request.electionId, request.contestsList)
        val manifestBytes = ElectionGuardSerde.manifestToJson(manifest).encodeToByteArray()

        val config = makeElectionConfig(
            protocolVersion, group.constants, n, q, manifestBytes,
            chainConfirmationCodes = false,
            baux0 = "cepsandik".encodeToByteArray(),
            metadata = mapOf(
                "CreatedBy" to "CreateJointKeyService",
                "CreatedOn" to getSystemDate(),
                "ElectionId" to request.electionId,
                "Flow" to "distributed",
            ),
        )

        // 2. Her GuardianPublicKey proto'sundan KMP PublicKeys nesnesi inşa
        val errs = ErrorMessages("createJointKey/${request.electionId}")
        val publicKeysList: List<PublicKeys> = request.publicKeysList
            .mapIndexed { idx, proto -> importGuardianPublicKey(proto, idx + 1, errs.nested("g-$idx")) }

        if (errs.hasErrors()) {
            error("GuardianPublicKey import hataları: $errs")
        }

        // 2b. Faz 1.2 — EXPLICIT sunucu-taraflı Schnorr proof doğrulama.
        //     Önceki kod doğrulamayı KeyCeremonyResults içinde "dolaylı"
        //     bırakıyordu; geçersiz proof sessizce placeholder'a düşüp
        //     ceremony'i bozuyordu. Artık her guardian'ın TÜM coefficient
        //     Schnorr proof'ları açıkça doğrulanır; HERHANGİ biri geçersizse
        //     joint key ÜRETİLMEZ (fail-hard, detaylı gerekçe).
        val invalidReports = ArrayList<String>()
        publicKeysList.forEach { pk ->
            val vr = pk.validate()
            if (vr is com.github.michaelbull.result.Err) {
                invalidReports.add("guardian=${pk.guardianId} x=${pk.guardianXCoordinate}: ${vr.error}")
                log.error("Schnorr proof GEÇERSİZ: election={}, guardian={}, sebep={}",
                    request.electionId, pk.guardianId, vr.error)
            } else {
                log.info("Schnorr proof OK: election={}, guardian={}, x={}",
                    request.electionId, pk.guardianId, pk.guardianXCoordinate)
            }
        }
        if (invalidReports.isNotEmpty()) {
            error("Schnorr proof doğrulaması başarısız (${invalidReports.size}/" +
                "${publicKeysList.size} guardian) — joint key üretilmedi: " +
                invalidReports.joinToString(" | "))
        }

        // 3. KeyCeremonyResults: public-only joint key construction
        //    Constructor public; List<PublicKeys> tek argüman.
        val ceremonyResults = KeyCeremonyResults(publicKeysList)
        val electionInit = ceremonyResults.makeElectionInitialized(
            config,
            mapOf(
                "CreatedBy" to "CreateJointKeyService",
                "CreatedFrom" to "distributed-ceremony",
            ),
        )

        // 4. Response: composite context (init+config) + manifest + joint key hex
        val contextJson = ElectionGuardSerde.electionContextToJson(electionInit, config)
        val manifestJson = ElectionGuardSerde.manifestToJson(manifest)
        val jointPublicKeyHex = electionInit.jointPublicKey().toString()

        log.info("createJointKey OK: election={}, jointKey={}..., context={} bytes, manifest={} bytes",
            request.electionId, jointPublicKeyHex.take(40), contextJson.length, manifestJson.length)

        return CreateJointKeyResponse.newBuilder()
            .setElectionGuardContext(contextJson)
            .setJointPublicKey(jointPublicKeyHex)
            .setElectionManifest(manifestJson)
            .build()
    }

    /**
     * Proto `GuardianPublicKey`'i KMP `PublicKeys` nesnesine çevirir.
     *
     * Proto'da `guardian_x_coordinate` alanı yok — guardian'ların sıralı
     * (1..N) atandığını varsayıyoruz, çağıran taraf (Java GuardianService)
     * `findAllByElectionId` ile aldığı sırayı KORUMALI. Aksi takdirde
     * threshold decryption tarafında Lagrange interpolation yanlış kombinler.
     */
    private fun importGuardianPublicKey(
        proto: GuardianPublicKey,
        defaultXCoordinate: Int,
        errs: ErrorMessages,
    ): PublicKeys {
        // coefficient_proofs alanı zaten List<SchnorrProofJson> formatında JSON.
        val proofsJsonStr = proto.coefficientProofs
        if (proofsJsonStr.isBlank()) {
            errs.add("coefficient_proofs boş — guardian=${proto.guardianId}")
            return placeholder(defaultXCoordinate)
        }
        val schnorrProofs: List<SchnorrProofJson> = try {
            json.decodeFromString(proofsJsonStr)
        } catch (t: Throwable) {
            errs.add("coefficient_proofs JSON parse fail: ${t.message}")
            return placeholder(defaultXCoordinate)
        }
        if (schnorrProofs.size < 1) {
            errs.add("coefficient_proofs en az 1 eleman içermeli (Q taneye kadar)")
            return placeholder(defaultXCoordinate)
        }
        val pkJson = PublicKeysJson(
            guardianId = proto.guardianId,
            guardianXCoordinate = defaultXCoordinate,
            coefficientProofs = schnorrProofs,
        )
        return pkJson.`import`(group, errs)
            ?: run {
                errs.add("PublicKeysJson.import returned null")
                placeholder(defaultXCoordinate)
            }
    }

    /** Hata durumunda iskelet — errs zaten dolu olduğu için kullanılmaz. */
    private fun placeholder(x: Int): PublicKeys = PublicKeys("invalid", x, emptyList())
}
