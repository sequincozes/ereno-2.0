import { db } from '../config/firebase';
import { ref, set, get, push } from 'firebase/database';
import type { AttackConfigType } from '../types/attackConfigType';

export async function addAttackConfig(attackConfig: AttackConfigType, uid: string) {
  const attackRef = push(ref(db, `${uid}/attackConfigs`));
  await set(attackRef, attackConfig);
  return attackRef.key;
}

export async function getAttackConfigs(uid: string) {
  const snapshot = await get(ref(db, `${uid}/attackConfigs`));
  return snapshot.exists() ? snapshot.val() : {};
}

export async function deleteAttackConfig(attackConfigId: string, uid: string) {
  const attackRef = ref(db, `${uid}/attackConfigs/${attackConfigId}`);
  await set(attackRef, null);
}
