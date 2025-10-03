import type { GroupType } from '../types/groupType';
// Add a group to Firebase
export async function addGroup(group: GroupType) {
  const groupRef = push(ref(db, 'groups'));
  await set(groupRef, group);
  return groupRef.key;
}

// Get all groups from Firebase
export async function getGroups() {
  const snapshot = await get(ref(db, 'groups'));
  return snapshot.exists() ? snapshot.val() : {};
}

// Delete a group from Firebase
export async function deleteGroup(groupKey: string) {
  await set(ref(db, `groups/${groupKey}`), null);
}
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