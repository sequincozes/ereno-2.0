import { useContext, useState, useEffect } from "react";
import { Trash2 as IconTrash, Save as IconSave } from "lucide-react";
import AuthContext from "../../context/authContext";
import type { groupFormProps, GroupType } from "../../types/groupType";
import { addGroup, deleteGroup, getGroups, getIeds } from "../../services/iedService";

export default function GroupForm({ groupIndex, onDeleteForm }: groupFormProps) {
  const authContext = useContext(AuthContext);
  const user = authContext?.user;
  const [groupName, setGroupName] = useState("");
  const [selectedIeds, setSelectedIeds] = useState<string[]>([]);
  const [allIeds, setAllIeds] = useState<{ key: string; name: string }[]>([]);
  const [userGroups, setUserGroups] = useState<{ key: string; group: GroupType }[]>([]);

  // Fetch all IEDs registered by the user to show in the checkbox list
  useEffect(() => {
    async function fetchIeds() {
      if (user) {
        const ieds = await getIeds(user.uid);
        if (ieds && typeof ieds === "object") {
          setAllIeds(
            Object.entries(ieds).map(([key, value]) => ({
              key,
              name: (value as import("../../types/iedType").IedType).name || key
            }))
          );
        } else {
          setAllIeds([]);
        }
      }
    }
    fetchIeds();
  }, [user]);

  // Fetch all the groups of the user to show below the form
  useEffect(() => {
    async function fetchGroups() {
      if (user) {
        const groups = await getGroups(user.uid);
        if (groups && typeof groups === "object") {
          setUserGroups(
            Object.entries(groups).map(([key, value]) => ({
              key,
              group: value as GroupType
            }))
          );
        } else {
          setUserGroups([]);
        }
      }
    }
    fetchGroups();
  }, [user]);

  // Fetch existing group if groupIndex exists (edit mode)
  useEffect(() => {
    async function fetchGroup() {
      if (user && groupIndex) {
        const groups = await getGroups(user.uid);
        if (groups && groups[groupIndex]) {
          setGroupName(groups[groupIndex].name || "");
          setSelectedIeds(groups[groupIndex].ieds || []);
        }
      }
    }
    fetchGroup();
  }, [user, groupIndex]);

  const handleIedToggle = (iedKey: string) => {
    setSelectedIeds(prev =>
      prev.includes(iedKey)
        ? prev.filter(k => k !== iedKey)
        : [...prev, iedKey]
    );
  };

  const handleDeleteGroup = async (key?: string) => {
    // If key is passed, delete persisted group, otherwise delete editing group
    if (user && (key || groupIndex)) {
      await deleteGroup(key || groupIndex, user.uid);
      alert('Group deleted successfully!');
      const groups = await getGroups(user.uid);
      if (groups && typeof groups === "object") {
        setUserGroups(
          Object.entries(groups).map(([k, value]) => ({
            key: k,
            group: value as GroupType
          }))
        );
      } else {
        setUserGroups([]);
      }
    }
    if (!key && onDeleteForm) {
      onDeleteForm();
    }
  };

  const handleSaveGroup = async () => {
    try {
      if (!user) {
        alert('User not authenticated!');
        return;
      }
      if (!groupName.trim()) {
        alert('Group name is required!');
        return;
      }
      if (selectedIeds.length === 0) {
        alert('Select at least one IED!');
        return;
      }
      const groupToSave: GroupType = {
        name: groupName,
        ieds: selectedIeds
      };
      await addGroup(groupToSave, user.uid);
      alert('Group saved successfully!');
      setGroupName("");
      setSelectedIeds([]);
      const groups = await getGroups(user.uid);
      if (groups && typeof groups === "object") {
        setUserGroups(
          Object.entries(groups).map(([k, value]) => ({
            key: k,
            group: value as GroupType
          }))
        );
      } else {
        setUserGroups([]);
      }
    } catch {
      alert('Error saving Group!');
    }
  };

  return (
    <div className="border border-blue-500 rounded-md p-6 mt-4">
      <div className="flex flex-col gap-2">
        <label className="block font-medium mb-1 mt-0">
          Name of Group <span className="text-red-500">*</span>
          <input
            type="text"
            value={groupName}
            onChange={e => setGroupName(e.target.value)}
            className="border flex rounded px-2 py-1 mt-1"
            placeholder="Ex: Group 1"
            required
            style={{ marginBottom: "0.5rem" }}
          />
        </label>
        <div className="font-medium mb-1 mt-0">
          IEDs of the Group <span className="text-red-500">*</span>
        </div>
        <div className="flex flex-col gap-1 mb-2">
          {allIeds.length === 0 && (
            <span className="text-gray-500">No IEDs registered.</span>
          )}
          {allIeds.map(ied => (
            <label key={ied.key} className="flex items-center gap-2">
              <input
                className="checkbox"
                type="checkbox"
                checked={selectedIeds.includes(ied.key)}
                onChange={() => handleIedToggle(ied.key)}
              />
              {ied.name}
            </label>
          ))}
        </div>
        <div className="flex justify-end gap-2 mt-2">
          <button
            type="button"
            onClick={handleSaveGroup}
            className="p-2 rounded border border-green-300 bg-green-100 text-green-600 hover:bg-green-200"
            title="Save Group"
          >
            <IconSave size={24} />
          </button>
          <button
            type="button"
            onClick={() => handleDeleteGroup()}
            className="p-2 rounded border border-red-300 bg-red-100 text-red-600 hover:bg-red-200"
            title="Delete Group"
          >
            <IconTrash size={24} />
          </button>
        </div>
      </div>

      {/* List of user groups */}
      {userGroups.length > 0 && (
        <div className="mt-6">
          <div className="font-bold mb-2 text-blue-700">Your Groups</div>
          <div className="flex flex-col gap-3">
            {userGroups.map(({ key, group }) => (
              <div key={key} className="border border-blue-300 rounded p-3 flex flex-col">
                <div className="flex justify-between items-center">
                  <span className="font-semibold text-blue-900">{group.name}</span>
                  <button
                    type="button"
                    onClick={() => handleDeleteGroup(key)}
                    className="p-1 rounded border border-red-300 bg-red-100 text-red-600 hover:bg-red-200"
                    title="Delete Group"
                  >
                    <IconTrash size={18} />
                  </button>
                </div>
                <div className="mt-2 text-sm text-gray-700">
                  <b>IEDs:</b> {group.ieds.map(iedKey => {
                    const ied = allIeds.find(i => i.key === iedKey);
                    return ied ? ied.name : iedKey;
                  }).join(", ")}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}