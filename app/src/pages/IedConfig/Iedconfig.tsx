import { Group as IconGroup, Server as IconServer } from 'lucide-react';
import Header from '../../components/common/Header';
import IedForm from './IedForm';
import GroupForm from './GroupForm';
import { useState } from 'react';
import { Link } from 'react-router-dom';

export default function IedConfig() {
  const [showIedForm, setShowIedForm] = useState(false);
  const [showGroupForm, setShowGroupForm] = useState(false);

  // just to test the move of pages
  const moveToNextPage = () => {
    window.location.href = '/attackConfig';
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
            <IconServer className="w-5 h-5"/>
            IED Configuration</h3>
          <p className="text-gray-600 mb-4">
            Configure Intelligent Electronic Devices for simulation
          </p>
          <button className="bg-blue-500 hover:bg-blue-700 text-white font-medium px-4 py-2 rounded mb-4"
          onClick={() => setShowIedForm(true)}>
            + Add IED
          </button>
          <div className="bg-[#ECF0FF] text-[#0051A2] px-3 py-2 rounded text-sm">
            <b>Limits:</b> Maximum of 1 Publisher and 10 Subscribers | Current:
            0 Publisher, 0 Subscriber(s)
          </div>
          
          {showIedForm && <IedForm />}

        </div>

        {/* Card Group */}
        <div className="bg-white rounded-md shadow p-6">
          <h3 className="flex items-center gap-2 text-[#0051A2] text-lg mb-1 font-bold">
            <IconGroup className="w-5 h-5"/>
            Group Configuration</h3>
          <p className="text-gray-600 mb-4">
            Organize IEDs into groups for attack application
          </p>
          <button className="bg-blue-500 hover:bg-blue-700 text-white font-medium px-4 py-2 rounded"
          onClick={() => setShowGroupForm(true)}>
            + Add Group
          </button>
          
          {showGroupForm && <GroupForm />}
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
  )
}