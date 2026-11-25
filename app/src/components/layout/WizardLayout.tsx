import type { ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Header from '../common/Header';
import { useRunDraft } from '../../context/runDraftContext';
import { stepForField, type StepId } from '../../run/runDraft';

const STEPS: { id: StepId; label: string; path: string }[] = [
  { id: 'ied', label: 'IED Config', path: '/ied' },
  { id: 'goose', label: 'GOOSE Flow', path: '/goose' },
  { id: 'benign', label: 'Benign Traffic', path: '/benign' },
  { id: 'attacks', label: 'Attacks', path: '/attacks' },
  { id: 'generate', label: 'Generate', path: '/generate' },
];

type Props = {
  step: StepId;
  icon: ReactNode;
  title: string;
  subtitle: string;
  children: ReactNode;
  /** Replaces the default "Next" button on the last step. */
  primaryAction?: ReactNode;
};

export default function WizardLayout({ step, icon, title, subtitle, children, primaryAction }: Props) {
  const navigate = useNavigate();
  const { errors, catalogError, reloadCatalog } = useRunDraft();
  const index = STEPS.findIndex(s => s.id === step);
  const prev = STEPS[index - 1];
  const next = STEPS[index + 1];
  const stepsWithErrors = new Set(errors.map(e => stepForField(e.field)));

  return (
    <main className="min-h-screen bg-[#ECF0FF] flex flex-col">
      <Header />

      <ol className="flex flex-wrap justify-between gap-2 px-4 sm:px-16 py-6 text-sm font-medium">
        {STEPS.map((s, i) => (
          <li key={s.id} className="flex items-center gap-2">
            <span className={`flex h-6 w-6 items-center justify-center rounded-full text-xs ${
              s.id === step ? 'bg-[#0051A2] text-white' : 'bg-white text-gray-600'}`}>{i + 1}</span>
            {s.id === step
              ? <span className="text-[#0051A2]">{s.label}</span>
              : <Link to={s.path} className="text-gray-600 hover:underline">{s.label}</Link>}
            {stepsWithErrors.has(s.id) && (
              <span className="h-2 w-2 rounded-full bg-red-500" title="This step has errors" />
            )}
          </li>
        ))}
      </ol>
      <hr className="border border-gray-200" />

      {catalogError && (
        <div className="mx-4 sm:mx-8 mt-4 rounded-md border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900 flex flex-wrap items-center justify-between gap-2">
          <span>{catalogError} Start it with <code>./dev.ps1</code> (or <code>./dev.sh</code>) from the repository root.</span>
          <button type="button" className="underline" onClick={reloadCatalog}>Retry</button>
        </div>
      )}

      <div className="flex-1 px-4 sm:px-8 py-6 flex flex-col gap-6">
        <section className="bg-white rounded-md shadow p-4 sm:p-6">
          <h3 className="flex items-center gap-2 text-[#0051A2] text-lg mb-1 font-bold">
            {icon}
            {title}
          </h3>
          <p className="text-gray-600 mb-4">{subtitle}</p>
          {children}
        </section>
      </div>

      <footer className="flex justify-between gap-4 px-4 sm:px-8 pb-8">
        {prev ? (
          <button type="button" onClick={() => navigate(prev.path)}
            className="border border-gray-800 bg-gray-500 hover:bg-gray-700 text-white font-medium px-6 py-2 rounded">
            &lt; Previous
          </button>
        ) : <span />}
        {primaryAction ?? (next && (
          <button type="button" onClick={() => navigate(next.path)}
            className="border border-blue-800 bg-blue-500 hover:bg-blue-700 text-white font-medium px-6 py-2 rounded">
            Next &gt;
          </button>
        ))}
      </footer>
    </main>
  );
}
