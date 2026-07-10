import { useEffect, useState } from 'react'

// All useNow() consumers share one ticker, so many cards advance from a single interval, not one
// timer each. The interval starts with the first subscriber and stops once the last one leaves.
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
 * Current epoch ms, re-rendering on an interval so time-relative text keeps advancing even while no
 * other state changes. A device that falls silent stops pushing updates, which is exactly when its
 * readings must be seen to age.
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
