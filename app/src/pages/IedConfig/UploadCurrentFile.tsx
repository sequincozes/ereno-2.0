import { FileUser as IconUpload, FileUp as IconDropzone, Paperclip as IconFile, XCircle as IconRemove } from 'lucide-react';
import Header from '../../components/common/Header';
import { Link } from 'react-router-dom';
import { FileUpload } from '@skeletonlabs/skeleton-react';

export default function IedConfig() {

    // just to test the move of pages
    const moveToLastPage = () => {
        window.location.href = '/';
    }

    // just to test the move of pages
    const moveToNextPage = () => {
        window.location.href = '/gooseFlow';
    }
    return (
        <main className="min-h-screen  bg-[#ECF0FF] flex flex-col">
            <Header />

            {/* Stepper */}
            <div className="flex justify-between items-center px-16 py-8 text-sm font-medium">
                <span className="text-[#0051A2]">IED Config</span>
                <Link to="/gooseFlow" className="text-gray-600 hover:underline cursor-pointer">GOOSE Flow Config</Link>
                <Link to="/attackConfig" className="text-gray-600 hover:underline cursor-pointer">Attack Config</Link>
                <Link to="/downloadDataset" className="text-gray-600 hover:underline cursor-pointer">Download Dataset</Link>
            </div>
            <hr className="border border-gray-200" />

            {/* Content */}
            <div className="flex-1 px-8 py-6 flex flex-col gap-6">
                {/* IED Card */}
                <div className="bg-white rounded-md shadow p-6">
                    <h3 className="flex items-center gap-2 text-[#0051A2] text-lg mb-1 font-bold">
                        <IconUpload className="w-5 h-5"/>
                        Upload Current and Voltage Files
                    </h3>
                    <p className="text-gray-600 mb-4">
                        Insert current and voltage files from electrical substations to simulate IEDs
                    </p>
                    <div className="bg-[#ECF0FF] text-[#0051A2] px-3 py-2 rounded text-sm mb-4">
                        <b>Observation:</b> If no Files are Inserted into the Uploader, the System will Use Default Current and Voltage Files
                    </div>
                    <FileUpload
                        name="example"
                        accept={[".out", ".csv"]}
                        maxFiles={5}
                        subtext="Put .out or .csv files."
                        iconInterface={<IconDropzone className="size-8" />}
                        iconFile={<IconFile className="size-4" />}
                        iconFileRemove={<IconRemove className="size-4" />}
                        onFileChange={console.log}
                        onFileReject={console.error}
                        classes="w-full"
                    />
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
    )
}