package com.example.socialapp.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import java.util.concurrent.Executors

class WebRTCClient(
    private val context: Context,
    private val observer: PeerConnection.Observer,
    private val onSignalReady: (type: String, payload: String) -> Unit,
) {
    companion object {
        private const val TAG = "WebRTCClient"
        val sharedEglBase: EglBase by lazy { EglBase.create() }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localSurfaceView: SurfaceViewRenderer? = null
    private var remoteViewInitialized = false
    private var localViewInitialized = false

    fun init() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val encoderFactory = DefaultVideoEncoderFactory(sharedEglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(sharedEglBase.eglBaseContext)
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
        Log.d(TAG, "PeerConnection created")
    }

    fun initRemoteVideo(surfaceView: SurfaceViewRenderer) {
        try {
            if (!remoteViewInitialized) {
                surfaceView.setZOrderMediaOverlay(false)
                surfaceView.init(sharedEglBase.eglBaseContext, null)
                surfaceView.setEnableHardwareScaler(true)
                surfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                remoteViewInitialized = true
                Log.d(TAG, "Remote video initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "initRemoteVideo error: ${e.message}", e)
        }
    }

    fun startLocalVideo(surfaceView: SurfaceViewRenderer) {
        try {
            localSurfaceView = surfaceView
            Log.d(TAG, "startLocalVideo called")

            if (!localViewInitialized) {
                surfaceView.setZOrderMediaOverlay(true)
                surfaceView.init(sharedEglBase.eglBaseContext, null)
                localViewInitialized = true
                Log.d(TAG, "Local video view initialized")
            }

            surfaceView.setMirror(false)

            surfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)

            surfaceView.setEnableHardwareScaler(false)

            val videoSource = peerConnectionFactory.createVideoSource(false)
            surfaceTextureHelper = SurfaceTextureHelper.create(
                "CaptureThread", sharedEglBase.eglBaseContext
            )

            videoCapturer = createCameraCapturer()
            if (videoCapturer == null) {
                Log.e(TAG, "❌ ERROR: هیچ دوربینی پیدا نشد!")
                return
            }
            Log.d(TAG, "✅ Camera capturer created: ${videoCapturer!!.javaClass.simpleName}")

            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
            videoCapturer?.startCapture(1280, 720, 30)
            Log.d(TAG, "Camera capture started (1280x720 @ 30fps)")

            localVideoTrack = peerConnectionFactory.createVideoTrack("VIDEO_TRACK", videoSource)
            localVideoTrack?.addSink(surfaceView)
            Log.d(TAG, "Local track created and added to preview view")

            localVideoTrack?.let { peerConnection?.addTrack(it, listOf("stream1")) }

            // ۱۰. افزودن صدای محلی
            val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
            localAudioTrack = peerConnectionFactory.createAudioTrack("AUDIO_TRACK", audioSource)
            localAudioTrack?.let { peerConnection?.addTrack(it, listOf("stream1")) }

            Log.d(TAG, "✅ Local video/audio fully started")
        } catch (e: Exception) {
            Log.e(TAG, "startLocalVideo error: ${e.message}", e)
        }
    }

    fun bringLocalToFront() {
        try {
            localSurfaceView?.bringToFront()
            localSurfaceView?.requestLayout()
            Log.d(TAG, "Local view brought to front")
        } catch (e: Exception) {
            Log.e(TAG, "bringLocalToFront error: ${e.message}")
        }
    }

    fun startLocalAudioOnly() {
        try {
            val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
            localAudioTrack = peerConnectionFactory.createAudioTrack("AUDIO_TRACK", audioSource)
            localAudioTrack?.let { peerConnection?.addTrack(it, listOf("stream1")) }
            Log.d(TAG, "Local audio only started")
        } catch (e: Exception) {
            Log.e(TAG, "startLocalAudioOnly error: ${e.message}", e)
        }
    }

    fun createOffer() {
        executor.execute {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
            peerConnection?.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    sdp?.let {
                        peerConnection?.setLocalDescription(SimpleSdpObserver(), it)
                        onSignalReady("offer", it.description)
                    }
                }
            }, constraints)
        }
    }

    fun handleOffer(sdp: String) {
        executor.execute {
            val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
            peerConnection?.setRemoteDescription(SimpleSdpObserver(), sessionDescription)
            peerConnection?.createAnswer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(answer: SessionDescription?) {
                    answer?.let {
                        peerConnection?.setLocalDescription(SimpleSdpObserver(), it)
                        onSignalReady("answer", it.description)
                    }
                }
            }, MediaConstraints())
        }
    }

    fun handleAnswer(sdp: String) {
        executor.execute {
            val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            peerConnection?.setRemoteDescription(SimpleSdpObserver(), sessionDescription)
        }
    }

    fun handleIceCandidate(sdpMid: String?, sdpMLineIndex: Int, sdp: String) {
        executor.execute {
            try {
                val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                peerConnection?.addIceCandidate(candidate)
            } catch (e: Exception) {
                Log.e(TAG, "addIceCandidate error: ${e.message}")
            }
        }
    }

    fun getStatsRaw(callback: (String) -> Unit) {
        try {
            peerConnection?.getStats { report ->
                try {
                    val sb = StringBuilder("\n=== PeerConnection Stats ===\n")
                    report.statsMap?.forEach { (key, stat) ->
                        val type = stat.type
                        if (type == "inbound-rtp" || type == "outbound-rtp" ||
                            type == "candidate-pair" || type == "transport") {
                            sb.append("[$type] $key\n")
                            stat.members?.forEach { member ->
                                sb.append("  ${member.key} = ${member.value}\n")
                            }
                        }
                    }
                    callback(sb.toString())
                } catch (e: Exception) {
                    callback("Stats formatting error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            callback("Stats exception: ${e.message}")
        }
    }

    fun close() {
        try { videoCapturer?.stopCapture() } catch (_: Exception) {}
        try { videoCapturer?.dispose() } catch (_: Exception) {}
        videoCapturer = null
        try { surfaceTextureHelper?.dispose() } catch (_: Exception) {}
        surfaceTextureHelper = null
        try { localVideoTrack?.dispose() } catch (_: Exception) {}
        localVideoTrack = null
        try { localAudioTrack?.dispose() } catch (_: Exception) {}
        localAudioTrack = null
        try { peerConnection?.close() } catch (_: Exception) {}
        try { peerConnection?.dispose() } catch (_: Exception) {}
        peerConnection = null
        try { peerConnectionFactory.dispose() } catch (_: Exception) {}
        try { executor.shutdown() } catch (_: Exception) {}
        remoteViewInitialized = false
        localViewInitialized = false
        localSurfaceView = null
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        try {
            val enumerator1 = Camera1Enumerator(false)
            // اول دوربین جلو
            for (name in enumerator1.deviceNames) {
                if (enumerator1.isFrontFacing(name)) {
                    val capturer = enumerator1.createCapturer(name, null)
                    if (capturer != null) {
                        Log.d(TAG, "Using Camera1 front: $name")
                        return capturer
                    }
                }
            }
            // بعد هر دوربینی
            for (name in enumerator1.deviceNames) {
                val capturer = enumerator1.createCapturer(name, null)
                if (capturer != null) {
                    Log.d(TAG, "Using Camera1 (any): $name")
                    return capturer
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Camera1 enumerator failed: ${e.message}")
        }

        try {
            val enumerator2 = Camera2Enumerator(context)
            for (name in enumerator2.deviceNames) {
                if (enumerator2.isFrontFacing(name)) {
                    val capturer = enumerator2.createCapturer(name, null)
                    if (capturer != null) {
                        Log.d(TAG, "Using Camera2 front: $name")
                        return capturer
                    }
                }
            }
            for (name in enumerator2.deviceNames) {
                val capturer = enumerator2.createCapturer(name, null)
                if (capturer != null) {
                    Log.d(TAG, "Using Camera2 (any): $name")
                    return capturer
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Camera2 enumerator failed: ${e.message}")
        }

        Log.e(TAG, "❌ No camera capturer available!")
        return null
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}