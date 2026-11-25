import { Activity as IconActivity } from 'lucide-react';
import WizardLayout from '../../components/layout/WizardLayout';
import { ErrorList, NumberField, SelectField } from '../../components/common/Fields';
import { useRunDraft } from '../../context/runDraftContext';
import { errorAt, errorsFor } from '../../run/runDraft';
import type { Benign, SvStrategy } from '../../types/run';

export default function BenignStep() {
  const { draft, update, errors, catalog } = useRunDraft();
  const b = draft.benign;
  const set = <K extends keyof Benign>(key: K, value: Benign[K]) =>
    update(d => ({ ...d, benign: { ...d.benign, [key]: value } }));

  const resolutions = catalog?.electricalSources.length ? catalog.electricalSources : [b.electricalSources];

  return (
    <WizardLayout step="benign" icon={<IconActivity className="w-5 h-5" />} title="Benign Traffic"
      subtitle="Legitimate GOOSE traffic and the Sampled Values (SV) stream it is correlated with. Attacks are generated on top of it.">
      <ErrorList errors={errorsFor(errors, 'benign')} />
      <div className="border border-blue-500 rounded-md p-4 sm:p-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <NumberField label="GOOSE messages" step={1} min={1} value={b.numberOfMessages}
          hint="Benign GOOSE messages to simulate"
          error={errorAt(errors, 'benign.numberOfMessages')} onChange={v => set('numberOfMessages', v)} />
        <NumberField label="Fault probability (%)" step={1} min={0} max={100} value={b.faultProbability}
          hint="Chance of an electrical fault (status change)"
          error={errorAt(errors, 'benign.faultProbability')} onChange={v => set('faultProbability', v)} />
        <SelectField<string> label="Electrical sources" value={b.electricalSources}
          hint="Simulation resolution under electrical-sources/"
          error={errorAt(errors, 'benign.electricalSources')}
          options={resolutions.map(r => ({ value: r, label: r }))}
          onChange={v => set('electricalSources', v)} />
        <SelectField<SvStrategy> label="SV strategy" value={b.svStrategy}
          error={errorAt(errors, 'benign.svStrategy')}
          hint={b.svStrategy === 'DECIMATED'
            ? 'Keeps N SV samples per GOOSE (smaller, faster)'
            : 'Keeps every SV sample (4763 per GOOSE, very large)'}
          options={[
            { value: 'DECIMATED', label: 'Decimated' },
            { value: 'UPSTREAM_FAITHFUL', label: 'Upstream faithful' },
          ]}
          onChange={v => set('svStrategy', v)} />
        {b.svStrategy === 'DECIMATED' && (
          <NumberField label="SV samples per GOOSE" step={1} min={80} value={b.svSamplesPerGoose}
            hint="At least 80, needed for the cycle-window features"
            error={errorAt(errors, 'benign.svSamplesPerGoose')} onChange={v => set('svSamplesPerGoose', v)} />
        )}
      </div>
    </WizardLayout>
  );
}
