package com.stormgbca.emulator.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
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

data class RaAchievementBadge(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val badgeName: String = "",
    val type: String = "",
    val unlocked: Boolean = false,
    val unlockedHardcore: Boolean = false,
) {
    val badgeUrl: String
        get() = if (badgeName.isNotBlank()) {
            val suffix = if (unlocked || unlockedHardcore) "" else "_lock"
            "https://media.retroachievements.org/Badge/${badgeName}${suffix}.png"
        } else ""
}

data class RaGameSession(
    val gameId: Int,
    val gameTitle: String,
    val gameIconUrl: String,
    val richPresencePatch: String,
    val achievements: List<RaAchievementBadge>,
) {
    val totalCount: Int get() = achievements.size
    val unlockedCount: Int get() = achievements.count { it.unlocked || it.unlockedHardcore }
    val hardcoreUnlockedCount: Int get() = achievements.count { it.unlockedHardcore }
    val totalPoints: Int get() = achievements.sumOf { it.points }
    val earnedPoints: Int get() = achievements.filter { it.unlocked || it.unlockedHardcore }.sumOf { it.points }
    val completionPercent: Int get() = if (totalCount == 0) 0 else ((unlockedCount * 100) / totalCount)
}

object RetroAchievementsClient {
    private const val CONNECT_URL = "https://retroachievements.org/dorequest.php"
    private const val UA = "STORM-GBCA/1.1.0"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
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
            return@withContext RaLoginResult(false, error = "Введите логин и пароль RetroAchievements")
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
                    val err = obj.optString("Error", "Неверный логин или пароль")
                    RaLoginResult(false, error = err)
                }
            }
        }.getOrElse {
            RaLoginResult(false, error = "Ошибка сети: ${it.message ?: "нет подключения"}")
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
                    getCachedProfile().copy(error = obj.optString("Error", "Сессия истекла"))
                }
            }
        }.getOrElse {
            getCachedProfile().copy(error = "Нет сети (показаны сохраненные данные)")
        }
    }

    suspend fun lookupGameSession(raHash: String): RaGameSession? = withContext(Dispatchers.IO) {
        val user = GbcaPrefs.getRaUsername()
        val token = GbcaPrefs.getRaToken()
        if (user.isBlank() || token.isBlank() || raHash.isBlank()) return@withContext null

        val gameIdUrl = "$CONNECT_URL?r=gameid&m=${URLEncoder.encode(raHash, "UTF-8")}"
        val gameId = runCatching {
            client.newCall(Request.Builder().url(gameIdUrl).header("User-Agent", UA).build())
                .execute().use { resp ->
                    JSONObject(resp.body?.string() ?: "{}").optInt("GameID", 0)
                }
        }.getOrDefault(0)
        if (gameId <= 0) return@withContext null

        // Fetch user unlocks for BOTH softcore (h=0) and hardcore (h=1)
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
                    val iconUrl = patchData.optString("ImageIconURL", "")
                        .ifBlank {
                            val iconName = patchData.optString("ImageIcon", "")
                            if (iconName.isNotBlank()) "https://media.retroachievements.org$iconName" else ""
                        }
                    val rp = patchData.optString("RichPresencePatch", "")
                    val arr = patchData.optJSONArray("Achievements")
                    val includeUnofficial = GbcaPrefs.isRaUnofficial()
                    val list = mutableListOf<RaAchievementBadge>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val a = arr.optJSONObject(i) ?: continue
                            val flags = a.optInt("Flags", 3)
                            if (flags != 3 && !includeUnofficial) continue
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
                                    unlocked = isSoft,
                                    unlockedHardcore = isHard,
                                )
                            )
                        }
                    }
                    RaGameSession(gameId, title, iconUrl, rp, list)
                }
        }.getOrNull()
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
