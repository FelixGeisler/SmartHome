import { useEffect, useState } from 'react'

// Every useNow() consumer shares this one ticker, so a dashboard of many cards advances its
// time-relative text from a single interval rather than one timer per card. The interval starts
// with the first subscriber and stops once the last one leaves.
const subscribers = new Set<(now: number) => void>()
let intervalId: ReturnType<typeof setInterval> | null = null

function startTicking(intervalMs: number): void {
  if (intervalId === null) {
    intervalId = setInterval(() => {
      const now = Date.now()
      subscribers.forEach((notify) => notify(now))
    }, intervalMs)
  }
}

/**
 * Returns the current epoch time in milliseconds, re-rendering on a fixed interval so time-relative
 * text (a reading's age, a stale cue) keeps advancing even while no other state changes. A device
 * that falls silent stops pushing updates, which is exactly when its readings must be seen to age.
 * Every consumer shares one interval, so a dashboard of many cards still runs a single timer.
 *
 * @param intervalMs how often to re-read the clock; defaults to 30 seconds
 * @returns the latest observed time in epoch milliseconds
 */
export function useNow(intervalMs = 30_000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    subscribers.add(setNow)
    startTicking(intervalMs)
    return () => {
      subscribers.delete(setNow)
      if (subscribers.size === 0 && intervalId !== null) {
        clearInterval(intervalId)
        intervalId = null
      }
    }
  }, [intervalMs])
  return now
}
