package com.stormgbca.emulator.data

import android.content.Context
import android.os.Build
import com.stormgbca.emulator.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import ru.stormgames.gbca.NativeCore
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class RaUserProfile(
    val connected: Boolean = false,
    val username: String = "",
    val points: Int = 0,
    val softcorePoints: Int = 0,
    val rank: Int = 0,
    val motto: String = "",
    val avatarUrl: String = "",
    val unreadMessages: Int = 0,
    val error: String = "",
)

data class RaLoginResult(
    val success: Boolean,
    val profile: RaUserProfile = RaUserProfile(),
    val token: String = "",
    val error: String = "",
)

data class RaConnectionDiagnostics(
    val serverReachable: Boolean,
    val latencyMs: Long,
    val authenticated: Boolean,
    val username: String,
    val message: String,
)

data class RaAchievementBadge(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val badgeName: String = "",
    val memAddr: String = "",
    val flags: Int = 3,
    val type: String = "",
    val isUnofficial: Boolean = flags != 3,
    val unlockedCasual: Boolean = false,
    val unlocked: Boolean = unlockedCasual,
    val unlockedHardcore: Boolean = false,
) {
    val badgeUrl: String
        get() = if (badgeName.isNotBlank()) {
            val suffix = if (unlocked || unlockedCasual || unlockedHardcore) "" else "_lock"
            "https://media.retroachievements.org/Badge/${badgeName}${suffix}.png"
        } else ""
}

typealias RaAchievement = RaAchievementBadge

data class RaLeaderboardEntry(
    val rank: Int,
    val user: String,
    val scoreFormatted: String,
)

data class RaLeaderboard(
    val id: Int,
    val title: String,
    val description: String,
    val mem: String = "",
    val format: String = "",
    val lowerIsBetter: Boolean = false,
    val hidden: Boolean = false,
    val entries: List<RaLeaderboardEntry> = emptyList(),
)

data class RaGameSession(
    val gameId: Int,
    val gameTitle: String,
    val gameIconUrl: String,
    val boxArtUrl: String = "",
    val richPresencePatch: String = "",
    val gameHash: String = "",
    val achievements: List<RaAchievementBadge> = emptyList(),
    val leaderboards: List<RaLeaderboard> = emptyList(),
) {
    val totalCount: Int get() = achievements.size
    val unlockedCount: Int get() = achievements.count { it.unlocked || it.unlockedCasual || it.unlockedHardcore }
    val hardcoreUnlockedCount: Int get() = achievements.count { it.unlockedHardcore }
    val casualOnlyCount: Int get() = achievements.count { (it.unlocked || it.unlockedCasual) && !it.unlockedHardcore }
    val softcoreOnlyCount: Int get() = casualOnlyCount
    val missableCount: Int get() = achievements.count { it.type.equals("missable", ignoreCase = true) }
    val totalPoints: Int get() = achievements.sumOf { it.points }
    val earnedPoints: Int get() = achievements.filter { it.unlocked || it.unlockedCasual || it.unlockedHardcore }.sumOf { it.points }
    val hardcoreEarnedPoints: Int get() = achievements.filter { it.unlockedHardcore }.sumOf { it.points }
    val completionPercent: Int get() = if (totalCount == 0) 0 else ((unlockedCount * 100) / totalCount)
    val hardcorePercent: Int get() = if (totalCount == 0) 0 else ((hardcoreUnlockedCount * 100) / totalCount)
    val webUrl: String get() = "https://retroachievements.org/game/$gameId"
}

object RetroAchievementsClient {
    private const val CONNECT_URL = "https://retroachievements.org/dorequest.php"
    private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()

    @Volatile
    var currentCoreName: String? = null

