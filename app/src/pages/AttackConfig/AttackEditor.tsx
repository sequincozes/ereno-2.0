import { useState } from 'react';
import { Trash2 as IconTrash, Copy as IconCopy } from 'lucide-react';
import { CheckboxField, NumberField, SelectField, TextField } from '../../components/common/Fields';
import { humanize, isMetaKey, isRange, optionsFor } from '../../run/runDraft';
import type { CatalogAttack, DraftAttack, FieldError, JsonObject, JsonValue } from '../../types/run';

// Renders any attack template generically: numbers, booleans, strings, number
// lists, {min,max} ranges and nested groups. Fields come from the core's
// config/attacks/*.json, so a new parameter there shows up here unchanged.
// uc11's `rules` is edited as JSON (a visual rule editor is a later step).

const HIDDEN = new Set(['attackType', 'description', 'comment', 'enabled']);

type Props = {
  index: number;
  attack: DraftAttack;
  template?: CatalogAttack;
  errors: FieldError[];
  onChange: (config: JsonObject) => void;
  onDuplicate: () => void;
  onRemove: () => void;
};

function setIn(obj: JsonObject, path: string[], value: JsonValue): JsonObject {
  const [head, ...rest] = path;
  if (rest.length === 0) return { ...obj, [head]: value };
  const child = obj[head];
  return { ...obj, [head]: setIn((child && typeof child === 'object' && !Array.isArray(child) ? child : {}) as JsonObject, rest, value) };
}

export default function AttackEditor({ index, attack, template, errors, onChange, onDuplicate, onRemove }: Props) {
  const base = `attacks[${index}].config`;
  const err = (path: string[]) => errors.find(e => e.field === `${base}.${path.join('.')}`)?.message;
  const update = (path: string[], value: JsonValue) => onChange(setIn(attack.config, path, value));
  const description = template?.description ?? (typeof attack.config.description === 'string' ? attack.config.description : null);

  const renderEntries = (obj: JsonObject, path: string[]) =>
    Object.entries(obj)
      .filter(([k]) => !(path.length === 0 && HIDDEN.has(k)) && !isMetaKey(k))
      .map(([k, v]) => renderValue(k, v, [...path, k]));

  const renderValue = (key: string, value: JsonValue, path: string[]) => {
    const id = path.join('.');
    if (path.length === 1 && key === 'rules' && attack.config.attackType === 'programmable') {
      return <RulesEditor key={id} value={value} error={err(path) ?? childError(path)} onChange={v => update(path, v)} />;
    }
    if (typeof value === 'number') {
      return <NumberField key={id} label={humanize(key)} value={value} error={err(path)} onChange={v => update(path, v)} />;
    }
    if (typeof value === 'boolean') {
      return (
        <div key={id} className="flex items-end pb-1">
          <CheckboxField label={humanize(key)} checked={value} onChange={v => update(path, v)} />
        </div>
      );
    }
    const options = path.length === 1 ? optionsFor(template?.template ?? attack.config, key) : undefined;
    if (typeof value === 'string' && options) {
      const list = options.includes(value) ? options : [value, ...options];
      return (
        <SelectField<string> key={id} label={humanize(key)} value={value} error={err(path)}
          options={list.map(o => ({ value: o, label: o }))} onChange={v => update(path, v)} />
      );
    }
    if (typeof value === 'string') {
      return <TextField key={id} label={humanize(key)} value={value} error={err(path)} onChange={v => update(path, v)} />;
    }
    if (Array.isArray(value)) {
      return (
        <TextField key={id} label={humanize(key)} hint="Comma-separated numbers" error={err(path)}
          value={value.join(', ')}
          onChange={v => update(path, v.split(',').map(s => s.trim()).filter(s => s !== '').map(Number).filter(Number.isFinite))} />
      );
    }
    if (value && typeof value === 'object' && isRange(value)) {
      return (
        <div key={id} className="flex flex-col min-w-0">
          <span className="font-medium mb-1 text-sm">{humanize(key)}</span>
          <div className="grid grid-cols-2 gap-2">
            <NumberField label="min" value={value.min} error={err([...path, 'min'])} onChange={v => update([...path, 'min'], v)} />
            <NumberField label="max" value={value.max} error={err([...path, 'max'])} onChange={v => update([...path, 'max'], v)} />
          </div>
        </div>
      );
    }
    if (value && typeof value === 'object') {
      return (
        <fieldset key={id} className="col-span-full border border-gray-200 rounded-md p-3">
          <legend className="px-1 text-sm font-semibold text-[#0051A2]">{humanize(key)}</legend>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">{renderEntries(value as JsonObject, path)}</div>
        </fieldset>
      );
    }
    return null;
  };

  const childError = (path: string[]) =>
    errors.filter(e => e.field.startsWith(`${base}.${path.join('.')}.`)).map(e => `${e.field.slice(base.length + 1)}: ${e.message}`).join(' · ') || undefined;

  return (
    <div className="border border-blue-500 rounded-md p-4 sm:p-6 mt-4">
      <div className="flex flex-wrap items-start justify-between gap-2 mb-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="rounded bg-[#0051A2] px-2 py-0.5 text-xs font-bold text-white uppercase">{template?.uc ?? '?'}</span>
            <span className="font-semibold">{String(attack.config.attackType)}</span>
            <span className="text-xs text-gray-500">({attack.templateId})</span>
          </div>
          {description && <p className="text-sm text-gray-600 mt-1">{description}</p>}
        </div>
        <div className="flex gap-2">
          <button type="button" onClick={onDuplicate} title="Duplicate"
            className="p-2 rounded border border-blue-300 bg-blue-100 text-blue-600 hover:bg-blue-200">
            <IconCopy size={20} />
          </button>
          <button type="button" onClick={onRemove} title="Remove"
            className="p-2 rounded border border-red-300 bg-red-100 text-red-600 hover:bg-red-200">
            <IconTrash size={20} />
          </button>
        </div>
      </div>
      {err(['attackType']) && <p className="mb-2 text-sm text-red-600">{err(['attackType'])}</p>}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {renderEntries(attack.config, [])}
      </div>
    </div>
  );
}

function RulesEditor({ value, error, onChange }: { value: JsonValue; error?: string; onChange: (v: JsonValue) => void }) {
  const [text, setText] = useState(() => JSON.stringify(value, null, 2));
  const [parseError, setParseError] = useState<string | null>(null);

  return (
    <div className="col-span-full flex flex-col">
      <span className="font-medium mb-1 text-sm">Rules (JSON)</span>
      <textarea
        className={`font-mono text-xs border rounded p-2 min-h-56 ${parseError || error ? 'border-red-500 bg-red-50' : 'border-gray-300'}`}
        spellCheck={false}
        value={text}
        onChange={e => {
          setText(e.target.value);
          try {
            const parsed = JSON.parse(e.target.value) as JsonValue;
            setParseError(null);
            onChange(parsed);
          } catch (ex) {
            setParseError((ex as Error).message);
          }
        }}
      />
      <span className="mt-1 text-xs text-gray-500">
        Slots <code>{'{"r0": {op, field, value, fraction, when}}'}</code> applied in key order.
        ops: set, add, scale, drop, duplicate · fields: cbStatus, stNum, sqNum, gooseTimeAllowedtoLive, confRev, t, timestamp
        (t/timestamp in seconds, add only) · when.cmp: always, eq, ne, gt, lt, gte, lte.
      </span>
      {parseError && <span className="mt-1 text-xs text-red-600">Invalid JSON: {parseError}</span>}
      {!parseError && error && <span className="mt-1 text-xs text-red-600">{error}</span>}
    </div>
  );
}
