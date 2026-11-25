import type { ReactNode } from 'react';

// Small controlled inputs used by every step. `error` comes from the backend
// validator (POST /api/runs 400) and is shown under the field.

type BaseProps = {
  label: string;
  error?: string;
  hint?: ReactNode;
};

function Wrapper({ label, error, hint, children }: BaseProps & { children: ReactNode }) {
  return (
    <label className="flex flex-col min-w-0">
      <span className="font-medium mb-1 text-sm">{label}</span>
      {children}
      {hint && !error && <span className="mt-1 text-xs text-gray-500">{hint}</span>}
      {error && <span className="mt-1 text-xs text-red-600">{error}</span>}
    </label>
  );
}

const inputClass = (error?: string) =>
  `border rounded px-2 py-1 w-full ${error ? 'border-red-500 bg-red-50' : 'border-gray-300'}`;

export function TextField({ label, error, hint, value, onChange, placeholder }: BaseProps & {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}) {
  return (
    <Wrapper label={label} error={error} hint={hint}>
      <input type="text" className={inputClass(error)} value={value} placeholder={placeholder}
        onChange={e => onChange(e.target.value)} />
    </Wrapper>
  );
}

export function NumberField({ label, error, hint, value, onChange, step, min, max }: BaseProps & {
  value: number;
  onChange: (v: number) => void;
  step?: number | 'any';
  min?: number;
  max?: number;
}) {
  return (
    <Wrapper label={label} error={error} hint={hint}>
      <input type="number" className={inputClass(error)} value={Number.isFinite(value) ? value : ''}
        step={step ?? 'any'} min={min} max={max}
        onChange={e => onChange(e.target.value === '' ? Number.NaN : Number(e.target.value))} />
    </Wrapper>
  );
}

export function SelectField<T extends string>({ label, error, hint, value, onChange, options }: BaseProps & {
  value: T;
  onChange: (v: T) => void;
  options: { value: T; label: string }[];
}) {
  return (
    <Wrapper label={label} error={error} hint={hint}>
      <select className={inputClass(error)} value={value} onChange={e => onChange(e.target.value as T)}>
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </Wrapper>
  );
}

export function CheckboxField({ label, checked, onChange, hint }: {
  label: string;
  checked: boolean;
  onChange: (v: boolean) => void;
  hint?: string;
}) {
  return (
    <label className="flex items-center gap-2 text-sm" title={hint}>
      <input type="checkbox" className="checkbox" checked={checked} onChange={e => onChange(e.target.checked)} />
      {label}
    </label>
  );
}

export function ErrorList({ errors }: { errors: { field: string; message: string }[] }) {
  if (errors.length === 0) return null;
  return (
    <ul className="mb-4 rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800 list-disc list-inside">
      {errors.map(e => <li key={e.field + e.message}><code>{e.field}</code>: {e.message}</li>)}
    </ul>
  );
}
