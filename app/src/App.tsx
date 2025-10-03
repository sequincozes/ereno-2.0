import { Route, BrowserRouter, Routes } from "react-router-dom";
import Iedconfig from "./pages/IedConfig/Iedconfig";
import GooseFlow from './pages/GooseFlow/GooseFlow';
import AttackConfig from './pages/AttackConfig/AttackConfig';
import DownloadDataset from './pages/DownloadDataset/DownloadDataset';
// import UploadCurrentFile from "./pages/IedConfig/UploadCurrentFile";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Iedconfig />} />
        {/* <Route path="/uploadCurrentFile" element={<UploadCurrentFile />} /> */}
        <Route path="/gooseFlow" element={<GooseFlow />} />
        <Route path="/attackConfig" element={<AttackConfig />} />
        <Route path="/downloadDataset" element={<DownloadDataset />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
