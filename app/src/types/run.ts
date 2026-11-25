// Shapes shared with the ERENO API (api/src/main/java/br/ufu/facom/ereno/api).
// Field names follow the core's action configs on purpose: the UI speaks the
// core schema, the backend only validates and copies it into step configs.

export type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };
export type JsonObject = { [key: string]: JsonValue };

export type OutputFormat = 'csv' | 'arff';
export type SvStrategy = 'DECIMATED' | 'UPSTREAM_FAITHFUL';

export interface SetupIED {
  iedName: string;
  gocbRef: string;
  datSet: string;
  minTime: string;
  maxTime: string;
  timestamp: string;
  stNum: string;
  sqNum: string;
}

export interface GooseFlow {
  goID: string;
  ethSrc: string;
  ethDst: string;
  ethType: string;
  gooseAppid: string;
  TPID: string;
  ndsCom: boolean;
  test: boolean;
  cbstatus: boolean;
}

export interface Benign {
  numberOfMessages: number;
  faultProbability: number;
  svStrategy: SvStrategy;
  svSamplesPerGoose: number;
  electricalSources: string;
}

export interface DatasetStructure {
  messagesPerSegment: number;
  benignMessagesPerSegment: number;
  includeBenignSegment: boolean;
  shuffleSegments: boolean;
  binaryClassification: boolean;
}

/** One attack in the wizard: which catalog template it came from and its edited config. */
export interface DraftAttack {
  key: string;
  templateId: string;
  config: JsonObject;
}

/** Everything the wizard edits. Persisted in the browser between steps. */
export interface RunDraft {
  name: string;
  randomSeed: number;
  format: OutputFormat;
  setupIED: SetupIED;
  gooseFlow: GooseFlow;
  benign: Benign;
  datasetStructure: DatasetStructure;
  attacks: DraftAttack[];
}

/** Body of POST /api/runs. */
export interface RunSpec {
  name: string;
  randomSeed: number;
  output: { format: OutputFormat };
  benign: Benign;
  setupIED: SetupIED;
  gooseFlow: GooseFlow;
  datasetStructure: DatasetStructure;
  attacks: { config: JsonObject }[];
}

export interface CatalogAttack {
  id: string;
  uc: string;
  attackType: string;
  description: string | null;
  template: JsonObject;
}

export interface Catalog {
  attacks: CatalogAttack[];
  electricalSources: string[];
  svStrategies: SvStrategy[];
  formats: OutputFormat[];
  defaults: {
    generation?: Partial<Benign> & JsonObject;
    gooseFlow?: Partial<GooseFlow> & JsonObject;
    setupIED?: Partial<SetupIED> & JsonObject;
    datasetStructure?: Partial<DatasetStructure> & JsonObject;
  };
}

export type RunState = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

export interface RunStatus {
  id: string;
  name: string | null;
  state: RunState;
  step: string | null;
  stepIndex: number;
  stepCount: number;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  exitCode: number | null;
  error: string | null;
  randomSeed: number | null;
  outputFile: string | null;
}

export interface RunFile {
  path: string;
  bytes: number;
}

export interface RunDetail {
  run: RunStatus;
  spec?: RunSpec;
  files: RunFile[];
  logTail: string;
}

export interface FieldError {
  field: string;
  message: string;
}
