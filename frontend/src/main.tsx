import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'

// BrowserRouter gives clean URLs (/dashboard, /configuration). In production Spring forwards
// unmatched non-API routes to index.html (see SpaResourceConfig) so deep links and reloads
// resolve to the SPA shell; in development the Vite dev server does the same.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
