import { useState } from 'react';
import { Target as IconTarget } from 'lucide-react';
import WizardLayout from '../../components/layout/WizardLayout';
import { ErrorList } from '../../components/common/Fields';
import { useRunDraft } from '../../context/runDraftContext';
import { newAttack } from '../../run/runDraft';
import AttackEditor from './AttackEditor';

export default function AttacksStep() {
  const { draft, update, errors, catalog } = useRunDraft();
  const templates = catalog?.attacks ?? [];
  const [selected, setSelected] = useState('');
  const chosen = selected || templates[0]?.id || '';

  const add = () => {
    const t = templates.find(x => x.id === chosen);
    if (t) update(d => ({ ...d, attacks: [...d.attacks, newAttack(t)] }));
  };

  const listErrors = errors.filter(e => e.field === 'attacks');

  return (
    <WizardLayout step="attacks" icon={<IconTarget className="w-5 h-5" />} title="Attack Configuration"
      subtitle="Each attack becomes one labelled segment of the dataset. Parameters come from the core's attack templates.">
      <ErrorList errors={listErrors} />
      <div className="flex flex-wrap items-end gap-2 mb-2">
        <label className="flex flex-col">
          <span className="font-medium mb-1 text-sm">Attack</span>
          <select className="border border-gray-300 rounded px-2 py-1 min-w-64" value={chosen}
            onChange={e => setSelected(e.target.value)} disabled={templates.length === 0}>
            {templates.map(t => (
              <option key={t.id} value={t.id}>{t.uc.toUpperCase()} · {t.id.replace(/^uc\d+_/, '').replace(/_/g, ' ')}</option>
            ))}
          </select>
        </label>
        <button type="button" onClick={add} disabled={!chosen}
          className="bg-blue-500 hover:bg-blue-700 disabled:opacity-50 text-white font-medium px-4 py-2 rounded">
          + Add attack
        </button>
      </div>
      {templates.length === 0 && (
        <p className="text-sm text-gray-500">The attack catalog loads from the ERENO API.</p>
      )}
      {draft.attacks.length === 0 && templates.length > 0 && (
        <p className="text-sm text-gray-500 mt-2">No attacks yet. Add at least one.</p>
      )}
      {draft.attacks.map((a, i) => (
        <AttackEditor
          key={a.key}
          index={i}
          attack={a}
          template={templates.find(t => t.id === a.templateId)}
          errors={errors}
          onChange={config => update(d => ({
            ...d, attacks: d.attacks.map(x => (x.key === a.key ? { ...x, config } : x)),
          }))}
          onDuplicate={() => update(d => {
            const copy = { ...a, key: `${a.key}-copy-${Date.now().toString(36)}`, config: structuredClone(a.config) };
            const attacks = [...d.attacks];
            attacks.splice(i + 1, 0, copy);
            return { ...d, attacks };
          })}
          onRemove={() => update(d => ({ ...d, attacks: d.attacks.filter(x => x.key !== a.key) }))}
        />
      ))}
    </WizardLayout>
  );
}
