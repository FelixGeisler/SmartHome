import { useEffect, useState } from 'react'

/**
 * Returns the current epoch time in milliseconds, re-rendering on a fixed interval so time-relative
 * text (a reading's age, a stale cue) keeps advancing even while no other state changes. A device
 * that falls silent stops pushing updates, which is exactly when its readings must be seen to age.
 *
 * @param intervalMs how often to re-read the clock; defaults to 30 seconds
 * @returns the latest observed time in epoch milliseconds
 */
export function useNow(intervalMs = 30_000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(id)
  }, [intervalMs])
  return now
}
