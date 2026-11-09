import { useCallback, useRef, useState } from "react";

// Synchronous writes also retain the final pause/close event without waiting for IPC.
export function useSavedState(key, fallback, validate) {
  const [value, setValue] = useState(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(key));
      return validate(saved) ? saved : fallback;
    } catch { return fallback; }
  });
  const latest = useRef(value);
  const [storageError, setStorageError] = useState("");
  const update = useCallback((change) => {
    const next = typeof change === "function" ? change(latest.current) : change;
    latest.current = next;
    try { localStorage.setItem(key, JSON.stringify(next)); setStorageError(""); }
    catch { setStorageError("Could not save your library settings. Changes may be lost when you close the app."); }
    setValue(next);
  }, [key]);
  return [value, update, storageError];
}
