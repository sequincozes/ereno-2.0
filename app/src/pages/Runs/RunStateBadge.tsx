import type { RunState } from '../../types/run';

const STYLE: Record<RunState, string> = {
  QUEUED: 'bg-gray-100 text-gray-700',
  RUNNING: 'bg-blue-100 text-blue-800',
  SUCCEEDED: 'bg-green-100 text-green-800',
  FAILED: 'bg-red-100 text-red-800',
  CANCELLED: 'bg-amber-100 text-amber-800',
};

const LABEL: Record<RunState, string> = {
  QUEUED: 'Queued',
  RUNNING: 'Running',
  SUCCEEDED: 'Done',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};

export default function RunStateBadge({ state }: { state: RunState }) {
  return <span className={`rounded px-2 py-0.5 text-xs font-semibold ${STYLE[state]}`}>{LABEL[state]}</span>;
}

