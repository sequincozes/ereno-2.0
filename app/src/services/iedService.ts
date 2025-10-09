import { db } from '../config/firebase';
import { ref, set, get, push } from 'firebase/database';
import type { IedType } from '../types/iedType';
import type { GroupType } from '../types/groupType';

// Add a group to Firebase
export async function addGroup(group: GroupType) {
  const groupRef = push(ref(db, 'groups'));
  await set(groupRef, group);
  return groupRef.key;
}

// TODO I need to adapte to auth logic
export async function getGroups() {
  const snapshot = await get(ref(db, 'groups'));
  return snapshot.exists() ? snapshot.val() : {};
}

// TODO I need to adapte to auth logic
export async function deleteGroup(groupKey: string) {
  await set(ref(db, `groups/${groupKey}`), null);
} 

export async function addIed(ied: IedType, uid: string) {
  const iedRef = push(ref(db, `${uid}/iedConfigs`));
  await set(iedRef, ied);
  return iedRef.key;
}

export async function getIeds(uid: string) {
  const snapshot = await get(ref(db, `${uid}/iedConfigs`));
  return snapshot.exists() ? snapshot.val() : {};
}

export async function deleteIed(iedKey: string, uid: string) {
  await set(ref(db, `${uid}/iedConfigs/${iedKey}`), null);
}