import Header from "../../components/common/Header";
import { Target as IconTarget } from "lucide-react";
import { useState, useEffect } from "react";
import { getAttackConfigs } from '../../services/attackConfigService';
import { Link } from "react-router-dom";
import AttackForm from "./AttackConfigForm";

export default function AttackConfig() {
    const [attackForms, setAttackForms] = useState<string[]>([]);

    const moveToLastPage = () => {
        window.location.href = '/gooseFlow';
    }

    const moveToNextPage = () => {
        window.location.href = '/downloadDataset';
    }

    const handleAddAttack = () => {
        setAttackForms(prev => [...prev, Date.now().toString()]);
    };

    // Fetch saved attacks on mount
    useEffect(() => {
        async function fetchAttacks() {
            const attacks = await getAttackConfigs();
            if (attacks && typeof attacks === 'object') {
                setAttackForms(Object.keys(attacks));
            }
        }
        fetchAttacks();
    }, []);

    return(
        <main className="min-h-screen bg-[#ECF0FF] flex flex-col">
            <Header />

            {/* Stepper */}
            <div className="flex justify-between items-center px-16 py-8 text-sm font-medium">
                <Link to="/" className="text-gray-600 hover:underline cursor-pointer">IED Config</Link>
                <Link to="/gooseFlow" className="text-gray-600 hover:underline cursor-pointer">GOOSE Flow Config</Link>
                <span className="text-[#0051A2]">Attack Config</span>
                <Link to="/downloadDataset" className="text-gray-600 hover:underline cursor-pointer">Download Dataset</Link>
            </div>
            <hr className="border border-gray-200" />

            {/* Content */}
            <div className="flex px-8 py-6 flex-col">
                <div className="bg-white rounded-md shadow p-6">
                    <h3 className="flex items-center gap-2 text-[#0051A2] text-lg mb-1 font-bold">
                        <IconTarget className="w-5 h-5"/>
                        Attack Configuration
                    </h3>
                    <p className="text-gray-600 mb-4">
                        Select the attack type for the IEDs
                    </p>
                    <button className="bg-blue-500 hover:bg-blue-700 text-white font-medium px-4 py-2 rounded mb-4"
                        onClick={handleAddAttack}>
                        + Add Attack
                    </button>
                    {attackForms.map((id, idx) => (
                        <AttackForm key={id} attackIndex={idx} onDelete={() => setAttackForms(prev => prev.filter(formId => formId !== id))} />
                    ))}
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