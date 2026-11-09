import { useEffect, useRef, useState } from "react";
import Hls from "hls.js";
import { resumable, formatTime } from "../lib/library.mjs";

export default function IPTVPlayer({ streamUrl, onFailed, onPlaying, audio, onAudioChange, resumeRecord, onProgress }) {
  const videoRef = useRef(null);
  const callbacks = useRef({ onFailed, onPlaying, onAudioChange, onProgress });
  const initial = useRef({ audio, resumeRecord });
  const [resumePrompt, setResumePrompt] = useState(null);
  const chooseResume = useRef(null);
  const [status, setStatus] = useState("");
  useEffect(() => { callbacks.current = { onFailed, onPlaying, onAudioChange, onProgress }; }, [onFailed, onPlaying, onAudioChange, onProgress]);

  useEffect(() => {
    const video = videoRef.current;
    let hls;
    let disposed = false;
    let failed = false;
    let attempts = 0;
    let lastProgress = Date.now();
    let lastTime = -1;
    let isLive = /127\.0\.0\.1.*\/mp4\//.test(streamUrl) ? true : null;
    let pendingResume = false;
    let resumeChecked = false;
    let savedAt = 0;
    const record = initial.current.resumeRecord;
    video.volume = initial.current.audio?.volume ?? 1;
    video.muted = initial.current.audio?.muted ?? false;
    const save = (ended = false) => {
      if (pendingResume || isLive !== false || !Number.isFinite(video.duration)) return;
      callbacks.current.onProgress?.({ position: video.currentTime, duration: video.duration,
        ended: ended || !resumable(video.currentTime, video.duration) && video.currentTime > video.duration / 2 });
    };
    const volumeChange = () => callbacks.current.onAudioChange?.({ volume: video.volume, muted: video.muted });
    const progress = () => {
      if (Date.now() - savedAt > 5000) { savedAt = Date.now(); save(); }
    };
    const paused = () => save();
    const beforeClose = () => save();
    const metadata = () => {
      if (!hls) isLive = Number.isFinite(video.duration) && video.duration > 0 && !/127\.0\.0\.1.*\/mp4\//.test(streamUrl) ? false : true;
      if (isLive !== false || resumeChecked || !Number.isFinite(video.duration)) return;
      resumeChecked = true;
      if (!record || !resumable(record.position, video.duration, record.ended)) return;
      pendingResume = true;
      video.pause();
      setResumePrompt(record.position);
      setStatus("");
    };
    chooseResume.current = (resume) => {
      if (disposed) return;
      const position = resume ? record.position : 0;
      // Only promise resume when the source exposes a range covering this point.
      if (resume && !Array.from({ length: video.seekable.length }, (_, i) =>
        position >= video.seekable.start(i) && position <= video.seekable.end(i)).some(Boolean)) {
        setStatus("This source cannot seek to the saved position yet. Try Resume again or Start over.");
        return;
      }
      video.currentTime = position;
      if (!resume && isLive === false) callbacks.current.onProgress?.({ position: 0, duration: video.duration, ended: false, reset: true });
      pendingResume = false;
      setResumePrompt(null);
      setStatus("");
      play();
    };
    video.addEventListener("volumechange", volumeChange);
    video.addEventListener("timeupdate", progress);
    video.addEventListener("pause", paused);
    video.addEventListener("loadedmetadata", metadata);
    video.addEventListener("durationchange", metadata);
    window.addEventListener("pagehide", beforeClose);
    window.addEventListener("beforeunload", beforeClose);
    const fail = () => {
      if (disposed || failed) return;
      failed = true;
      setStatus("This stream could not play. Select it again to retry.");
      callbacks.current.onFailed?.();
    };
    const play = () => {
      metadata();
      if (pendingResume) return;
      return video.play().catch((error) => {
        if (!disposed && !pendingResume && error.name === "NotAllowedError") setStatus("Press Play to start playback.");
      });
    };
    const recover = () => {
      if (disposed || failed || pendingResume) return;
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
    const ended = () => { if (isLive === false) { save(true); setStatus("Finished"); } else recover(); };
    video.addEventListener("ended", ended);
    if (/\.m3u8(?:[?#]|$)/i.test(streamUrl) && Hls.isSupported()) {
      hls = new Hls({ maxBufferLength: 30, maxMaxBufferLength: 60, backBufferLength: 30 });
      hls.attachMedia(video);
      hls.loadSource(streamUrl);
      hls.on(Hls.Events.LEVEL_LOADED, (_event, data) => {
        isLive = data.details.live;
        metadata();
      });
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
      if (disposed || failed || pendingResume || video.ended && isLive === false) return;
      if (video.currentTime !== lastTime) {
        lastTime = video.currentTime;
        lastProgress = Date.now();
      }
      // Respect manual pauses. Startup and buffering still get a timeout.
      if (video.paused && video.readyState >= 2) { lastProgress = Date.now(); return; }
      if (Date.now() - lastProgress > 20000) recover();
    }, 1000);
    return () => {
      save();
      disposed = true;
      chooseResume.current = null;
      video.removeEventListener("volumechange", volumeChange);
      video.removeEventListener("timeupdate", progress);
      video.removeEventListener("pause", paused);
      video.removeEventListener("loadedmetadata", metadata);
      video.removeEventListener("durationchange", metadata);
      window.removeEventListener("pagehide", beforeClose);
      window.removeEventListener("beforeunload", beforeClose);
      clearInterval(watchdog);
      video.removeEventListener("playing", playing);
      video.removeEventListener("waiting", waiting);
      video.removeEventListener("error", error);
      video.removeEventListener("ended", ended);
      hls?.destroy();
      video.pause();
      video.removeAttribute("src");
      video.load();
    };
  }, [streamUrl]);

  return <div style={{ width: "100%", height: "100%", position: "relative", display: "flex", flexDirection: "column", minHeight: 0 }}>
    <div style={{ position: "relative", flex: 1, minHeight: 0 }}>
    <video ref={videoRef} style={{ width: "100%", height: "100%", background: "black" }} controls playsInline />
    {resumePrompt !== null && <div role="dialog" aria-label="Resume playback" style={{ position: "absolute", top: "35%", left: "10%", right: "10%", padding: 20, background: "#141425f2", color: "white", borderRadius: 10 }}>
      <p>Resume from {formatTime(resumePrompt)}?</p>
      <button onClick={() => chooseResume.current?.(true)} style={{ padding: 10, marginRight: 10 }}>Resume</button>
      <button onClick={() => chooseResume.current?.(false)} style={{ padding: 10 }}>Start over</button>
    </div>}
    {status && <div role="status" style={{ position: "absolute", top: 12, left: 12, color: "white", background: "#000b", padding: 8, pointerEvents: "none" }}>{status}</div>}
    </div>
  </div>;
}
