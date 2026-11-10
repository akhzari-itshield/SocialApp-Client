package com.example.socialapp.data

import android.content.Context
import android.content.SharedPreferences
import com.example.socialapp.util.Constants

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(Constants.KEY_TOKEN, null)
        set(value) = prefs.edit().putString(Constants.KEY_TOKEN, value).apply()

    var userId: Int
        get() = prefs.getInt(Constants.KEY_USER_ID, -1)
        set(value) = prefs.edit().putInt(Constants.KEY_USER_ID, value).apply()

    var phone: String?
        get() = prefs.getString(Constants.KEY_PHONE, null)
        set(value) = prefs.edit().putString(Constants.KEY_PHONE, value).apply()

    var displayName: String?
        get() = prefs.getString(Constants.KEY_DISPLAY_NAME, null)
        set(value) = prefs.edit().putString(Constants.KEY_DISPLAY_NAME, value).apply()

    var avatarUrl: String?
        get() = prefs.getString("avatar_url", null)
        set(value) = prefs.edit().putString("avatar_url", value).apply()

    var permissionsJson: String?
        get() = prefs.getString("permissions", "{}")
        set(value) = prefs.edit().putString("permissions", value).apply()

    val isLoggedIn: Boolean
        get() = !token.isNullOrEmpty() && userId > 0

    fun saveSession(token: String, userId: Int, displayName: String?, phone: String) {
        this.token = token
        this.userId = userId
        this.displayName = displayName
        this.phone = phone
    }

    fun hasPermission(key: String): Boolean {
        return try {
            val json = org.json.JSONObject(permissionsJson ?: "{}")
            if (!json.has(key)) true else json.optBoolean(key, true)
        } catch (_: Exception) { true }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}