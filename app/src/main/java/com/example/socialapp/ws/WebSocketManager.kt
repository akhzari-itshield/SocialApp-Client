package com.example.socialapp.ws

import android.util.Log
import com.example.socialapp.util.Constants
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val token: String,
    private val onEvent: (event: String, data: JSONObject) -> Unit,
) {
    private var ws: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun connect() {
        val url = "${Constants.WS_URL}?token=$token"
        val req = Request.Builder().url(url).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WS", "Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    val event = obj.optString("event")
                    val data = obj.optJSONObject("data") ?: obj
                    onEvent(event, data)
                } catch (e: Exception) {
                    Log.e("WS", "Bad message: $text", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WS", "Failure", t)
                webSocket.cancel()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    connect()
                }, 3000)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WS", "Closed: $reason")
            }
        })
    }

    fun send(raw: String): Boolean {
        return ws?.send(raw) ?: false
    }

    fun sendTyping(chatId: Int) {
        val payload = JSONObject().apply {
            put("event", "typing")
            put("chat_id", chatId)
        }
        ws?.send(payload.toString())
    }

    fun close() {
        ws?.close(1000, "user closed")
        ws = null
    }
}