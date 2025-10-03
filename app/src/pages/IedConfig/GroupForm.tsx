
import { useState, useEffect } from "react";
import { Trash2 as IconTrash, Save as IconSave } from "lucide-react";
import { getIeds, addGroup, deleteGroup, getGroups } from '../../services/iedService';


interface GroupFormBox {
  key: string;
  name: string;
  ieds: string[];
}

export default function GroupForm() {
  const [groups, setGroups] = useState<GroupFormBox[]>([]);
  const [ieds, setIeds] = useState<{ key: string; name: string }[]>([]);

  // Fetch IEDs from Firebase
  useEffect(() => {
    async function fetchIeds() {
      const iedsDb = await getIeds();
      if (iedsDb && typeof iedsDb === 'object') {
        setIeds(Object.entries(iedsDb).map(([key, value]) => ({ key, name: (value as { name: string }).name })));
      }
    }
    fetchIeds();
  }, []);

  // Fetch groups from Firebase
  useEffect(() => {
    async function fetchGroups() {
      const groupsDb = await getGroups();
      if (groupsDb && typeof groupsDb === 'object') {
        setGroups(Object.entries(groupsDb).map(([key, value]) => {
          const groupValue = value as { name: string; ieds: string[] };
          return { key, name: groupValue.name, ieds: groupValue.ieds };
        }));
      }
    }
    fetchGroups();
  }, []);

  // Add new group box
  const handleAddGroup = () => {
    setGroups(prev => [...prev, { key: Date.now().toString(), name: '', ieds: [] }]);
  };

  // Change group name
  const handleGroupNameChange = (idx: number, value: string) => {
    setGroups(prev => prev.map((g, i) => i === idx ? { ...g, name: value } : g));
  };

  // Toggle IED selection
  const handleIedToggle = (groupIdx: number, iedKey: string) => {
    setGroups(prev => prev.map((g, i) => {
      if (i !== groupIdx) return g;
      const iedsArr = g.ieds.includes(iedKey)
        ? g.ieds.filter(id => id !== iedKey)
        : [...g.ieds, iedKey];
      return { ...g, ieds: iedsArr };
    }));
  };

  // Save group to Firebase
  const handleSaveGroup = async (group: GroupFormBox) => {
    await addGroup({ name: group.name, ieds: group.ieds });
    alert('Group saved successfully!');
  };

  // Delete group from Firebase and UI
  const handleDeleteGroup = async (idx: number, groupKey?: string) => {
    if (groupKey) {
      await deleteGroup(groupKey);
    }
    setGroups(prev => prev.filter((_, i) => i !== idx));
  };

  return (
    <div className="mt-4">
      {groups.map((group, idx) => (
        <div key={group.key} className="border border-blue-500 rounded-md p-4 mb-4 relative">
          <div className="flex items-center justify-between mb-3">
            <label className="font-medium mr-4" style={{ minWidth: '180px', maxWidth: '220px', width: '220px' }}>
              Name of Group <span className="text-red-500">*</span>
              <input
                type="text"
                value={group.name}
                onChange={e => handleGroupNameChange(idx, e.target.value)}
                className="border flex rounded px-2 py-1 mt-1 w-full"
                placeholder="Ex: Group 1"
                required
                style={{ width: '100%' }}
              />
            </label>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => handleSaveGroup(group)}
                className="p-2 rounded border border-green-300 bg-green-100 text-green-600 hover:bg-green-200"
                title="Salvar Grupo"
              >
                <IconSave size={20} />
              </button>
              <button
                type="button"
                onClick={() => handleDeleteGroup(idx, group.key)}
                className="p-2 rounded border border-red-300 bg-red-100 text-red-600 hover:bg-red-200"
                title="Excluir Grupo"
              >
                <IconTrash size={20} />
              </button>
            </div>
          </div>
          <div className="mb-2 font-medium">
            IEDs of the Group <span className="text-red-500">*</span>
          </div>
          <div className="flex flex-col gap-2">
            {ieds.map(ied => (
              <label key={ied.key} className="flex items-center gap-2">
                <input
                  className="checkbox"
                  type="checkbox"
                  checked={group.ieds.includes(ied.key)}
                  onChange={() => handleIedToggle(idx, ied.key)}
                />
                {ied.name}
              </label>
            ))}
          </div>
        </div>
      ))}
      {/* Show Add Group button below all groups if at least one group exists */}
      {groups.length > 0 && (
        <button
          type="button"
          onClick={handleAddGroup}
          className="bg-blue-500 hover:bg-blue-700 text-white font-medium px-4 py-2 rounded mb-4"
        >
          + Add Group
        </button>
      )}
    </div>
  );
}