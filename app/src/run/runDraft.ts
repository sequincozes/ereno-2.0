import type {
  Catalog, CatalogAttack, DraftAttack, FieldError, JsonObject, JsonValue, RunDraft, RunSpec, RunState,
} from '../types/run';

// Pure helpers for the wizard: build a draft from the backend catalog, turn it
// into the POST /api/runs body, and route validation errors back to steps.

const FALLBACK: RunDraft = {
  name: '',
  randomSeed: 42,
  format: 'csv',
  setupIED: {
    iedName: 'Protection IED', gocbRef: 'LD/LLN0$GO$gcblA', datSet: 'LD/LLN0$IntLockA',
    minTime: '4', maxTime: '1000', timestamp: '1', stNum: '1', sqNum: '1',
  },
  gooseFlow: {
    goID: 'IntLockA', ethSrc: '01:0c:cd:01:2f:80', ethDst: '01:0c:cd:01:2f:81', ethType: '0x88B8',
    gooseAppid: '0x00003001', TPID: '0x8100', ndsCom: false, test: false, cbstatus: false,
  },
  benign: {
    numberOfMessages: 10000, faultProbability: 5, svStrategy: 'DECIMATED', svSamplesPerGoose: 80,
    electricalSources: 'res10',
  },
  datasetStructure: {
    messagesPerSegment: 1000, benignMessagesPerSegment: 0, includeBenignSegment: true,
    shuffleSegments: false, binaryClassification: false,
  },
  attacks: [],
};

function pick<T extends object>(base: T, source: JsonObject | undefined): T {
  if (!source) return { ...base };
  const out = { ...base } as Record<string, unknown>;
  for (const key of Object.keys(base)) {
    const v = source[key];
    if (v === undefined || v === null) continue;
    const current = out[key];
    if (typeof current === 'string') out[key] = String(v);
    else if (typeof current === 'number') out[key] = Number(v);
    else if (typeof current === 'boolean') out[key] = Boolean(v);
  }
  return out as T;
}

/** A fresh draft whose defaults come from the core's own action configs. */
export function draftFromCatalog(catalog: Catalog | null): RunDraft {
  const d = catalog?.defaults ?? {};
  const draft: RunDraft = {
    ...FALLBACK,
    setupIED: pick(FALLBACK.setupIED, d.setupIED),
    gooseFlow: pick(FALLBACK.gooseFlow, d.gooseFlow),
    benign: pick(FALLBACK.benign, d.generation),
    datasetStructure: pick(FALLBACK.datasetStructure, d.datasetStructure),
    attacks: [],
  };
  // The shipped benign action is sized for papers (60k messages); start smaller in the UI.
  draft.benign.numberOfMessages = Math.min(draft.benign.numberOfMessages, 10000);
  draft.datasetStructure.messagesPerSegment = Math.min(draft.datasetStructure.messagesPerSegment, 1000);
  if (catalog?.electricalSources.length && !catalog.electricalSources.includes(draft.benign.electricalSources)) {
    draft.benign.electricalSources = catalog.electricalSources[0];
  }
  return draft;
}

let keyCounter = 0;
export function newAttack(template: CatalogAttack): DraftAttack {
  keyCounter += 1;
  return {
    key: `${Date.now().toString(36)}-${keyCounter}`,
    templateId: template.id,
    config: structuredClone(template.template),
  };
}

/** Template keys that document a file rather than configure the attack. */
const NON_PARAMETER_KEYS = new Set(['description', 'comment', 'enabled']);

/** Keys starting with "_" are template metadata (comments, `_options`), never parameters. */
export function isMetaKey(key: string): boolean {
  return key.startsWith('_');
}

function stripDocs(config: JsonObject): JsonObject {
  const out: JsonObject = {};
  for (const [k, v] of Object.entries(config)) {
    if (NON_PARAMETER_KEYS.has(k) || isMetaKey(k)) continue;
    out[k] = v;
  }
  return out;
}

