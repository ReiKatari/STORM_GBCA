package com.stormgbca.emulator.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
    val type: String = "",
    val isUnofficial: Boolean = false,
    val unlocked: Boolean = false,
    val unlockedHardcore: Boolean = false,
) {
    val badgeUrl: String
        get() = if (badgeName.isNotBlank()) {
            val suffix = if (unlocked || unlockedHardcore) "" else "_lock"
            "https://media.retroachievements.org/Badge/${badgeName}${suffix}.png"
        } else ""
}

data class RaLeaderboardEntry(
    val rank: Int,
    val user: String,
    val scoreFormatted: String,
)

data class RaLeaderboard(
    val id: Int,
    val title: String,
    val description: String,
    val format: String = "",
    val entries: List<RaLeaderboardEntry> = emptyList(),
)

data class RaGameSession(
    val gameId: Int,
    val gameTitle: String,
    val gameIconUrl: String,
    val boxArtUrl: String = "",
    val richPresencePatch: String = "",
    val achievements: List<RaAchievementBadge> = emptyList(),
    val leaderboards: List<RaLeaderboard> = emptyList(),
) {
    val totalCount: Int get() = achievements.size
    val unlockedCount: Int get() = achievements.count { it.unlocked || it.unlockedHardcore }
    val hardcoreUnlockedCount: Int get() = achievements.count { it.unlockedHardcore }
    val softcoreOnlyCount: Int get() = achievements.count { it.unlocked && !it.unlockedHardcore }
    val missableCount: Int get() = achievements.count { it.type.equals("missable", ignoreCase = true) }
    val totalPoints: Int get() = achievements.sumOf { it.points }
    val earnedPoints: Int get() = achievements.filter { it.unlocked || it.unlockedHardcore }.sumOf { it.points }
    val hardcoreEarnedPoints: Int get() = achievements.filter { it.unlockedHardcore }.sumOf { it.points }
    val completionPercent: Int get() = if (totalCount == 0) 0 else ((unlockedCount * 100) / totalCount)
    val hardcorePercent: Int get() = if (totalCount == 0) 0 else ((hardcoreUnlockedCount * 100) / totalCount)
    val webUrl: String get() = "https://retroachievements.org/game/$gameId"
}

object RetroAchievementsClient {
    private const val CONNECT_URL = "https://retroachievements.org/dorequest.php"
    private const val UA = "STORM-GBCA/1.2.0"

    private val gameIdCache = ConcurrentHashMap<String, Int>()
    private val boxArtCache = ConcurrentHashMap<String, String>()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .build()
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
        val body = FormBody.Builder()
            .add("r", "login2")
            .add("u", cleanUser)
            .add("p", password)
            .build()
        val req = Request.Builder()
            .url(CONNECT_URL)
            .post(body)
            .header("User-Agent", UA)
            .build()

        runCatching {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                val obj = JSONObject(text)
                if (obj.optBoolean("Success", false)) {
                    val user = obj.optString("User", cleanUser)
                    val token = obj.optString("Token", "")
                    val score = obj.optInt("Score", 0)
                    val softScore = obj.optInt("SoftcoreScore", 0)
                    val messages = obj.optInt("Messages", 0)
                    GbcaPrefs.setRaCredentials(user, token)
                    GbcaPrefs.setRaProfileStats(score, softScore, GbcaPrefs.getRaRank(), GbcaPrefs.getRaMotto())
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
        }.getOrElse {
            RaLoginResult(false, error = it.message ?: "Network error")
        }
    }

