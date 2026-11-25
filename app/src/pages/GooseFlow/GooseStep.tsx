import { GlobeLock as IconGlobal } from 'lucide-react';
import WizardLayout from '../../components/layout/WizardLayout';
import { CheckboxField, ErrorList, TextField } from '../../components/common/Fields';
import { useRunDraft } from '../../context/runDraftContext';
import { errorAt, errorsFor } from '../../run/runDraft';
import type { GooseFlow } from '../../types/run';

type TextKey = 'goID' | 'ethSrc' | 'ethDst' | 'ethType' | 'gooseAppid' | 'TPID';
type FlagKey = 'ndsCom' | 'test' | 'cbstatus';

const TEXT_FIELDS: { key: TextKey; label: string; hint?: string }[] = [
  { key: 'goID', label: 'GoID' },
  { key: 'ethSrc', label: 'Source MAC', hint: 'e.g. 01:0c:cd:01:2f:80' },
  { key: 'ethDst', label: 'Destination MAC', hint: 'e.g. 01:0c:cd:01:2f:81' },
  { key: 'ethType', label: 'EtherType', hint: 'GOOSE is 0x88B8' },
  { key: 'gooseAppid', label: 'GOOSE APPID', hint: 'e.g. 0x00003001' },
  { key: 'TPID', label: 'TPID', hint: 'VLAN tag, 0x8100' },
];

const FLAGS: { key: FlagKey; label: string }[] = [
  { key: 'ndsCom', label: 'ndsCom' },
  { key: 'test', label: 'Test' },
  { key: 'cbstatus', label: 'Initial cbStatus' },
];

export default function GooseStep() {
  const { draft, update, errors } = useRunDraft();
  const set = <K extends keyof GooseFlow>(key: K, value: GooseFlow[K]) =>
    update(d => ({ ...d, gooseFlow: { ...d.gooseFlow, [key]: value } }));

  return (
    <WizardLayout step="goose" icon={<IconGlobal className="w-5 h-5" />} title="GOOSE Flow Configuration"
      subtitle="Ethernet and GOOSE header fields of the published flow.">
      <ErrorList errors={errorsFor(errors, 'gooseFlow')} />
      <div className="border border-blue-500 rounded-md p-4 sm:p-6 flex flex-col gap-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {TEXT_FIELDS.map(f => (
            <TextField key={f.key} label={f.label} hint={f.hint} value={draft.gooseFlow[f.key]}
              error={errorAt(errors, `gooseFlow.${f.key}`)} onChange={v => set(f.key, v)} />
          ))}
        </div>
        <div className="flex flex-wrap gap-6">
          {FLAGS.map(f => (
            <CheckboxField key={f.key} label={f.label} checked={draft.gooseFlow[f.key]} onChange={v => set(f.key, v)} />
          ))}
        </div>
      </div>
    </WizardLayout>
  );
}
