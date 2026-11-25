import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Rocket as IconRocket } from 'lucide-react';
import WizardLayout from '../../components/layout/WizardLayout';
import { CheckboxField, ErrorList, NumberField, SelectField, TextField } from '../../components/common/Fields';
import { useRunDraft } from '../../context/runDraftContext';
import { api, ApiError } from '../../services/api';
import { buildRunSpec, errorAt, stepForField } from '../../run/runDraft';
import type { DatasetStructure, OutputFormat } from '../../types/run';
import PresetsPanel from './PresetsPanel';

export default function GenerateStep() {
  const { draft, update, errors, setErrors, reset } = useRunDraft();
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const ds = draft.datasetStructure;
  const setDs = <K extends keyof DatasetStructure>(key: K, value: DatasetStructure[K]) =>
    update(d => ({ ...d, datasetStructure: { ...d.datasetStructure, [key]: value } }));

  const generate = async () => {
    setSubmitting(true);
    setMessage(null);
    try {
      const run = await api.createRun(buildRunSpec(draft));
      setErrors([]);
      navigate(`/runs/${run.id}`);
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors.length > 0) {
        setErrors(e.fieldErrors);
        setMessage('Some fields need fixing. Steps with errors are marked in red above.');
      } else {
        setMessage(e instanceof Error ? e.message : String(e));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const otherStepErrors = errors.filter(e => stepForField(e.field) !== 'generate');
  const ownErrors = errors.filter(e => stepForField(e.field) === 'generate');

  return (
    <WizardLayout step="generate" icon={<IconRocket className="w-5 h-5" />} title="Generate Dataset"
      subtitle="Dataset layout and output. The backend generates the benign traffic, then the attack segments, and keeps every file of the run."
      primaryAction={
        <button type="button" onClick={generate} disabled={submitting}
          className="border border-green-800 bg-green-600 hover:bg-green-700 disabled:opacity-60 text-white font-medium px-6 py-2 rounded">
          {submitting ? 'Submitting…' : 'Generate dataset'}
        </button>
      }>
      {message && (
        <div className="mb-4 rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800">{message}</div>
      )}
      <ErrorList errors={[...ownErrors, ...otherStepErrors]} />

      <div className="border border-blue-500 rounded-md p-4 sm:p-6 flex flex-col gap-6">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <TextField label="Dataset name" value={draft.name} placeholder="e.g. replay_vs_grayhole"
            error={errorAt(errors, 'name')} onChange={v => update(d => ({ ...d, name: v }))} />
          <NumberField label="Random seed" step={1} value={draft.randomSeed}
            hint="Same configuration + same seed = same dataset"
            error={errorAt(errors, 'randomSeed')} onChange={v => update(d => ({ ...d, randomSeed: v }))} />
          <SelectField<OutputFormat> label="Output format" value={draft.format}
            error={errorAt(errors, 'output.format')}
            options={[{ value: 'csv', label: 'CSV' }, { value: 'arff', label: 'ARFF (Weka)' }]}
            onChange={v => update(d => ({ ...d, format: v }))} />
        </div>

        <div>
          <h4 className="font-semibold text-[#0051A2] mb-2">Dataset structure</h4>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <NumberField label="Messages per attack segment" step={1} min={1} value={ds.messagesPerSegment}
              error={errorAt(errors, 'datasetStructure.messagesPerSegment')}
              onChange={v => setDs('messagesPerSegment', v)} />
            <NumberField label="Benign segment size" step={1} min={0} value={ds.benignMessagesPerSegment}
              hint="0 = same as an attack segment"
              error={errorAt(errors, 'datasetStructure.benignMessagesPerSegment')}
              onChange={v => setDs('benignMessagesPerSegment', v)} />
          </div>
          <div className="flex flex-wrap gap-6 mt-4">
            <CheckboxField label="Include benign segment" checked={ds.includeBenignSegment}
              onChange={v => setDs('includeBenignSegment', v)} />
            <CheckboxField label="Shuffle segments" checked={ds.shuffleSegments}
              onChange={v => setDs('shuffleSegments', v)} />
            <CheckboxField label="Binary labels (normal / attack)" checked={ds.binaryClassification}
              onChange={v => setDs('binaryClassification', v)} />
          </div>
        </div>

        <div className="rounded-md bg-[#ECF0FF] px-4 py-3 text-sm text-[#0051A2]">
          <b>Summary:</b> {draft.benign.numberOfMessages.toLocaleString()} benign GOOSE messages
          ({draft.benign.electricalSources}, {draft.benign.faultProbability}% faults) ·{' '}
          {draft.attacks.length} attack segment{draft.attacks.length === 1 ? '' : 's'}
          {draft.attacks.length > 0 && <> ({draft.attacks.map(a => String(a.config.attackType)).join(', ')})</>} ·
          seed {draft.randomSeed} · {draft.format.toUpperCase()}
        </div>

        <div className="flex justify-end">
          <button type="button" className="text-sm text-gray-600 underline" onClick={() => {
            if (confirmReset()) reset();
          }}>Reset form to defaults</button>
        </div>
      </div>

      <PresetsPanel />
    </WizardLayout>
  );
}

function confirmReset(): boolean {
  // Inline confirmation keeps the flow simple; reset only touches this browser's draft.
  return window.confirm('Reset every step to the default values?');
}
