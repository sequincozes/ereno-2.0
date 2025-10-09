import { db } from '../config/firebase';
import { ref, set, get, push } from 'firebase/database';
import type { GooseFlowType } from '../types/gooseFlowType';

export async function addGooseFlow(gooseFlow: GooseFlowType, uid: string) {
  const gooseRef = push(ref(db, `${uid}/gooseFlows`));
  await set(gooseRef, gooseFlow);
  return gooseRef.key;
}

export async function getGooseFlows(uid: string) {
  const snapshot = await get(ref(db, `${uid}/gooseFlows`));
  return snapshot.exists() ? snapshot.val() : {};
}
