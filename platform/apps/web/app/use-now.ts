'use client';

import { useEffect, useState } from 'react';

/** Relógio para contagens regressivas: re-renderiza a cada `intervalMs`. */
export function useNow(intervalMs: number) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(timer);
  }, [intervalMs]);
  return now;
}
