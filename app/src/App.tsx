import { useContext, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import AuthContext from './context/authContext';
import { RunDraftProvider } from './context/runDraftContext';
import Login from './pages/Login/login';
import IedStep from './pages/IedConfig/IedStep';
import GooseStep from './pages/GooseFlow/GooseStep';
import BenignStep from './pages/Benign/BenignStep';
import AttacksStep from './pages/AttackConfig/AttacksStep';
import GenerateStep from './pages/Generate/GenerateStep';
import RunsPage from './pages/Runs/RunsPage';
import RunDetailPage from './pages/Runs/RunDetailPage';

function RequireAuth({ children }: { children: ReactNode }) {
  const ctx = useContext(AuthContext);
  if (!ctx?.authEnabled) return <>{children}</>;
  if (ctx.loading) return <p className="p-8 text-gray-600">Loading…</p>;
  return ctx.user ? <>{children}</> : <Navigate to="/" replace />;
}

const guard = (el: ReactNode) => <RequireAuth>{el}</RequireAuth>;

function App() {
  return (
    <BrowserRouter>
      <RunDraftProvider>
        <Routes>
          <Route path="/" element={<Login />} />
          <Route path="/ied" element={guard(<IedStep />)} />
          <Route path="/goose" element={guard(<GooseStep />)} />
          <Route path="/benign" element={guard(<BenignStep />)} />
          <Route path="/attacks" element={guard(<AttacksStep />)} />
          <Route path="/generate" element={guard(<GenerateStep />)} />
          <Route path="/runs" element={guard(<RunsPage />)} />
          <Route path="/runs/:id" element={guard(<RunDetailPage />)} />
          {/* Old paths from the first version of the UI */}
          <Route path="/iedconfig" element={<Navigate to="/ied" replace />} />
          <Route path="/gooseFlow" element={<Navigate to="/goose" replace />} />
          <Route path="/attackConfig" element={<Navigate to="/attacks" replace />} />
          <Route path="/downloadDataset" element={<Navigate to="/generate" replace />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </RunDraftProvider>
    </BrowserRouter>
  );
}

export default App;
