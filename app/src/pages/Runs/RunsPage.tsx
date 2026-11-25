import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ListChecks as IconList } from 'lucide-react';
import Header from '../../components/common/Header';
import { api } from '../../services/api';
import type { RunStatus } from '../../types/run';
import RunStateBadge from './RunStateBadge';
import { isActive } from '../../run/runDraft';

export default function RunsPage() {
  const [runs, setRuns] = useState<RunStatus[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let timer: number | undefined;
    let stopped = false;
    const load = async () => {
      try {
        const list = await api.listRuns();
        if (stopped) return;
        setRuns(list);
        setError(null);
        timer = window.setTimeout(load, list.some(r => isActive(r.state)) ? 2000 : 10000);
      } catch (e) {
        if (stopped) return;
        setError(e instanceof Error ? e.message : String(e));
        timer = window.setTimeout(load, 5000);
      }
    };
    load();
    return () => { stopped = true; window.clearTimeout(timer); };
  }, []);

  return (
    <main className="min-h-screen bg-[#ECF0FF] flex flex-col">
      <Header />
      <div className="flex-1 px-4 sm:px-8 py-6">
        <section className="bg-white rounded-md shadow p-4 sm:p-6">
          <h3 className="flex items-center gap-2 text-[#0051A2] text-lg mb-1 font-bold">
            <IconList className="w-5 h-5" /> Runs
          </h3>
          <p className="text-gray-600 mb-4">Every dataset generation, newest first. Open one to follow it and download its files.</p>
          {error && <p className="mb-4 rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800">{error}</p>}
          {runs === null && !error && <p className="text-sm text-gray-500">Loading…</p>}
          {runs?.length === 0 && (
            <p className="text-sm text-gray-500">No runs yet. <Link className="text-blue-700 underline" to="/ied">Configure a dataset</Link>.</p>
          )}
          {runs && runs.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-gray-500 border-b">
                    <th className="py-2 pr-4 font-medium">Name</th>
                    <th className="py-2 pr-4 font-medium">State</th>
                    <th className="py-2 pr-4 font-medium">Progress</th>
                    <th className="py-2 pr-4 font-medium">Seed</th>
                    <th className="py-2 pr-4 font-medium">Created</th>
                  </tr>
                </thead>
                <tbody>
                  {runs.map(r => (
                    <tr key={r.id} className="border-b last:border-0 hover:bg-gray-50">
                      <td className="py-2 pr-4">
                        <Link to={`/runs/${r.id}`} className="text-blue-700 underline">{r.name || r.id}</Link>
                      </td>
                      <td className="py-2 pr-4"><RunStateBadge state={r.state} /></td>
                      <td className="py-2 pr-4 text-gray-600">
                        {r.state === 'RUNNING' ? `${r.stepIndex}/${r.stepCount} · ${r.step}` : r.error ?? ''}
                      </td>
                      <td className="py-2 pr-4 tabular-nums">{r.randomSeed}</td>
                      <td className="py-2 pr-4 text-gray-600 whitespace-nowrap">{new Date(r.createdAt).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
