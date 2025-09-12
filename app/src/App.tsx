import { Route, BrowserRouter, Routes } from "react-router-dom";
import Iedconfig from "./pages/IedConfig/Iedconfig";
import GooseFlow from './pages/GooseFlow/GooseFlow';
import AttackConfig from './pages/AttackConfig/AttackConfig';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Iedconfig />} />
        <Route path="/gooseFlow" element={<GooseFlow />} />
        <Route path="/attackConfig" element={<AttackConfig />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
