import { useEffect, useRef, useState } from "react";
import Hls from "hls.js";

export default function IPTVPlayer({ streamUrl, onFailed, onPlaying }) {
  const videoRef = useRef(null);
  const callbacks = useRef({ onFailed, onPlaying });
  const [status, setStatus] = useState("");
  useEffect(() => { callbacks.current = { onFailed, onPlaying }; }, [onFailed, onPlaying]);

  useEffect(() => {
    const video = videoRef.current;
    let hls;
    let disposed = false;
    let failed = false;
    let attempts = 0;
    let lastProgress = Date.now();
    let lastTime = -1;
    const fail = () => {
      if (disposed || failed) return;
      failed = true;
      setStatus("This stream could not play. Select it again to retry.");
      callbacks.current.onFailed?.();
    };
    const play = () => video.play().catch(() => {
      if (!disposed) setStatus("Press Play to start playback.");
    });
    const recover = () => {
      if (disposed || failed) return;
      if (attempts++ >= 3) return fail();
      lastProgress = Date.now();
      setStatus(`Reconnecting… (${attempts}/3)`);
      if (hls) hls.startLoad(-1);
      else { video.load(); play(); }
    };
    const playing = () => {
      lastProgress = Date.now();
      setStatus("");
      callbacks.current.onPlaying?.();
    };
    const waiting = () => { if (!failed) setStatus("Buffering…"); };
    const error = () => { if (!hls) recover(); };
    video.addEventListener("playing", playing);
    video.addEventListener("waiting", waiting);
    video.addEventListener("error", error);
    video.addEventListener("ended", recover);
    if (/\.m3u8(?:[?#]|$)/i.test(streamUrl) && Hls.isSupported()) {
      hls = new Hls({ maxBufferLength: 30, maxMaxBufferLength: 60, backBufferLength: 30 });
      hls.attachMedia(video);
      hls.loadSource(streamUrl);
      hls.on(Hls.Events.MANIFEST_PARSED, play);
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (!data.fatal || disposed || failed) return;
        if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
          if (attempts++ >= 3) return fail();
          lastProgress = Date.now();
          hls.recoverMediaError();
        } else if (data.type === Hls.ErrorTypes.NETWORK_ERROR) recover();
        else fail();
      });
    } else { video.src = streamUrl; play(); }
    const watchdog = setInterval(() => {
      if (disposed || failed) return;
      if (video.currentTime !== lastTime) {
        lastTime = video.currentTime;
        lastProgress = Date.now();
      }
      // Respect manual pauses. Startup and buffering still get a timeout.
      if (video.paused && video.readyState >= 2) { lastProgress = Date.now(); return; }
      if (Date.now() - lastProgress > 20000) recover();
    }, 1000);
    return () => {
      disposed = true;
      clearInterval(watchdog);
      video.removeEventListener("playing", playing);
      video.removeEventListener("waiting", waiting);
      video.removeEventListener("error", error);
      video.removeEventListener("ended", recover);
      hls?.destroy();
      video.pause();
      video.removeAttribute("src");
      video.load();
    };
  }, [streamUrl]);

  return <div style={{ width: "100%", height: "100%", position: "relative" }}>
    <video ref={videoRef} style={{ width: "100%", height: "100%", background: "black" }} controls autoPlay playsInline />
    {status && <div role="status" style={{ position: "absolute", top: 12, left: 12, color: "white", background: "#000b", padding: 8, pointerEvents: "none" }}>{status}</div>}
  </div>;
}
