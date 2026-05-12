package com.cepsandik.cryptoengine.service

import electionguard.ballot.ElectionConfig
import electionguard.ballot.ElectionInitialized
import electionguard.ballot.Manifest
import electionguard.json2.ElectionConfigJson
import electionguard.json2.ElectionInitializedJson
import electionguard.json2.ManifestJson
import electionguard.json2.publishJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * KMP nesnelerini JSON string'e çeviren helper'lar.
 *
 * Mimari karar: gRPC kontratımızdaki `election_guard_context: string` tek bir
 * field; ama KMP'de ElectionInitialized'ı parse etmek için ElectionConfig de
 * gerekli (`ElectionInitializedJson.import(group, config, errs)`). Bu yüzden
 * context field'ı composite bir JSON taşır:
 *   { "init": {...ElectionInitializedJson...}, "config": {...ElectionConfigJson...} }
 *
 * Not: composite için kendi @Serializable data class yazmak yerine JsonObject
 * tree yapıyoruz — KMP fat JAR'daki kotlinx-serialization-core sürümüyle bizim
 * @Serializable plugin'imiz çakışıyor (binary uyumsuzluğu).
 */
object ElectionGuardSerde {

    val json: Json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    data class ElectionContextBundle(
        val init: ElectionInitializedJson,
        val config: ElectionConfigJson,
    )

    fun manifestToJson(manifest: Manifest): String =
        json.encodeToString(manifest.publishJson())

    fun electionContextToJson(init: ElectionInitialized, config: ElectionConfig): String {
        val initObj = json.encodeToJsonElement(init.publishJson())
        val configObj = json.encodeToJsonElement(config.publishJson())
        val composite = buildJsonObject {
            put("init", initObj)
            put("config", configObj)
        }
        return json.encodeToString(composite)
    }

    fun parseElectionContext(jsonString: String): ElectionContextBundle {
        val root: JsonObject = json.parseToJsonElement(jsonString).jsonObject
        val initElement = root["init"]
            ?: error("electionGuardContext composite: 'init' alanı eksik")
        val configElement = root["config"]
            ?: error("electionGuardContext composite: 'config' alanı eksik")
        val initDto = json.decodeFromJsonElement<ElectionInitializedJson>(initElement)
        val configDto = json.decodeFromJsonElement<ElectionConfigJson>(configElement)
        return ElectionContextBundle(initDto, configDto)
    }

    fun parseManifest(jsonString: String): ManifestJson =
        json.decodeFromString<ManifestJson>(jsonString)

    // Helper: T tipindeki bir DTO'yu JsonElement'e çevir (reified)
    private inline fun <reified T> Json.encodeToJsonElement(value: T): kotlinx.serialization.json.JsonElement =
        this.parseToJsonElement(this.encodeToString(value))

    private inline fun <reified T> Json.decodeFromJsonElement(element: kotlinx.serialization.json.JsonElement): T =
        this.decodeFromString<T>(this.encodeToString(element))
}
