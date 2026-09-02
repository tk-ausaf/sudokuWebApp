import { useEffect, useRef } from 'react';

/** Calls `callback(value)` `delayMs` after `value` last changed, skipping the initial mount. */
export function useAutosave(value, callback, delayMs = 1200) {
  const timeoutRef = useRef(null);
  const callbackRef = useRef(callback);
  const isFirstRun = useRef(true);
  callbackRef.current = callback;

  useEffect(() => {
    if (isFirstRun.current) {
      isFirstRun.current = false;
      return undefined;
    }
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }
    timeoutRef.current = setTimeout(() => {
      callbackRef.current(value);
    }, delayMs);
    return () => clearTimeout(timeoutRef.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, delayMs]);
}