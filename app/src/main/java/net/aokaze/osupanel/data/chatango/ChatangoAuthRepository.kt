/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.chatango

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.math.BigInteger
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * Chatango account API — reverse-engineered from the Chatango 2.1.5 APK
 * (see ANALISIS_CHATANGO.md):
 *
 *   login         → POST https://chatango.com/settokenapp
 *                    (sid, pwd, encrypted, gcm, version=50, os, serial, model)
 *                    JSON: {"type":"success","data":{"token":"..."}}
 *                    error data: "pwd" | "sid" | "version"
 *
 *   updateProfile → POST https://chatango.com/updateprofile
 *                    (auth=token, token, arch=app, action=update, <field>=<value>…)
 */
object ChatangoAuthRepository {

    private const val VERSION = "50" // app version code used by the Chatango APK

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Login failure reasons, mirroring the Chatango error codes. */
    enum class LoginError { PASSWORD, USERNAME, VERSION, GENERIC }

    sealed class LoginResult {
        data class Success(val token: String) : LoginResult()
        data class Failure(val error: LoginError) : LoginResult()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** One Chatango group: [name] (room slug, used in the URL) + [title]. */
    data class ChatangoGroup(val name: String, val title: String)

    sealed class GroupsResult {
        data class Success(val groups: List<ChatangoGroup>, val recent: List<ChatangoGroup>) : GroupsResult()
        data object Failure : GroupsResult()
    }

    /**
     * Chatango device id — same as the APK's util/a.java:
     * ANDROID_ID (hex) → decimal, first 16 digits. Used for anti-abuse and as
     * the 3rd argument of the socket `tlogin` command.
     */
    fun deviceId(context: Context): String =
        runCatching {
            val hex = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: return@runCatching ""
            BigInteger(hex, 16).toString(10).take(16)
        }.getOrDefault("")

    private fun multipart(
        context: Context,
        extra: Map<String, String>,
    ): RequestBody {
        val form = FormBody.Builder()
        for ((key, value) in extra) {
            form.add(key, value)
        }
        form.add("version", VERSION)
        form.add("os", Build.VERSION.RELEASE)
        form.add("serial", deviceId(context))
        form.add("model", Build.MODEL)
        return form.build()
    }

    /**
     * Login to Chatango. [gcm] may be empty (GCM registration is optional for
     * basic chat functionality). Returns the auth token on success.
     */
    suspend fun login(
        context: Context,
        username: String,
        password: String,
        gcm: String = "",
    ): LoginResult = withContext(Dispatchers.IO) {
        val body = multipart(
            context,
            mapOf(
                "sid" to username.trim(),
                "pwd" to password,
                "encrypted" to "false",
                "gcm" to gcm,
            ),
        )
        val request = Request.Builder()
            .url("https://chatango.com/settokenapp")
            .post(body)
            .build()

        val responseText = runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull()

        if (responseText.isNullOrBlank()) return@withContext LoginResult.Failure(LoginError.GENERIC)

        runCatching {
            val root = json.parseToJsonElement(responseText).jsonObject
            when (root["type"]?.jsonPrimitive?.content) {
                "success" -> {
                    val data = root["data"]?.jsonObject
                    val token = data?.get("token")?.jsonPrimitive?.content
                    if (token.isNullOrEmpty()) {
                        LoginResult.Failure(LoginError.GENERIC)
                    } else {
                        LoginResult.Success(token)
                    }
                }
                "error" -> {
                    val code = root["data"]?.jsonPrimitive?.content
                    LoginResult.Failure(
                        when (code) {
                            "pwd" -> LoginError.PASSWORD
                            "sid" -> LoginError.USERNAME
                            "version" -> LoginError.VERSION
                            else -> LoginError.GENERIC
                        },
                    )
                }
                else -> LoginResult.Failure(LoginError.GENERIC)
            }
        }.getOrElse { LoginResult.Failure(LoginError.GENERIC) }
    }

    /**
     * Fetch the groups the account joined — same API as the Chatango app's
     * GroupListFragment: POST groupslistupdate (token, arch=app, auth=token).
     * Response: {"groups": "[[name,title],…]", "recent_groups": "[…]"}.
     */
    suspend fun getGroups(context: Context, token: String): GroupsResult = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("token", token)
            .add("arch", "app")
            .add("auth", "token")
            .build()
        val request = Request.Builder()
            .url("https://chatango.com/groupslistupdate")
            .post(form)
            .build()

