import { ref, get, push, set } from 'firebase/database';
import { db } from '../config/firebase';
import type { RunDraft } from '../types/run';

// Personal preset library in the Firebase Realtime Database (only when Firebase
// is configured). Stored under v2/ so presets in the old per-form format stay
// untouched and are simply not read.

export interface Preset {
  id: string;
  name: string;
  savedAt: string;
  draft: RunDraft;
}

function presetsPath(uid: string) {
  return `${uid}/v2/presets`;
}

export async function listPresets(uid: string): Promise<Preset[]> {
  if (!db) return [];
  const snap = await get(ref(db, presetsPath(uid)));
  const val = (snap.exists() ? snap.val() : {}) as Record<string, Omit<Preset, 'id'>>;
  return Object.entries(val)
    .map(([id, p]) => ({ id, ...p }))
    .sort((a, b) => b.savedAt.localeCompare(a.savedAt));
}

export async function savePreset(uid: string, name: string, draft: RunDraft): Promise<string | null> {
  if (!db) return null;
  const r = push(ref(db, presetsPath(uid)));
  await set(r, { name, savedAt: new Date().toISOString(), draft });
  return r.key;
}

export async function deletePreset(uid: string, id: string): Promise<void> {
  if (!db) return;
  await set(ref(db, `${presetsPath(uid)}/${id}`), null);
}
