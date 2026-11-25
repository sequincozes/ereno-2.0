import { Server as IconServer } from 'lucide-react';
import WizardLayout from '../../components/layout/WizardLayout';
import { ErrorList, TextField } from '../../components/common/Fields';
import { useRunDraft } from '../../context/runDraftContext';
import { errorAt, errorsFor } from '../../run/runDraft';
import type { SetupIED } from '../../types/run';

const FIELDS: { key: keyof SetupIED; label: string; hint?: string }[] = [
  { key: 'iedName', label: 'IED name' },
  { key: 'gocbRef', label: 'GoCB reference', hint: 'e.g. LD/LLN0$GO$gcblA' },
  { key: 'datSet', label: 'Data set', hint: 'e.g. LD/LLN0$IntLockA' },
  { key: 'minTime', label: 'Min time (ms)', hint: 'Fastest retransmission interval' },
  { key: 'maxTime', label: 'Max time (ms)', hint: 'Heartbeat interval when nothing changes' },
  { key: 'timestamp', label: 'Initial timestamp (s)' },
  { key: 'stNum', label: 'Initial stNum' },
  { key: 'sqNum', label: 'Initial sqNum' },
];

export default function IedStep() {
  const { draft, update, errors } = useRunDraft();
  const set = (key: keyof SetupIED, value: string) =>
    update(d => ({ ...d, setupIED: { ...d.setupIED, [key]: value } }));

  return (
    <WizardLayout step="ied" icon={<IconServer className="w-5 h-5" />} title="IED Configuration"
      subtitle="The protection IED that publishes the GOOSE messages. ERENO simulates one publisher per dataset.">
      <ErrorList errors={errorsFor(errors, 'setupIED')} />
      <div className="border border-blue-500 rounded-md p-4 sm:p-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {FIELDS.map(f => (
          <TextField key={f.key} label={f.label} hint={f.hint} value={draft.setupIED[f.key]}
            error={errorAt(errors, `setupIED.${f.key}`)} onChange={v => set(f.key, v)} />
        ))}
      </div>
    </WizardLayout>
  );
}
