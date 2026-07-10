import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'
import './index.css'
import App from './App.tsx'
import { AuthGate } from './components/AuthGate.tsx'

// Spring (see SpaResourceConfig), and Vite in dev, forward unmatched non-API routes to index.html
// so deep links and reloads resolve to the SPA shell.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthGate>
        <App />
      </AuthGate>
    </BrowserRouter>
  </StrictMode>,
)
