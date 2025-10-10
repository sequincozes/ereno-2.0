import { db } from '../config/firebase';
import { ref, set, get, push } from 'firebase/database';
import type { IedType } from '../types/iedType';
import type { GroupType } from '../types/groupType';

export async function addGroup(group: GroupType, uid: string) {
  const groupRef = push(ref(db, `${uid}/groups`));
  await set(groupRef, group);
  return groupRef.key;
}
export async function getGroups(uid: string) {
  const snapshot = await get(ref(db, `${uid}/groups`));
  return snapshot.exists() ? snapshot.val() : {};
}

export async function deleteGroup(groupKey: string, uid: string) {
  await set(ref(db, `${uid}/groups/${groupKey}`), null);
}

export async function addIed(ied: IedType, uid: string) {
  const iedRef = push(ref(db, `${uid}/iedConfigs`));
  await set(iedRef, ied);
  return iedRef.key;
}

export async function getIeds(uid: string) {
  const snapshot = await get(ref(db, `${uid}/iedConfigs`));
  return snapshot.val();
}

export async function deleteIed(iedKey: string, uid: string) {
  await set(ref(db, `${uid}/iedConfigs/${iedKey}`), null);
}