import Header from "../../components/common/Header";
import { Target as IconTarget } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import Modal from "../../components/feedback/Modal"
import { getIeds } from '../../services/iedService';
import { getGooseFlows } from '../../services/gooseFlowService';
import { getAttackConfigs } from '../../services/attackConfigService';

export default function DowloadDataset() {

    const [showModal, setShowModal] = useState(false);
    const [fileFormat, setFileFormat] = useState(".CSV");
    const [goose, setGoose] = useState(true);
    const [sv, setSv] = useState(false);
    const [datasetName, setDatasetName] = useState("");

    const moveToLastPage = () => {
        window.location.href = '/attackConfig';
    }

    async function handleJsonDownload() {
        // Fetch all config data
        const ieds = await getIeds();
        const gooseFlows = await getGooseFlows();
        const attackConfigs = await getAttackConfigs();
        const config = {
            datasetName,
            fileFormat,
            messageTypes: {
                goose,
                sv
            },
            ieds,
            gooseFlows,
            attackConfigs
        };
        const jsonStr = JSON.stringify(config, null, 2);
        const blob = new Blob([jsonStr], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = (datasetName || 'dataset') + '.json';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        setShowModal(true);
    }

    const fileFormats = [".CSV", ".PCAP", ".ARFF"];

    return(
        <main className="min-h-screen bg-[#ECF0FF] flex flex-col">
            <Header />

            {/* Stepper */}
            <div className="flex justify-between items-center px-16 py-8 text-sm font-medium">
                <Link to="/" className="text-gray-600 hover:underline cursor-pointer">IED Config</Link>
                <Link to="/gooseFlow" className="text-gray-600 hover:underline cursor-pointer">GOOSE Flow Config</Link>
                <Link to="/attackConfig" className="text-gray-600 hover:underline cursor-pointer">Attack Config</Link>
                <span className="text-[#0051A2]">Download Dataset</span>
            </div>
            <hr className="border border-gray-200" />

            {/* Content */}
            <div className="px-8 py-6 flex flex-col">
                <div className="bg-white rounded-md shadow p-6">
                    <h3 className="flex items-center gap-2 text-[#0051A2] text-lg mb-1 font-bold">
                        <IconTarget className="w-5 h-5"/>
                        JSON Download
                    </h3>
                    <p className="text-gray-600 mb-4">
                        Configure the file format of the dataset
                    </p>
                    <div className="flex flex-row gap-8">
                        <div className="flex-1 min-w-[200px]">
                            <label className="block text-sm font-medium text-gray-700">Name <span className="text-red-600">*</span></label>
                            <input
                                type="text"
                                value={datasetName}
                                onChange={e => setDatasetName(e.target.value)}
                                className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm focus:ring focus:ring-blue-500"
                                placeholder="e.g., Dataset_01"
                            />
                        </div>
                        <div className="flex-1 min-w-[200px]">
                            <label className="block text-sm font-medium text-gray-700">File Format <span className="text-red-600">*</span></label>
                            <select
                                value={fileFormat}
                                onChange={e => setFileFormat(e.target.value)}
                                className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm focus:ring focus:ring-blue-500"
                            >
                                {fileFormats.map(fmt => (
                                    <option key={fmt} value={fmt}>{fmt}</option>
                                ))}
                            </select>
                        </div>
                        <div className="flex-1 min-w-[200px]">
                            <span className="block text-sm font-medium text-gray-700">Add type of message (GOOSE by default) <span className="text-red-600">*</span></span>
                            <div className="flex items-center">
                                <input type="checkbox" checked={goose} onChange={e => setGoose(e.target.checked)} />
                                <label className="ml-2">GOOSE messages</label>
                            </div>
                            <div className="flex items-center">
                                <input type="checkbox" checked={sv} onChange={e => setSv(e.target.checked)} />
                                <label className="ml-2">SV messages</label>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            {/* Footer */}
            <footer className="flex px-8 justify-between">
                <button className="border border-gray-800 bg-gray-500 hover:bg-gray-700 text-white font-medium px-6 py-2 rounded"
                    onClick={moveToLastPage}>
                    &lt; Previous
                </button>
                <button className="border border-blue-800 bg-blue-500 hover:bg-blue-700 text-white font-medium px-6 py-2 rounded"
                    onClick={handleJsonDownload}>
                    JSON download
                </button>
                {showModal && <Modal title="Dataset Created!" content="The dataset will be created." />}
            </footer>
        </main>
    );
}