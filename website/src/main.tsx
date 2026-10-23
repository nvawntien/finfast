import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { AppRoutes } from './app/router';
import './styles/tokens.css';
import './styles/global.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter><AppRoutes /></BrowserRouter>
  </StrictMode>,
);
