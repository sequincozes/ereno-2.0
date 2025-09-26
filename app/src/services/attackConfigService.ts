import { db } from '../config/firebase';
import { ref, set, get, push } from 'firebase/database';
import type { AttackConfigType } from '../types/attackConfigType';

export async function addAttackConfig(attackConfig: AttackConfigType) {
  const attackRef = push(ref(db, 'attackConfigs'));
  await set(attackRef, attackConfig);
  return attackRef.key;
}

export async function getAttackConfigs() {
  const snapshot = await get(ref(db, 'attackConfigs'));
  return snapshot.exists() ? snapshot.val() : {};
}