    suspend fun refreshProfileWithToken(): RaUserProfile = withContext(Dispatchers.IO) {
        val user = GbcaPrefs.getRaUsername()
        val token = GbcaPrefs.getRaToken()
        if (user.isBlank() || token.isBlank()) return@withContext RaUserProfile(connected = false)

        val body = FormBody.Builder()
            .add("r", "login2")
            .add("u", user)
            .add("t", token)
            .build()
        val req = Request.Builder().url(CONNECT_URL).post(body).header("User-Agent", UA).build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                val obj = JSONObject(resp.body?.string() ?: "{}")
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
        }.getOrElse {
            getCachedProfile().copy(error = it.message ?: "Offline")
        }
    }

    suspend fun testConnection(): RaConnectionDiagnostics = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val user = GbcaPrefs.getRaUsername()
        val token = GbcaPrefs.getRaToken()
        runCatching {
            val req = if (user.isNotBlank() && token.isNotBlank()) {
                val body = FormBody.Builder()
                    .add("r", "login2")
                    .add("u", user)
                    .add("t", token)
                    .build()
                Request.Builder().url(CONNECT_URL).post(body).header("User-Agent", UA).build()
            } else {
                Request.Builder().url("$CONNECT_URL?r=latestclient&e=0").header("User-Agent", UA).build()
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

    suspend fun syncPendingSubmissions(): Int = withContext(Dispatchers.IO) {
        val user = GbcaPrefs.getRaUsername()
        val token = GbcaPrefs.getRaToken()
        val pending = GbcaPrefs.getRaPendingQueue()
        if (user.isBlank() || token.isBlank() || pending.isEmpty()) return@withContext 0
        var synced = 0
        for (achId in pending) {
            val url = "$CONNECT_URL?r=awardachievement&u=${URLEncoder.encode(user, "UTF-8")}&t=${URLEncoder.encode(token, "UTF-8")}&a=$achId&h=0"
            val ok = runCatching {
                client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
                    JSONObject(r.body?.string() ?: "{}").optBoolean("Success", false)
                }
            }.getOrDefault(false)
            if (ok) synced++
        }
        if (synced == pending.size) GbcaPrefs.clearRaPendingQueue()
        synced
    }

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
                val patchUrl = "$CONNECT_URL?r=patch&u=${URLEncoder.encode(user, "UTF-8")}&t=${URLEncoder.encode(token, "UTF-8")}&g=$gameId"
                val url = runCatching {
                    client.newCall(Request.Builder().url(patchUrl).header("User-Agent", UA).build()).execute().use { resp ->
                        val patch = JSONObject(resp.body?.string() ?: "{}").optJSONObject("PatchData")
                        val box = patch?.optString("ImageBoxArtURL", "")?.ifBlank {
                            patch.optString("ImageIconURL", "")
                        }?.ifBlank {
                            val rel = patch.optString("ImageIcon", "")
                            if (rel.isNotBlank()) "https://media.retroachievements.org$rel" else ""
                        }
                        box
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
        val gameIdUrl = "$CONNECT_URL?r=gameid&m=${URLEncoder.encode(raHash, "UTF-8")}"
        val id = runCatching {
            client.newCall(Request.Builder().url(gameIdUrl).header("User-Agent", UA).build())
                .execute().use { resp ->
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

        val softUnlocks = fetchUnlockIds(user, token, gameId, hardcore = false)
        val hardUnlocks = fetchUnlockIds(user, token, gameId, hardcore = true)

        val patchUrl = "$CONNECT_URL?r=patch&u=${URLEncoder.encode(user, "UTF-8")}&t=${URLEncoder.encode(token, "UTF-8")}&g=$gameId"
        runCatching {
            client.newCall(Request.Builder().url(patchUrl).header("User-Agent", UA).build())
                .execute().use { resp ->
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
                            val isSoft = isHard || softUnlocks.contains(id)
                            list.add(
                                RaAchievementBadge(
                                    id = id,
                                    title = a.optString("Title", ""),
                                    description = a.optString("Description", ""),
                                    points = a.optInt("Points", 0),
                                    badgeName = a.optString("BadgeName", ""),
                                    type = a.optString("Type", ""),
                                    isUnofficial = unofficial,
                                    unlocked = isSoft,
                                    unlockedHardcore = isHard,
                                )
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
                                    format = lb.optString("Format", ""),
                                )
                            )
                        }
                    }
                    RaGameSession(gameId, title, iconUrl, boxArtUrl, rp, list, lbs)
                }
        }.getOrNull()
    }

    suspend fun fetchLeaderboardEntries(leaderboardId: Int, limit: Int = 15): List<RaLeaderboardEntry> =
        withContext(Dispatchers.IO) {
            val url = "$CONNECT_URL?r=lbinfo&i=$leaderboardId&c=$limit"
            runCatching {
                client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { resp ->
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
                            )
                        )
                    }
                    out
                }
            }.getOrDefault(emptyList())
        }

    private fun fetchUnlockIds(user: String, token: String, gameId: Int, hardcore: Boolean): Set<Int> {
        val h = if (hardcore) 1 else 0
        val url = "$CONNECT_URL?r=unlocks&u=${URLEncoder.encode(user, "UTF-8")}&t=${URLEncoder.encode(token, "UTF-8")}&g=$gameId&h=$h"
        return runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", UA).build())
                .execute().use { resp ->
                    val obj = JSONObject(resp.body?.string() ?: "{}")
                    val arr = obj.optJSONArray("UserUnlocks") ?: return@use emptySet()
                    val out = HashSet<Int>(arr.length())
                    for (i in 0 until arr.length()) out.add(arr.optInt(i))
                    out
                }
        }.getOrDefault(emptySet())
    }
}
