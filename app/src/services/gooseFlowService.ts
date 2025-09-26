import { db } from '../config/firebase';
import { ref, set, get, push } from 'firebase/database';
import type { GooseFlowType } from '../types/gooseFlowType';

export async function addGooseFlow(gooseFlow: GooseFlowType) {
  const gooseRef = push(ref(db, 'gooseFlows'));
  await set(gooseRef, gooseFlow);
  return gooseRef.key;
}

export async function getGooseFlows() {
  const snapshot = await get(ref(db, 'gooseFlows'));
  return snapshot.exists() ? snapshot.val() : {};
}
