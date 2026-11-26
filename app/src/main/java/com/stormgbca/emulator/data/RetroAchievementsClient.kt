package com.stormgbca.emulator.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class RaLoginResult(
    val success: Boolean,
    val username: String = "",
    val token: String = "",
    val score: Int = 0,
    val error: String = "",
)

data class RaAchievementBadge(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val unlocked: Boolean,
)

data class RaGameSession(
    val gameId: Int,
    val gameTitle: String,
    val achievements: List<RaAchievementBadge>,
)

object RetroAchievementsClient {
    private const val CONNECT_URL = "https://retroachievements.org/dorequest.php"
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun login(username: String, password: String): RaLoginResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("r", "login2")
            .add("u", username.trim())
            .add("p", password)
            .build()
        val req = Request.Builder()
            .url(CONNECT_URL)
            .post(body)
            .header("User-Agent", "STORM-GBCA/1.0.0")
            .build()

        runCatching {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                val obj = JSONObject(text)
                if (obj.optBoolean("Success", false)) {
                    val user = obj.optString("User", username.trim())
                    val token = obj.optString("Token", "")
                    val score = obj.optInt("Score", 0)
                    GbcaPrefs.setRaCredentials(user, token)
                    RaLoginResult(true, user, token, score)
                } else {
                    RaLoginResult(false, error = obj.optString("Error", "Authentication failed"))
                }
            }
        }.getOrElse {
            RaLoginResult(false, error = it.message ?: "Network error")
        }
    }

    suspend fun lookupGameSession(raHash: String): RaGameSession? = withContext(Dispatchers.IO) {
        val user = GbcaPrefs.getRaUsername()
        val token = GbcaPrefs.getRaToken()
        if (user.isBlank() || token.isBlank() || raHash.isBlank()) return@withContext null

        val gameIdUrl = "$CONNECT_URL?r=gameid&m=${URLEncoder.encode(raHash, "UTF-8")}"
        val gameId = runCatching {
            client.newCall(Request.Builder().url(gameIdUrl).header("User-Agent", "STORM-GBCA/1.0.0").build())
                .execute().use { resp ->
                    val obj = JSONObject(resp.body?.string() ?: "{}")
                    obj.optInt("GameID", 0)
                }
        }.getOrDefault(0)

        if (gameId <= 0) return@withContext null

        val patchUrl = "$CONNECT_URL?r=patch&u=${URLEncoder.encode(user, "UTF-8")}&t=${URLEncoder.encode(token, "UTF-8")}&g=$gameId"
        runCatching {
            client.newCall(Request.Builder().url(patchUrl).header("User-Agent", "STORM-GBCA/1.0.0").build())
                .execute().use { resp ->
                    val root = JSONObject(resp.body?.string() ?: "{}")
                    if (!root.optBoolean("Success", false)) return@use null
                    val patchData = root.optJSONObject("PatchData") ?: return@use null
                    val title = patchData.optString("Title", "RetroAchievements #$gameId")
                    val arr = patchData.optJSONArray("Achievements")
                    val list = mutableListOf<RaAchievementBadge>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val a = arr.optJSONObject(i) ?: continue
                            list.add(
                                RaAchievementBadge(
                                    id = a.optInt("ID", 0),
                                    title = a.optString("Title", ""),
                                    description = a.optString("Description", ""),
                                    points = a.optInt("Points", 0),
                                    unlocked = a.optInt("Flags", 3) == 3 && a.optBoolean("Unlocked", false),
                                )
                            )
                        }
                    }
                    RaGameSession(gameId, title, list)
                }
        }.getOrNull()
    }
}
