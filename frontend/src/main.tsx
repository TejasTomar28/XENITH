import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './app/App';
import './index.css';

async function prepareApp() {
  if (import.meta.env.VITE_USE_MOCKS === 'true') {
    const { startWorker } = await import('./mocks/server-setup');
    await startWorker();
  }
}

prepareApp().then(() => {
  ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
});
