import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Download as IconDownload, FileText as IconFile } from 'lucide-react';
import Header from '../../components/common/Header';
import { api } from '../../services/api';
import { useRunDraft } from '../../context/runDraftContext';
import { draftFromSpec, isActive } from '../../run/runDraft';
import type { RunDetail } from '../../types/run';
import RunStateBadge from './RunStateBadge';

const STEP_LABEL: Record<string, string> = {
  create_benign: 'Generating benign traffic',
  create_attack_dataset: 'Generating attack segments',
};

function formatBytes(n: number) {
  if (n < 1024) return `${n} B`;
  if (n < 1024 ** 2) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 ** 2).toFixed(1)} MB`;
}

export default function RunDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const { catalog, replace } = useRunDraft();
  const [detail, setDetail] = useState<RunDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const logRef = useRef<HTMLPreElement>(null);

  const load = useCallback(async () => {
    try {
      const d = await api.getRun(id);
      setDetail(d);
      setError(null);
      return d;
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      return null;
    }
  }, [id]);

  useEffect(() => {
    let timer: number | undefined;
    let stopped = false;
    const tick = async () => {
      const d = await load();
      if (!stopped && (!d || isActive(d.run.state))) timer = window.setTimeout(tick, 1500);
    };
    tick();
    return () => { stopped = true; window.clearTimeout(timer); };
  }, [load]);

  useEffect(() => {
    if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
  }, [detail?.logTail]);

  const run = detail?.run;
  const active = run ? isActive(run.state) : false;
  const outputs = (detail?.files ?? []).filter(f => f.path.startsWith('output/'));
  const others = (detail?.files ?? []).filter(f => !f.path.startsWith('output/') && !f.path.startsWith('steps/'));

  const rerun = async () => {
    if (!detail?.spec) return;
    setBusy(true);
    try {
      const created = await api.createRun(detail.spec);
      navigate(`/runs/${created.id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!run) return;
    if (!active && !window.confirm('Delete this run and all its files?')) return;
    setBusy(true);
    try {
      await api.deleteRun(run.id);
      if (active) await load();
      else navigate('/runs');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const download = (path: string) => api.download(id, path).catch(e => setError(e.message));

  return (
    <main className="min-h-screen bg-[#ECF0FF] flex flex-col">
      <Header />
      <div className="flex-1 px-4 sm:px-8 py-6 flex flex-col gap-6">
        <Link to="/runs" className="text-sm text-blue-700 underline">&lt; All runs</Link>
        {error && <p className="rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800">{error}</p>}
        {!detail && !error && <p className="text-sm text-gray-500">Loading…</p>}
        {run && (
          <>
            <section className="bg-white rounded-md shadow p-4 sm:p-6">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <h3 className="text-[#0051A2] text-lg font-bold flex items-center gap-2">
                    {run.name || run.id} <RunStateBadge state={run.state} />
                  </h3>
                  <p className="text-sm text-gray-600 mt-1">
                    Run <code>{run.id}</code> · seed {run.randomSeed} · created {new Date(run.createdAt).toLocaleString()}
                    {run.finishedAt && <> · finished {new Date(run.finishedAt).toLocaleString()}</>}
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  {!active && detail?.spec && (
                    <>
                      <button type="button" disabled={busy} onClick={rerun}
                        className="bg-blue-500 hover:bg-blue-700 disabled:opacity-60 text-white text-sm px-3 py-1.5 rounded">
                        Run again
                      </button>
                      <button type="button" onClick={() => { replace(draftFromSpec(detail.spec!, catalog)); navigate('/ied'); }}
                        className="border border-blue-500 text-blue-700 hover:bg-blue-50 text-sm px-3 py-1.5 rounded">
                        Edit as new
                      </button>
                    </>
                  )}
                  <button type="button" disabled={busy} onClick={remove}
                    className="border border-red-400 text-red-700 hover:bg-red-50 disabled:opacity-60 text-sm px-3 py-1.5 rounded">
                    {active ? 'Cancel' : 'Delete'}
                  </button>
                </div>
              </div>

              {active && (
                <div className="mt-4">
                  <div className="flex justify-between text-sm text-gray-700 mb-1">
                    <span>{run.state === 'QUEUED' ? 'Waiting for a free worker…' : STEP_LABEL[run.step ?? ''] ?? run.step}</span>
                    <span>Step {Math.max(run.stepIndex, 0)} of {run.stepCount}</span>
                  </div>
                  <div className="h-2 rounded bg-gray-200 overflow-hidden">
                    <div className="h-2 bg-blue-500 transition-[width] duration-500"
                      style={{ width: `${(Math.max(run.stepIndex - 1, 0) / run.stepCount) * 100 + (run.state === 'RUNNING' ? 100 / run.stepCount / 2 : 0)}%` }} />
                  </div>
                </div>
              )}
              {run.error && run.state !== 'SUCCEEDED' && (
                <p className="mt-4 rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800">{run.error}</p>
              )}

              {outputs.length > 0 && (
                <div className="mt-6">
                  <h4 className="font-semibold text-[#0051A2] mb-2">Dataset</h4>
                  {outputs.map(f => (
                    <button key={f.path} type="button" onClick={() => download(f.path)}
                      className="flex items-center gap-2 bg-green-600 hover:bg-green-700 text-white text-sm px-4 py-2 rounded mb-2">
                      <IconDownload className="w-4 h-4" /> {f.path.replace('output/', '')} ({formatBytes(f.bytes)})
                    </button>
                  ))}
                </div>
              )}
              {others.length > 0 && (
                <div className="mt-4">
                  <h4 className="font-semibold text-[#0051A2] mb-2">Other files</h4>
                  <ul className="text-sm">
                    {others.map(f => (
                      <li key={f.path}>
                        <button type="button" onClick={() => download(f.path)} className="flex items-center gap-1 text-blue-700 underline">
                          <IconFile className="w-3.5 h-3.5" /> {f.path}
                        </button>
                        <span className="text-gray-500 ml-5">{formatBytes(f.bytes)}</span>
                      </li>
                    ))}
                  </ul>
                  <p className="text-xs text-gray-500 mt-2">
                    <code>manifest.json</code> records the seed, the git commit of the backend and a SHA-256 for each file;
                    <code> spec.json</code> is the exact configuration, enough to regenerate this dataset.
                  </p>
                </div>
              )}
            </section>

            <section className="bg-white rounded-md shadow p-4 sm:p-6">
              <h4 className="font-semibold text-[#0051A2] mb-2">Log</h4>
              <pre ref={logRef} className="max-h-96 overflow-auto rounded bg-gray-900 text-gray-100 text-xs p-3 whitespace-pre-wrap break-all">
                {detail?.logTail || 'No output yet.'}
              </pre>
            </section>
          </>
        )}
      </div>
    </main>
  );
}
