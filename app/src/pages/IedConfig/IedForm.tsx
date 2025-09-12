import { useState } from "react";
import iedData from "../../data/ied.json";
import { Switch } from '@skeletonlabs/skeleton-react';
import { Trash2 as IconTrash, Copy as IconCopy } from "lucide-react";

export default function IedForm() {
  const { parameters, defaultValues } = iedData;
  const [formValues, setFormValues] = useState(defaultValues);
  const [switchValue, setSwitchValue] = useState(true);


  const handleChange = (field: string, value: any) => {
    setFormValues(prev => ({ ...prev, [field]: value }));
  };

  const handleDelete = (field: string) => {
    setFormValues(prev => {
      const updated = { ...prev };
      if (field in updated) {
        updated[field] = "";
      }
      return updated;
    });
  };

  return (
    <div className="border border-blue-500 rounded-md p-6 mt-4">
      <div className="grid grid-cols-3 gap-4">
        {Object.entries(parameters).map(([key, type]) => {
          if (type === "boolean") {
            return (
              <label key={key} className="flex items-center gap-2">
                <Switch
                  checked={switchValue}
                  onCheckedChange={(e) => setSwitchValue(e.checked)}
                />
                <span className="font-medium">
                  {key.charAt(0).toUpperCase() + key.slice(1)}
                </span>
                <button
                  type="button"
                  onClick={() => navigator.clipboard.writeText(key)}
                  className="ml-2 px-2 py-1 bg-blue-500 text-white rounded"
                  title="Copiar"
                >
                  <IconCopy size={24}/>
                </button>
                <button
                  type="button"
                  onClick={() => handleDelete(key)}
                  className="ml-2 px-2 py-1 bg-red-500 text-white rounded border-[#F02532]"
                  title="Excluir"
                >
                  <IconTrash size={24} />
                </button>
              </label>
            );
          }

          if (Array.isArray(type)) {
            return (
              <label key={key} className="flex flex-col">
                <span className="font-medium mb-1">
                  {key.charAt(0).toUpperCase() + key.slice(1)}
                </span>
                <select
                  value={formValues[key]}
                  className="border rounded px-2 py-1"
                >;
                  {type.map(option => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </label>
            );
          }
        if (key === "groupId" || key === "id") {
            return;
        }
          return (
            <label key={key} className="flex flex-col">
              <span className="font-medium mb-1">
                {key.charAt(0).toUpperCase() + key.slice(1)}
              </span>
              <input
                type={type === "number" ? "number" : "text"}
                value={formValues[key] ?? ""}
                onChange={e =>
                  handleChange(
                    key,
                    type === "number" ? Number(e.target.value) : e.target.value
                  )
                }
                className="border rounded px-2 py-1"
              />
            </label>
          );
        })}
      </div>
    </div>
  );
}