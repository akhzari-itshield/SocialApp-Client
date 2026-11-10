package com.example.socialapp.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.socialapp.data.SessionManager
import com.example.socialapp.databinding.ActivityCallBinding
import com.example.socialapp.webrtc.WebRTCClient
import com.example.socialapp.ws.WebSocketManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.*

class CallActivity : AppCompatActivity(), PeerConnection.Observer {

    private lateinit var b: ActivityCallBinding
    private lateinit var session: SessionManager
    private lateinit var webRTCClient: WebRTCClient
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var isCaller = false
    private var targetUserId = -1
    private var callType = "video"
    private var callEnded = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) initializeCall()
        else {
            Toast.makeText(this, "مجوز دوربین/میکروفون لازم است", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCallBinding.inflate(layoutInflater)
        setContentView(b.root)

        session = SessionManager(this)
        targetUserId = intent.getIntExtra("target_user_id", -1)
        isCaller = intent.getBooleanExtra("is_caller", false)
        callType = intent.getStringExtra("call_type") ?: "video"

        setupAudioManager()

        b.btnEndCall.setOnClickListener { endCall() }

        checkPermissionsAndStart()
    }

    private fun setupAudioManager() {
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(focusRequest)
                audioFocusRequest = focusRequest
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }

            Log.d("Call", "AudioManager set to MODE_IN_COMMUNICATION")
        } catch (e: Exception) {
            Log.e("Call", "AudioManager setup error: ${e.message}", e)
        }
    }

    private fun checkPermissionsAndStart() {
        val needed = mutableListOf<String>()
        if (callType == "video") needed.add(Manifest.permission.CAMERA)
        needed.add(Manifest.permission.RECORD_AUDIO)
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) initializeCall()
        else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun initializeCall() {
        webSocketManager = WebSocketManager(session.token ?: "") { event, data ->
            runOnUiThread { handleIncomingSignal(event, data) }
        }
        webSocketManager.connect()

        webRTCClient = WebRTCClient(this, this) { type, payload ->
            sendSignal(type, payload)
        }
        webRTCClient.init()

        webRTCClient.initRemoteVideo(b.remoteVideoView)

        if (callType == "video") {
            b.localVideoView.visibility = View.VISIBLE
            b.remoteVideoView.visibility = View.VISIBLE

            b.localVideoView.bringToFront()
            b.localVideoView.requestLayout()

            webRTCClient.startLocalVideo(b.localVideoView)

            b.localVideoView.post {
                b.localVideoView.bringToFront()
                b.localVideoView.requestLayout()
                webRTCClient.bringLocalToFront()
            }
        } else {
            b.localVideoView.visibility = View.GONE
            b.remoteVideoView.visibility = View.GONE
            webRTCClient.startLocalAudioOnly()
        }

        if (isCaller) sendCallRequest() else sendCallAccept()

        startStatsLogging()
    }

    private fun startStatsLogging() {
        lifecycleScope.launch {
            delay(3000)
            while (isActive) {
                webRTCClient.getStatsRaw { stats ->
                    Log.d("WebRTC-Stats", stats)
                }
                delay(5000)
            }
        }
    }

    private fun sendCallRequest() {
        webSocketManager.send(JSONObject().apply {
            put("event", "call_request")
            put("target_id", targetUserId)
            put("call_type", callType)
        }.toString())
    }

    private fun sendCallAccept() {
        webSocketManager.send(JSONObject().apply {
            put("event", "call_accept")
            put("target_id", targetUserId)
        }.toString())
    }

    private fun sendCallEnd() {
        if (callEnded) return
        callEnded = true
        try {
            webSocketManager.send(JSONObject().apply {
                put("event", "call_end")
                put("target_id", targetUserId)
            }.toString())
        } catch (_: Exception) {}
    }

    private fun sendSignal(type: String, payload: String) {
        try {
            webSocketManager.send(JSONObject().apply {
                put("event", "webrtc_signal")
                put("target_id", targetUserId)
                put("signal", JSONObject().apply {
                    put("type", type)
                    put("payload", payload)
                })
            }.toString())
        } catch (e: Exception) {
            Log.e("Call", "sendSignal error: ${e.message}")
        }
    }

    private fun handleIncomingSignal(event: String, data: JSONObject) {
        Log.d("Call", "Incoming event: $event")
        when (event) {
            "call_accept" -> {
                if (isCaller) {
                    Log.d("Call", "call_accept received → creating offer")
                    webRTCClient.createOffer()
                }
            }
            "call_reject" -> {
                Toast.makeText(this, "تماس رد شد", Toast.LENGTH_SHORT).show()
                finish()
            }
            "call_end" -> {
                Toast.makeText(this, "تماس پایان یافت", Toast.LENGTH_SHORT).show()
                finish()
            }
            "webrtc_signal" -> {
                val signal = data.optJSONObject("signal") ?: return
                val type = signal.optString("type")
                val payload = signal.optString("payload")
                Log.d("Call", "webrtc_signal: type=$type")
                when (type) {
                    "offer" -> webRTCClient.handleOffer(payload)
                    "answer" -> webRTCClient.handleAnswer(payload)
                    "ice" -> {
                        val c = JSONObject(payload)
                        webRTCClient.handleIceCandidate(
                            c.optString("sdpMid"),
                            c.optInt("sdpMLineIndex"),
                            c.optString("candidate")
                        )
                    }
                }
            }
        }
    }

    private fun endCall() {
        sendCallEnd()
        try { webRTCClient.close() } catch (_: Exception) {}
        try { webSocketManager.close() } catch (_: Exception) {}
        finish()
    }

    override fun onBackPressed() { endCall() }

    override fun onDestroy() {
        if (!callEnded) {
            try { sendCallEnd() } catch (_: Exception) {}
        }
        try { webRTCClient.close() } catch (_: Exception) {}
        try { webSocketManager.close() } catch (_: Exception) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (_: Exception) {}

        super.onDestroy()
    }

    // ---------- PeerConnection.Observer ----------

    override fun onIceCandidate(candidate: IceCandidate?) {
        candidate?.let {
            val payload = JSONObject().apply {
                put("sdpMid", it.sdpMid)
                put("sdpMLineIndex", it.sdpMLineIndex)
                put("candidate", it.sdp)
            }.toString()
            sendSignal("ice", payload)
        }
    }

    override fun onAddStream(stream: MediaStream?) {
        Log.d("WebRTC", "onAddStream: ${stream?.videoTracks?.size} video tracks")
        runOnUiThread {
            stream?.videoTracks?.firstOrNull()?.let { vt ->
                vt.addSink(b.remoteVideoView)
                Log.d("WebRTC", "Added sink from onAddStream")
            }
        }
    }

    override fun onTrack(transceiver: RtpTransceiver?) {
        val mediaType = transceiver?.mediaType
        Log.d("WebRTC", "onTrack: mediaType=$mediaType")
        when (mediaType) {
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO -> {
                runOnUiThread {
                    (transceiver.receiver?.track() as? VideoTrack)?.let { vt ->
                        vt.addSink(b.remoteVideoView)
                        Log.d("WebRTC", "Added video sink from onTrack")
                    }
                }
            }
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO -> {
                Log.d("WebRTC", "Audio track received")
            }
            else -> {}
        }
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        Log.d("WebRTC", "ICE state: $state")
    }
    override fun onIceConnectionReceivingChange(p0: Boolean) {}
    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
    override fun onRemoveStream(p0: MediaStream?) {}
    override fun onDataChannel(p0: DataChannel?) {}
    override fun onRenegotiationNeeded() {}
}