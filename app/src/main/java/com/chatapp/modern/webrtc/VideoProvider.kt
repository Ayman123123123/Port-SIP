package com.chatapp.modern.webrtc

import org.webrtc.VideoSink

/**
 * Optional capability of a [com.chatapp.modern.engine.CallEngine] to expose live
 * local/remote video frames. The call UI checks `engine is VideoProvider` and
 * attaches its [org.webrtc.SurfaceViewRenderer] (which is a [VideoSink]).
 */
interface VideoProvider {
    fun attachLocalSink(sink: VideoSink)
    fun attachRemoteSink(sink: VideoSink)
    fun detachSinks()
}
