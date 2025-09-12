import { Route, BrowserRouter, Routes } from "react-router-dom";
import Iedconfig from "./pages/IedConfig/Iedconfig";
import GooseFlow from './pages/GooseFlow/GooseFlow';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Iedconfig />} />
        <Route path="/gooseFlow" element={<GooseFlow />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
