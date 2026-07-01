import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Testing Library only auto-registers its cleanup when afterEach is a global;
// we run Vitest with globals disabled, so unmount rendered trees explicitly.
afterEach(cleanup)
