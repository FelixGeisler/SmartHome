import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// jsdom has no ResizeObserver, which react-grid-layout's width hook and SensorChart rely on.
// A no-op stand-in keeps them from throwing under test.
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

// Testing Library only auto-registers its cleanup when afterEach is a global;
// we run Vitest with globals disabled, so unmount rendered trees explicitly.
afterEach(cleanup)
