import Header from "../../components/common/Header";
import { Target as IconTarget } from "lucide-react";
import { useState } from "react";

export default function AttackConfig() {
    const [showAttackForm, setShowAttackForm] = useState(false);

    // just to test the move of pages
    const moveToNextPage = () => {
        window.location.href = '/downloadDataset';
    }

    return(
        <main className="min-h-screen bg-[#ECF0FF] flex flex-col">
            <Header />

            {/* Stepper */}
            <div className="flex justify-between items-center px-16 py-8 text-sm font-medium">
                <span className="text-gray-600">IED Config</span>
                <span className="text-gray-600">GOOSE Flow Config</span>
                <span className="text-[#0051A2]">Attack Config</span>
                <span className="text-gray-600">Download Dataset</span>
            </div>
            <hr className="border border-gray-200" />

            {/* Content */}
            <div className="flex-1 px-8 py-6 flex flex-col gap-6">
                <div className="bg-white rounded-md shadow p-6">
                    <h3 className="flex items-center gap-2 text-[#0051A2] text-lg mb-1 font-bold">
                        <IconTarget className="w-5 h-5"/>
                        Attack Configuration
                    </h3>
                    <p className="text-gray-600 mb-4">
                        Select the attack type for the IEDs
                    </p>
                    <button className="bg-blue-500 hover:bg-blue-700 text-white font-medium px-4 py-2 rounded mb-4"
                    onClick={() => setShowAttackForm(true)}>
                        + Add Attack
                    </button>
                </div>
            </div>
            {/* Footer */}
            <footer className="flex justify-end px-8 py-6">
                <button className="bg-blue-500 hover:bg-blue-700 text-white font-medium px-6 py-2 rounded"
                onClick={moveToNextPage}>
                Next &gt;
                </button>
            </footer>
        </main>
    );
}