/**
 * Allowed values for a string parameter, declared by a template as
 * `"_options": {"approach": ["A", "B"]}`. Returns undefined for free text.
 */
export function optionsFor(template: JsonObject | undefined, key: string): string[] | undefined {
  const opts = template?._options;
  if (!opts || typeof opts !== 'object' || Array.isArray(opts)) return undefined;
  const list = (opts as JsonObject)[key];
  return Array.isArray(list) && list.every(v => typeof v === 'string') ? (list as string[]) : undefined;
}

export function buildRunSpec(draft: RunDraft): RunSpec {
  return {
    name: draft.name.trim(),
    randomSeed: Math.trunc(Number(draft.randomSeed)),
    output: { format: draft.format },
    benign: { ...draft.benign },
    setupIED: { ...draft.setupIED },
    gooseFlow: { ...draft.gooseFlow },
    datasetStructure: { ...draft.datasetStructure },
    attacks: draft.attacks.map(a => ({ config: stripDocs(a.config) })),
  };
}

/** Rebuilds a draft from a past run's spec ("load into wizard"). */
export function draftFromSpec(spec: RunSpec, catalog: Catalog | null): RunDraft {
  const base = draftFromCatalog(catalog);
  return {
    name: spec.name ?? '',
    randomSeed: spec.randomSeed ?? base.randomSeed,
    format: spec.output?.format ?? base.format,
    setupIED: { ...base.setupIED, ...spec.setupIED },
    gooseFlow: { ...base.gooseFlow, ...spec.gooseFlow },
    benign: { ...base.benign, ...spec.benign },
    datasetStructure: { ...base.datasetStructure, ...spec.datasetStructure },
    attacks: (spec.attacks ?? []).map((a, i) => {
      const type = String(a.config.attackType ?? '');
      const template = catalog?.attacks.find(t => t.attackType === type);
      return { key: `loaded-${i}-${Date.now().toString(36)}`, templateId: template?.id ?? type, config: a.config };
    }),
  };
}

export type StepId = 'ied' | 'goose' | 'benign' | 'attacks' | 'generate';

/** Which wizard step owns a field path returned by the backend validator. */
export function stepForField(field: string): StepId {
  if (field.startsWith('setupIED')) return 'ied';
  if (field.startsWith('gooseFlow')) return 'goose';
  if (field.startsWith('benign')) return 'benign';
  if (field.startsWith('attacks')) return 'attacks';
  return 'generate';
}

export function errorsFor(errors: FieldError[], prefix: string): FieldError[] {
  return errors.filter(e => e.field === prefix || e.field.startsWith(prefix + '.') || e.field.startsWith(prefix + '['));
}

export function errorAt(errors: FieldError[], field: string): string | undefined {
  return errors.find(e => e.field === field)?.message;
}

/** "delayMs" -> "Delay (ms)", "burstDropLen" -> "Burst drop len", "windowS" -> "Window (s)". */
export function humanize(key: string): string {
  let unit = '';
  let base = key;
  if (/[a-z]Ms$/.test(key)) { unit = ' (ms)'; base = key.slice(0, -2); }
  else if (/[a-z]S$/.test(key)) { unit = ' (s)'; base = key.slice(0, -1); }
  const words = base.replace(/_/g, ' ').replace(/([a-z0-9])([A-Z])/g, '$1 $2').toLowerCase().trim();
  return words.charAt(0).toUpperCase() + words.slice(1) + unit;
}

export function isRange(v: JsonValue): v is { min: number; max: number } {
  return typeof v === 'object' && v !== null && !Array.isArray(v)
    && typeof (v as JsonObject).min === 'number' && typeof (v as JsonObject).max === 'number'
    && Object.keys(v).length === 2;
}

export function isActive(state: RunState): boolean {
  return state === 'QUEUED' || state === 'RUNNING';
}
