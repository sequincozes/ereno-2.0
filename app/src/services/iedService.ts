import { db } from '../config/firebase';
import { ref, set, get, push } from 'firebase/database';
import type { IedType } from '../types/iedType'; 

export async function addIed(ied: IedType) {
  const iedRef = push(ref(db, 'ieds'));
  await set(iedRef, ied);
  return iedRef.key;
}

export async function getIeds() {
  const snapshot = await get(ref(db, 'ieds'));
  return snapshot.exists() ? snapshot.val() : {};
}

export async function deleteIed(iedKey: string) {
  await set(ref(db, `ieds/${iedKey}`), null);
}