        val body = runCatching {
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull() ?: return@withContext GroupsResult.Failure

        runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            GroupsResult.Success(
                groups = parseGroupArray(root["groups"]),
                recent = parseGroupArray(root["recent_groups"]),
            )
        }.getOrElse { GroupsResult.Failure }
    }

    /** One user found via the global /search endpoint. */
    @kotlinx.serialization.Serializable
    data class SearchUser(val username: String, val online: Boolean)

    /** Public mini-profile from mod1.xml (same as the Chatango app). */
    data class ChatangoProfile(
        val gender: String?,  // <s> — "M" / "F"
        val age: Int?,        // computed from <b> birthday
        val city: String?,    // <l> text, e.g. "San Jose, CA"
        val bio: String?,     // <body> (URL-decoded)
    )

    /**
     * Fetch a user's public mini-profile — GET ust.chatango.com/profileimg/x/y/user/mod1.xml
     * (same URL builder as the APK: d/a.java). No auth needed.
     */
    suspend fun getUserProfile(username: String): ChatangoProfile? = withContext(Dispatchers.IO) {
        val u = username.lowercase()
        val x = u.take(1)
        val y = u.drop(1).take(1).ifEmpty { x }
        // HTTPS required — Android blocks cleartext HTTP by default.
        val url = "https://ust.chatango.com/profileimg/$x/$y/$u/mod1.xml"
        val body = runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull() ?: return@withContext null
        parseMod1(body)
    }

