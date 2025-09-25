import { useState } from "react";
import { Trash2 as IconTrash } from "lucide-react";

// First Mock of the IEDs
const iedsCadastrados = [
  { id: 1, name: "Protection-IED" },
  { id: 2, name: "Control-IED" }
];

export default function GroupForm() {
  interface Group {
    name: string;
    ieds: number[];
  }

  const [groups, setGroups] = useState<Group[]>([
    { name: "", ieds: [] }
  ]);

  const handleGroupNameChange = (idx: number, value: string) => {
    const updated = [...groups];
    updated[idx].name = value;
    setGroups(updated);
  };

  const handleIedToggle = (groupIdx: number, iedId: number) => {
    const updated = [...groups];
    const ieds = updated[groupIdx].ieds as number[];
    if (ieds.includes(iedId)) {
      updated[groupIdx].ieds = ieds.filter((id: number) => id !== iedId);
    } else {
      updated[groupIdx].ieds = [...ieds, iedId];
    }
    setGroups(updated);
  };

  const handleDeleteGroup = (idx: number) => {
    setGroups(groups.filter((_, i) => i !== idx));
  };

  return (
    <div className="mt-4">
      {groups.map((group, idx) => (
        <div key={idx} className="border border-blue-500 rounded-md p-4 mb-4 relative">
          <button
            type="button"
            onClick={() => handleDeleteGroup(idx)}
            className="ml-auto p-2 rounded border border-red-300 bg-red-100 text-red-600 hover:bg-red-200"
            title="Excluir"
          >
            <IconTrash size={24} />
          </button>
          <label className="block mb-3 font-medium">
            Name of Group <span className="text-red-500">*</span>
            <input
              type="text"
              value={group.name}
              onChange={e => handleGroupNameChange(idx, e.target.value)}
              className="border flex rounded px-2 py-1 mt-1"
              placeholder="Ex: Group 1"
              required
            />
          </label>
          <div className="mb-2 font-medium">
            IEDs of the Group <span className="text-red-500">*</span>
          </div>
          <div className="flex flex-col gap-2">
            {iedsCadastrados.map(ied => (
              <label key={ied.id} className="flex items-center gap-2">
                <input
                className="checkbox"
                type="checkbox"
                checked={group.ieds.includes(ied.id)}
                onChange={() => handleIedToggle(idx, ied.id)}
                />
                {ied.name}
              </label>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}