import Header from "../../components/common/Header";
import { GlobeLock as IconGlobal } from "lucide-react";
import { Link } from "react-router-dom";
import GooseFlowForm from "./GooseFlowForm";

export default function GooseFlow() {

    const moveToLastPage = () => {
        window.location.href = '/uploadCurrentFile';
    }

    const moveToNextPage = () => {
        window.location.href = '/AttackConfig';
    }

    return(
        <main className="min-h-screen bg-[#ECF0FF] flex flex-col">
            <Header />

            {/* Stepper */}
            <div className="flex justify-between items-center px-16 py-8 text-sm font-medium">
                <Link to="/iedconfig" className="text-gray-600 hover:underline cursor-pointer">IED Config</Link>
                <span className="text-[#0051A2]">GOOSE Flow Config</span>
                <Link to="/attackConfig" className="text-gray-600 hover:underline cursor-pointer">Attack Config</Link>
                <Link to="/downloadDataset" className="text-gray-600 hover:underline cursor-pointer">Download JSON</Link>
            </div>
            <hr className="border border-gray-200" />

            {/* Content */}
            <div className="flex-1 px-8 py-6 flex flex-col gap-6">
                <div className="bg-white rounded-md shadow p-6">
                    <h3 className="flex items-center gap-2 text-[#0051A2] text-lg mb-1 font-bold">
                        <IconGlobal className="w-5 h-5"/>
                        GOOSE Flow Configuration
                    </h3>
                    <p className="text-gray-600 mb-4">
                        Configure GOOSE message flow parameters
                    </p>

                    <GooseFlowForm />
                </div>
            </div>
            
            {/* Footer */}
            <footer className="flex px-8 justify-between">
                <button className="border border-gray-800 bg-gray-500 hover:bg-gray-700 text-white font-medium px-6 py-2 rounded"
                onClick={moveToLastPage}>
                    &lt; Previous
                </button>
                <button className="border border-blue-800 bg-blue-500 hover:bg-blue-700 text-white font-medium px-6 py-2 rounded"
                onClick={moveToNextPage}>
                    Next &gt;
                </button>
            </footer>
        </main>
    );
}