    private val gameIdCache = ConcurrentHashMap<String, Int>()
    private val boxArtCache = ConcurrentHashMap<String, String>()
    private val queueLock = Any()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .build()
    }

    /**
     * RetroAchievements Section C compliant 3-segment User-Agent:
     * STORM-GBCA/<version> (Android <release>) <core>/<coreVersion>
     */
    fun buildUserAgent(activeCoreName: String? = currentCoreName): String {
        val cleanVersion = BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V")
        val coreSegment = if (activeCoreName?.contains("sameboy", ignoreCase = true) == true) {
            "sameboy/1.0.5"
        } else {
            "mgba/0.11.0"
        }
        return "STORM-GBCA/$cleanVersion (Android ${Build.VERSION.RELEASE}) $coreSegment"
    }

    fun isLoggedIn(): Boolean =
        GbcaPrefs.isRaEnabled() && GbcaPrefs.getRaUsername().isNotBlank() && GbcaPrefs.getRaToken().isNotBlank()

    fun isHardcore(): Boolean = GbcaPrefs.isRaHardcore()

    fun isLeaderboardPopupsEnabled(): Boolean = GbcaPrefs.isLeaderboardPopupsEnabled()

    fun setLeaderboardPopupsEnabled(enabled: Boolean) {
        GbcaPrefs.setLeaderboardPopupsEnabled(enabled)
    }

    private data class HttpPostOutcome(
        val reachable: Boolean,
        val httpCode: Int,
        val json: JSONObject?,
    )

    private fun executeNativePost(reqPair: Array<String>?, activeCoreName: String? = currentCoreName): HttpPostOutcome {
        if (reqPair == null || reqPair.size < 2) return HttpPostOutcome(false, 0, null)
        val url = reqPair[0].ifBlank { CONNECT_URL }
        val postData = reqPair[1]
        val req = Request.Builder()
            .url(url)
            .post(postData.toRequestBody(FORM_MEDIA_TYPE))
            .header("User-Agent", buildUserAgent(activeCoreName))
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                val parsed = runCatching { JSONObject(text) }.getOrNull()
                HttpPostOutcome(resp.isSuccessful, resp.code, parsed)
            }
        }.getOrElse {
            HttpPostOutcome(false, 0, null)
        }
    }

    private fun enqueuePendingItem(
        kind: String,
        id: Int,
        hardcore: Boolean,
        score: Int,
        hash: String,
    ) {
        synchronized(queueLock) {
            val arr = runCatching { JSONArray(GbcaPrefs.getRaPendingQueueJson()) }.getOrDefault(JSONArray())
            // Avoid exact duplicate queued item
            for (i in 0 until arr.length()) {
                val existing = arr.optJSONObject(i) ?: continue
                if (existing.optString("kind") == kind &&
                    existing.optInt("id") == id &&
                    existing.optBoolean("hardcore") == hardcore &&
                    existing.optInt("score") == score
                ) {
                    return
                }
            }
            arr.put(
                JSONObject().apply {
                    put("kind", kind)
                    put("id", id)
                    put("hardcore", hardcore)
                    put("score", score)
                    put("hash", hash)
                    put("timestamp", System.currentTimeMillis())
                },
            )
            GbcaPrefs.saveRaPendingQueueJson(arr.toString())
        }
    }

    suspend fun verifyRomHasAchievements(raHash: String): Boolean = withContext(Dispatchers.IO) {
        val clean = raHash.trim().lowercase()
        if (clean.length != 32) return@withContext false
        val persisted = GbcaPrefs.getCachedRaGameIds()
        persisted[clean]?.let { return@withContext it > 0 }
        val id = resolveGameId(clean)
        persisted[clean] = id
        GbcaPrefs.saveCachedRaGameIds(persisted)
        id > 0
    }

    fun getCachedProfile(): RaUserProfile {
        val u = GbcaPrefs.getRaUsername()
        val t = GbcaPrefs.getRaToken()
        if (u.isBlank() || t.isBlank()) return RaUserProfile(connected = false)
        return RaUserProfile(
            connected = true,
            username = u,
            points = GbcaPrefs.getRaPoints(),
            softcorePoints = GbcaPrefs.getRaSoftcorePoints(),
            rank = GbcaPrefs.getRaRank(),
            motto = GbcaPrefs.getRaMotto(),
            avatarUrl = "https://media.retroachievements.org/UserPic/${URLEncoder.encode(u, "UTF-8")}.png",
        )
    }

    suspend fun login(username: String, password: String): RaLoginResult = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        if (cleanUser.isBlank() || password.isBlank()) {
            return@withContext RaLoginResult(false, error = "Username and password required")
        }
        val reqPair = NativeCore.raBuildLoginRequest(cleanUser, password, "")
        val outcome = executeNativePost(reqPair)
        val obj = outcome.json
        if (obj == null) {
            return@withContext RaLoginResult(
                false,
                error = if (outcome.httpCode > 0) "HTTP ${outcome.httpCode}" else "Network error",
            )
        }
        if (obj.optBoolean("Success", false)) {
            val user = obj.optString("User", cleanUser)
            val token = obj.optString("Token", "")
            val score = obj.optInt("Score", 0)
            val softScore = obj.optInt("SoftcoreScore", 0)
            val messages = obj.optInt("Messages", 0)
            GbcaPrefs.setRaCredentials(user, token)
            GbcaPrefs.setRaProfileStats(score, softScore, GbcaPrefs.getRaRank(), GbcaPrefs.getRaMotto())
            flushPendingQueue()
            val profile = RaUserProfile(
                connected = true,
                username = user,
                points = score,
                softcorePoints = softScore,
                rank = GbcaPrefs.getRaRank(),
                motto = GbcaPrefs.getRaMotto(),
                avatarUrl = "https://media.retroachievements.org/UserPic/${URLEncoder.encode(user, "UTF-8")}.png",
                unreadMessages = messages,
            )
            RaLoginResult(true, profile = profile, token = token)
        } else {
            val err = obj.optString("Error", "Invalid credentials")
            RaLoginResult(false, error = err)
        }
    }

    suspend fun refreshProfileWithToken(): RaUserProfile = withContext(Dispatchers.IO) {
        val user = GbcaPrefs.getRaUsername()
        val token = GbcaPrefs.getRaToken()
        if (user.isBlank() || token.isBlank()) return@withContext RaUserProfile(connected = false)

        val outcome = executeNativePost(NativeCore.raBuildLoginRequest(user, "", token))
        val obj = outcome.json ?: return@withContext getCachedProfile().copy(error = "Offline")
        if (obj.optBoolean("Success", false)) {
            val realUser = obj.optString("User", user)
            val score = obj.optInt("Score", GbcaPrefs.getRaPoints())
            val softScore = obj.optInt("SoftcoreScore", GbcaPrefs.getRaSoftcorePoints())
            val messages = obj.optInt("Messages", 0)
            GbcaPrefs.setRaProfileStats(score, softScore, GbcaPrefs.getRaRank(), GbcaPrefs.getRaMotto())
            RaUserProfile(
                connected = true,
                username = realUser,
                points = score,
                softcorePoints = softScore,
                rank = GbcaPrefs.getRaRank(),
                motto = GbcaPrefs.getRaMotto(),
                avatarUrl = "https://media.retroachievements.org/UserPic/${URLEncoder.encode(realUser, "UTF-8")}.png",
                unreadMessages = messages,
            )
        } else {
            getCachedProfile().copy(error = obj.optString("Error", "Session expired"))
        }
    }

    suspend fun testConnection(): RaConnectionDiagnostics = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val user = GbcaPrefs.getRaUsername()
        val token = GbcaPrefs.getRaToken()
        runCatching {
            val req = if (user.isNotBlank() && token.isNotBlank()) {
                val reqPair = NativeCore.raBuildLoginRequest(user, "", token)
                val url = reqPair?.getOrNull(0)?.ifBlank { CONNECT_URL } ?: CONNECT_URL
                val postData = reqPair?.getOrNull(1) ?: "r=login2&u=${URLEncoder.encode(user, "UTF-8")}&t=${URLEncoder.encode(token, "UTF-8")}"
                Request.Builder()
                    .url(url)
                    .post(postData.toRequestBody(FORM_MEDIA_TYPE))
                    .header("User-Agent", buildUserAgent())
                    .build()
            } else {
                Request.Builder()
                    .url("$CONNECT_URL?r=latestclient&e=0")
                    .header("User-Agent", buildUserAgent())
                    .build()
            }
            client.newCall(req).execute().use { resp ->
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                val bodyStr = resp.body?.string() ?: "{}"
                val obj = runCatching { JSONObject(bodyStr) }.getOrDefault(JSONObject())
                val ok = resp.isSuccessful
                val authed = user.isNotBlank() && token.isNotBlank() && obj.optBoolean("Success", false)
                val msg = when {
                    !ok -> "HTTP ${resp.code}"
                    authed -> "OK - Authenticated as $user (${elapsed} ms)"
                    user.isNotBlank() -> obj.optString("Error", "Token verification failed")
                    else -> "Server reachable (${elapsed} ms) - Not signed in"
                }
                RaConnectionDiagnostics(ok, elapsed, authed, user, msg)
            }
        }.getOrElse { ex ->
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            RaConnectionDiagnostics(false, elapsed, false, user, ex.message ?: "Connection failed")
        }
    }

    suspend fun startSession(gameId: Int, gameHash: String, hardcore: Boolean): Boolean = withContext(Dispatchers.IO) {
        val user = GbcaPrefs.getRaUsername()
        val token = GbcaPrefs.getRaToken()
        if (user.isBlank() || token.isBlank() || gameId <= 0) return@withContext false
        val reqPair = NativeCore.raBuildStartSessionRequest(user, token, gameId, gameHash, hardcore)
        val outcome = executeNativePost(reqPair)
        val ok = outcome.reachable && (outcome.json?.optBoolean("Success", false) == true)
        if (ok) {
            flushPendingQueue()
        }
        ok
    }

    suspend fun sendPing(
        gameId: Int,
        gameHash: String,
        richPresence: String,
        hardcore: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val user = GbcaPrefs.getRaUsername()
        val token = GbcaPrefs.getRaToken()
        if (user.isBlank() || token.isBlank() || gameId <= 0) return@withContext false
        val reqPair = NativeCore.raBuildPingRequest(user, token, gameId, richPresence, gameHash, hardcore)
        val outcome = executeNativePost(reqPair)
        val ok = outcome.reachable && (outcome.json?.optBoolean("Success", false) == true)
        if (ok) {
            flushPendingQueue()
        }
        ok
    }

    private fun pendingItemFingerprint(item: JSONObject): String =
        "${item.optString("kind", "achievement")}|${item.optInt("id", 0)}|${item.optBoolean("hardcore", false)}|${item.optInt("score", 0)}|${item.optString("hash", "")}|${item.optLong("timestamp", 0L)}"

    suspend fun awardAchievement(achId: Int, hardcore: Boolean, gameHash: String): Boolean =
        withContext(Dispatchers.IO) {
            val user = GbcaPrefs.getRaUsername()
            val token = GbcaPrefs.getRaToken()
            if (user.isBlank() || token.isBlank() || achId <= 0) return@withContext false

            val reqPair = NativeCore.raBuildAwardRequest(user, token, achId, hardcore, gameHash)
            val outcome = executeNativePost(reqPair)
            val json = outcome.json
            if (outcome.reachable && json != null) {
                if (json.optBoolean("Success", false)) {
                    val newScore = json.optInt("Score", -1)
                    val newSoftScore = json.optInt("SoftcoreScore", -1)
                    if (newScore >= 0 || newSoftScore >= 0) {
                        GbcaPrefs.setRaProfileStats(
                            if (newScore >= 0) newScore else GbcaPrefs.getRaPoints(),
                            if (newSoftScore >= 0) newSoftScore else GbcaPrefs.getRaSoftcorePoints(),
                            GbcaPrefs.getRaRank(),
                            GbcaPrefs.getRaMotto(),
                        )
                    }
                    return@withContext true
                }
                // Explicit non-transient rejection from RA (e.g. already unlocked)
                val errMsg = if (!json.isNull("Error")) json.optString("Error", "") else ""
                if (outcome.httpCode in 200..499 && outcome.httpCode != 429 && errMsg.isNotBlank()) {
                    return@withContext false
                }
            }

            // Offline or transient HTTP failure: persist to ra_pending_queue
            enqueuePendingItem("achievement", achId, hardcore, 0, gameHash)
            false
        }

    suspend fun submitLeaderboard(
        lboardId: Int,
        score: Int,
        gameHash: String,
        hardcore: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        // RA standard policy: leaderboards are only submitted in Hardcore mode
        if (!hardcore) return@withContext false
        val user = GbcaPrefs.getRaUsername()
        val token = GbcaPrefs.getRaToken()
        if (user.isBlank() || token.isBlank() || lboardId <= 0) return@withContext false

        val reqPair = NativeCore.raBuildLboardRequest(user, token, lboardId, score, gameHash)
        val outcome = executeNativePost(reqPair)
        val json = outcome.json
        if (outcome.reachable && json != null) {
            if (json.optBoolean("Success", false)) {
                return@withContext true
            }
            val errMsg = if (!json.isNull("Error")) json.optString("Error", "") else ""
            if (outcome.httpCode in 200..499 && outcome.httpCode != 429 && errMsg.isNotBlank()) {
                return@withContext false
            }
        }

        enqueuePendingItem("leaderboard", lboardId, true, score, gameHash)
        false
    }

    suspend fun flushPendingQueue(): Int = withContext(Dispatchers.IO) {
        val user = GbcaPrefs.getRaUsername()
        val token = GbcaPrefs.getRaToken()
        if (user.isBlank() || token.isBlank()) return@withContext 0

        val snapshotItems: List<JSONObject> = synchronized(queueLock) {
            val arr = runCatching { JSONArray(GbcaPrefs.getRaPendingQueueJson()) }.getOrDefault(JSONArray())
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        }
        if (snapshotItems.isEmpty()) return@withContext 0

        val completedFingerprints = HashSet<String>()
        var flushed = 0
        for (item in snapshotItems) {
            val fp = pendingItemFingerprint(item)
            val kind = item.optString("kind", "achievement")
            val id = item.optInt("id", 0)
            val hardcore = item.optBoolean("hardcore", false)
            val score = item.optInt("score", 0)
            val hash = item.optString("hash", "")
            if (id <= 0 || (kind == "leaderboard" && !hardcore)) {
                completedFingerprints.add(fp)
                continue
            }

            val reqPair = when (kind) {
                "leaderboard" -> NativeCore.raBuildLboardRequest(user, token, id, score, hash)
                else -> NativeCore.raBuildAwardRequest(user, token, id, hardcore, hash)
            }

            val outcome = executeNativePost(reqPair)
            val json = outcome.json
            val isSuccess = outcome.reachable && json != null && json.optBoolean("Success", false)
            val errMsg = json?.takeIf { !it.isNull("Error") }?.optString("Error", "").orEmpty()
            val isAlreadyUnlocked = outcome.reachable && json != null &&
                errMsg.contains("already unlocked", ignoreCase = true)
            if (isSuccess || isAlreadyUnlocked) {
                completedFingerprints.add(fp)
                if (isSuccess) flushed++
            }
        }

        if (completedFingerprints.isNotEmpty()) {
            synchronized(queueLock) {
                val currentArr = runCatching { JSONArray(GbcaPrefs.getRaPendingQueueJson()) }.getOrDefault(JSONArray())
                val remaining = JSONArray()
                for (i in 0 until currentArr.length()) {
                    val curItem = currentArr.optJSONObject(i) ?: continue
                    if (!completedFingerprints.contains(pendingItemFingerprint(curItem))) {
                        remaining.put(curItem)
                    }
                }
                GbcaPrefs.saveRaPendingQueueJson(remaining.toString())
            }
        }
        flushed
    }

    suspend fun syncPendingSubmissions(): Int = flushPendingQueue()

    /**
     * Resolves official RetroAchievements game icon/boxart URL by MD5/RA hash for the independent RA cover toggle.
     */
    suspend fun resolveGameBoxArtUrl(@Suppress("UNUSED_PARAMETER") context: Context, raHash: String): String? =
        withContext(Dispatchers.IO) {
            if (raHash.isBlank()) return@withContext null
            boxArtCache[raHash]?.let { return@withContext it.ifBlank { null } }

            val user = GbcaPrefs.getRaUsername()
            val token = GbcaPrefs.getRaToken()
            val gameId = resolveGameId(raHash)
            if (gameId <= 0) {
                boxArtCache[raHash] = ""
                return@withContext null
            }

            if (user.isNotBlank() && token.isNotBlank()) {
                val body = FormBody.Builder()
                    .add("r", "patch")
                    .add("u", user)
                    .add("t", token)
                    .add("g", gameId.toString())
                    .build()
                val req = Request.Builder()
                    .url(CONNECT_URL)
                    .post(body)
                    .header("User-Agent", buildUserAgent())
                    .build()
                val url = runCatching {
                    client.newCall(req).execute().use { resp ->
                        val patch = JSONObject(resp.body?.string() ?: "{}").optJSONObject("PatchData")
                        patch?.optString("ImageBoxArtURL", "")?.ifBlank {
                            patch.optString("ImageIconURL", "")
                        }?.ifBlank {
                            val rel = patch.optString("ImageIcon", "")
                            if (rel.isNotBlank()) "https://media.retroachievements.org$rel" else ""
                        }
                    }
                }.getOrNull()
                if (!url.isNullOrBlank()) {
                    boxArtCache[raHash] = url
                    return@withContext url
                }
            }
            boxArtCache[raHash] = ""
            null
        }

    private fun resolveGameId(raHash: String): Int {
        gameIdCache[raHash]?.let { return it }
        val body = FormBody.Builder()
            .add("r", "gameid")
            .add("m", raHash)
            .build()
        val req = Request.Builder()
            .url(CONNECT_URL)
            .post(body)
            .header("User-Agent", buildUserAgent())
            .build()
        val id = runCatching {
            client.newCall(req).execute().use { resp ->
                JSONObject(resp.body?.string() ?: "{}").optInt("GameID", 0)
            }
        }.getOrDefault(0)
        if (id > 0) gameIdCache[raHash] = id
        return id
    }

    suspend fun lookupGameSession(raHash: String): RaGameSession? = withContext(Dispatchers.IO) {
        val user = GbcaPrefs.getRaUsername()
        val token = GbcaPrefs.getRaToken()
        if (user.isBlank() || token.isBlank() || raHash.isBlank()) return@withContext null

        val gameId = resolveGameId(raHash)
        if (gameId <= 0) return@withContext null

        val casualUnlocks = fetchUnlockIds(user, token, gameId, hardcore = false)
        val hardUnlocks = fetchUnlockIds(user, token, gameId, hardcore = true)

        val patchBody = FormBody.Builder()
            .add("r", "patch")
            .add("u", user)
            .add("t", token)
            .add("g", gameId.toString())
            .build()
        val patchReq = Request.Builder()
            .url(CONNECT_URL)
            .post(patchBody)
            .header("User-Agent", buildUserAgent())
            .build()

        runCatching {
            client.newCall(patchReq).execute().use { resp ->
                val root = JSONObject(resp.body?.string() ?: "{}")
                if (!root.optBoolean("Success", false)) return@use null
                val patchData = root.optJSONObject("PatchData") ?: return@use null
                val title = patchData.optString("Title", "RetroAchievements #$gameId")
                val iconUrl = patchData.optString("ImageIconURL", "").ifBlank {
                    val iconName = patchData.optString("ImageIcon", "")
                    if (iconName.isNotBlank()) "https://media.retroachievements.org$iconName" else ""
                }
                val boxArtUrl = patchData.optString("ImageBoxArtURL", "").ifBlank { iconUrl }
                val rp = patchData.optString("RichPresencePatch", "")
                val arr = patchData.optJSONArray("Achievements")
                val includeUnofficial = GbcaPrefs.isRaUnofficial()
                val list = mutableListOf<RaAchievementBadge>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val a = arr.optJSONObject(i) ?: continue
                        val flags = a.optInt("Flags", 3)
                        val unofficial = flags != 3
                        if (unofficial && !includeUnofficial) continue
                        val id = a.optInt("ID", 0)
                        val isHard = hardUnlocks.contains(id)
                        val isCasual = isHard || casualUnlocks.contains(id)
                        list.add(
                            RaAchievementBadge(
                                id = id,
                                title = a.optString("Title", ""),
                                description = a.optString("Description", ""),
                                points = a.optInt("Points", 0),
                                badgeName = a.optString("BadgeName", ""),
                                memAddr = a.optString("MemAddr", ""),
                                flags = flags,
                                type = a.optString("Type", ""),
                                isUnofficial = unofficial,
                                unlockedCasual = isCasual,
                                unlocked = isCasual,
                                unlockedHardcore = isHard,
                            ),
                        )
                    }
                }

                val lbs = mutableListOf<RaLeaderboard>()
                val lbArr = patchData.optJSONArray("Leaderboards")
                if (lbArr != null) {
                    for (i in 0 until lbArr.length()) {
                        val lb = lbArr.optJSONObject(i) ?: continue
                        lbs.add(
                            RaLeaderboard(
                                id = lb.optInt("ID", 0),
                                title = lb.optString("Title", ""),
                                description = lb.optString("Description", ""),
                                mem = lb.optString("Mem", ""),
                                format = lb.optString("Format", ""),
                                lowerIsBetter = lb.optInt("LowerIsBetter", 0) != 0 || lb.optBoolean("LowerIsBetter", false),
                                hidden = lb.optInt("Hidden", 0) != 0 || lb.optBoolean("Hidden", false),
                            ),
                        )
                    }
                }
                RaGameSession(
                    gameId = gameId,
                    gameTitle = title,
                    gameIconUrl = iconUrl,
                    boxArtUrl = boxArtUrl,
                    richPresencePatch = rp,
                    gameHash = raHash,
                    achievements = list,
                    leaderboards = lbs,
                )
            }
        }.getOrNull()
    }

    suspend fun fetchLeaderboardEntries(leaderboardId: Int, limit: Int = 15): List<RaLeaderboardEntry> =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("r", "lbinfo")
                .add("i", leaderboardId.toString())
                .add("c", limit.toString())
                .build()
            val req = Request.Builder()
                .url(CONNECT_URL)
                .post(body)
                .header("User-Agent", buildUserAgent())
                .build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    val root = JSONObject(resp.body?.string() ?: "{}")
                    val lbData = root.optJSONObject("LeaderboardData") ?: return@use emptyList()
                    val entries = lbData.optJSONArray("Entries") ?: return@use emptyList()
                    val out = mutableListOf<RaLeaderboardEntry>()
                    for (i in 0 until entries.length()) {
                        val e = entries.optJSONObject(i) ?: continue
                        out.add(
                            RaLeaderboardEntry(
                                rank = e.optInt("Rank", i + 1),
                                user = e.optString("User", ""),
                                scoreFormatted = e.optString("FormattedScore", e.optString("Score", "")),
                            ),
                        )
                    }
                    out
                }
            }.getOrDefault(emptyList())
        }

    private fun fetchUnlockIds(user: String, token: String, gameId: Int, hardcore: Boolean): Set<Int> {
        val h = if (hardcore) "1" else "0"
        val body = FormBody.Builder()
            .add("r", "unlocks")
            .add("u", user)
            .add("t", token)
            .add("g", gameId.toString())
            .add("h", h)
            .build()
        val req = Request.Builder()
            .url(CONNECT_URL)
            .post(body)
            .header("User-Agent", buildUserAgent())
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                val obj = JSONObject(resp.body?.string() ?: "{}")
                val arr = obj.optJSONArray("UserUnlocks") ?: return@use emptySet()
                val out = HashSet<Int>(arr.length())
                for (i in 0 until arr.length()) out.add(arr.optInt(i))
                out
            }
        }.getOrDefault(emptySet())
    }
}
