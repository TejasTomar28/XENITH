import '@emotion/react';
import '@emotion/styled';
import '@mui/material/styles';
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './app/App';
import './index.css';

async function prepareApp() {
  if (import.meta.env.VITE_USE_MOCKS === 'true') {
    try {
      const { startWorker } = await import('./mocks/server-setup');
      // Timeout after 3s so a stale/failing SW never blocks rendering
      await Promise.race([
        startWorker(),
        new Promise<void>((resolve) => setTimeout(resolve, 3000)),
      ]);
    } catch (e) {
      console.warn('[MSW] Failed to start mock service worker:', e);
    }
  }
}

prepareApp().then(() => {
  const root = document.getElementById('root');
  if (!root) throw new Error('#root element not found');
  ReactDOM.createRoot(root).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
});
