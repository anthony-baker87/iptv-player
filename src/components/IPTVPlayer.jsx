export default function IPTVPlayer({ streamUrl }) {
  return (
    <video
      src={streamUrl}
      style={{ width: "100%", height: "100%", background: "black" }}
      controls
      autoPlay
      playsInline
      muted={false}
    />
  );
}