    /** Parse the flat mod1.xml (tags: s=gender, b=birthday, l=city, body=bio). */
    private fun parseMod1(xml: String): ChatangoProfile? {
        fun tag(name: String): String? =
            Regex("<$name(?: [^>]*)?>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL)
                .find(xml)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

        val gender = tag("s")
        val birthday = tag("b")
        val city = tag("l")
        val bio = tag("body")?.let {
            runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
        }
        val age = birthday?.let { b ->
            runCatching {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val birth = fmt.parse(b)
                val now = java.util.Calendar.getInstance()
                val born = java.util.Calendar.getInstance().apply { time = birth }
                now.get(java.util.Calendar.YEAR) - born.get(java.util.Calendar.YEAR) -
                    if (now.get(java.util.Calendar.DAY_OF_YEAR) < born.get(java.util.Calendar.DAY_OF_YEAR)) 1 else 0
            }.getOrNull()
        }
        if (gender == null && birthday == null && city == null && bio == null) return null
        return ChatangoProfile(gender = gender, age = age, city = city, bio = bio)
    }

    /** Full profile picture URL — https://<user>.chatango.com/getprofilepicture. */
    fun profilePictureUrl(username: String): String =
        "https://${username.lowercase()}.chatango.com/getprofilepicture"

    /**
     * Small avatar thumbnail URL — ust.chatango.com/profileimg/x/y/user/thumb_m.jpg
     * (same as the Chatango app: d/a.java builds x=1st char, y=2nd char).
     */
    fun thumbUrl(username: String): String {
        val u = username.lowercase()
        val x = u.take(1)
        val y = u.drop(1).take(1).ifEmpty { x }
        return "https://ust.chatango.com/profileimg/$x/$y/$u/thumb_m.jpg"
    }

    /**
     * Uploaded image URL — ust.chatango.com/um/x/y/user/img/t_<id>.jpg
     * (pattern from the APK: pm/a/g.java, `//ust.chatango.com/um/.../img/t_*.jpg`).
     */
    fun chatangoUploadedImageUrl(sender: String, imgId: String): String {
        val u = sender.lowercase()
        val x = u.take(1)
        val y = u.drop(1).take(1).ifEmpty { x }
        return "https://ust.chatango.com/um/$x/$y/$u/img/t_$imgId.jpg"
    }

    /**
     * Upload an image for PM messages — POST chatango.com/uploadimg with
     * `u` (username) + `p` (password), exactly like the current web client
     * (UploadMediaModule.js: `u`, `p`, `filedata`). The old token-based
     * variant (`auth=token`) is dead — it returns 404. Response:
     * `success:<image-id>` (or `error:...`). Returns the id or null.
     */
    suspend fun uploadPmImage(context: Context, username: String, password: String, file: File): String? =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("u", username)
                .addFormDataPart("p", password)
                .addFormDataPart("filedata", file.name, file.asRequestBody("image/*".toMediaType()))
                .build()
            val request = Request.Builder()
                .url("https://chatango.com/uploadimg")
                .header("Origin", "https://chatango.com")
                .header("Referer", "https://chatango.com/")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                )
                .post(body)
                .build()
            val response = runCatching {
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else "HTTP ${resp.code}"
                }
            }.getOrNull() ?: return@withContext null
            // "success:<image-id>" / "error:<message>" / "HTTP <code>"
            val parts = response.split(":")
            if (parts.size == 2 && parts[0] == "success") parts[1] else null
        }

    sealed class SearchResult {
        data class Success(val users: List<SearchUser>) : SearchResult()
        data object Overloaded : SearchResult()
        data object Failure : SearchResult()
    }

    /**
     * Global user search — same API as the Chatango app's MembersSearch:
     * POST /search with filters; response `h=<user>;<1|0>:…` (1 = online).
     * [query] empty = browse all users (pagination via [from]/[to]).
     */
    suspend fun searchUsers(
        context: Context,
        token: String,
        query: String,
        from: Int = 0,
        to: Int = 20,
    ): SearchResult = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("token", token)
            .add("app", "1")
            .add("ami", "13")
            .add("ama", "99")
            .add("s", "B") // both genders
            .add("imc", "0") // no image filter (required, like the APK)
            .add("ss", query.trim())
            .add("f", from.toString())
            .add("t", to.toString())
            .build()
        val request = Request.Builder()
            .url("https://chatango.com/search")
            .post(form)
            .build()

        val body = runCatching {
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull() ?: return@withContext SearchResult.Failure

        when {
            body == "overload" -> SearchResult.Overloaded
            body == "unsafe" || body == "None" -> SearchResult.Success(emptyList())
            body.startsWith("h=") -> {
                val users = body.substring(2).split(":").mapNotNull { entry ->
                    val parts = entry.split(";")
                    if (parts.size != 2 || parts[0].isBlank()) null
                    else SearchUser(username = parts[0], online = parts[1] == "1")
                }
                SearchResult.Success(users)
            }
            else -> SearchResult.Failure
        }
    }

    /**
     * Parse the groups response — modern servers return a plain JSON array
     * directly: [["name","title"],…] (title still URL-encoded). Older ones
     * serialized it as a string; both are handled here.
     */
    private fun parseGroupArray(raw: kotlinx.serialization.json.JsonElement?): List<ChatangoGroup> {
        if (raw == null) return emptyList()
        return runCatching {
            val array = if (raw is kotlinx.serialization.json.JsonArray) {
                raw
            } else {
                // Legacy: the value was a JSON string containing the array.
                json.parseToJsonElement(raw.jsonPrimitive.content).jsonArray
            }
            array.mapNotNull { item ->
                runCatching {
                    val arr = item.jsonArray
                    if (arr.size < 1) return@runCatching null
                    val name = arr[0].jsonPrimitive.content
                    val title = if (arr.size > 1) {
                        runCatching { URLDecoder.decode(arr[1].jsonPrimitive.content, "UTF-8") }
                            .getOrDefault(arr[1].jsonPrimitive.content)
                    } else {
                        name
                    }
                    ChatangoGroup(name = name, title = title)
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Upload a profile photo — same as the Chatango EditProfileActivity
     * (`updateprofile` with action=fullpic + Filedata). Returns true on success.
     */
    suspend fun uploadProfilePicture(context: Context, token: String, file: File): Boolean = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("auth", "token")
            .addFormDataPart("token", token)
            .addFormDataPart("arch", "app")
            .addFormDataPart("action", "fullpic")
            .addFormDataPart("Filedata", file.name, file.asRequestBody("image/*".toMediaType()))
            .build()
        val request = Request.Builder()
            .url("https://chatango.com/updateprofile")
            .post(body)
            .build()
        runCatching {
            client.newCall(request).execute().use { resp -> resp.isSuccessful }
        }.getOrDefault(false)
    }

    /**
     * Update Chatango profile fields. [fields] maps field names → values
     * (age, gender, line, dir, la, lo, a2 …) — same API as the Chatango
     * EditProfileActivity. Returns true on success.
     */
    suspend fun updateProfile(
        context: Context,
        token: String,
        fields: Map<String, String>,
    ): Boolean = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
        form.add("auth", "token")
        form.add("token", token)
        form.add("arch", "app")
        form.add("action", "update")
        for ((key, value) in fields) {
            if (value.isNotEmpty()) form.add(key, value)
        }
        val request = Request.Builder()
            .url("https://chatango.com/updateprofile")
            .post(form.build())
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                resp.isSuccessful
            }
        }.getOrDefault(false)
    }
}
