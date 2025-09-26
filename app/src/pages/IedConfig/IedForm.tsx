import { useState, useEffect } from "react";
import { getIeds } from '../../services/iedService';
import iedData from "../../data/ied.json";
import { Switch } from '@skeletonlabs/skeleton-react';
import { Trash2 as IconTrash, Copy as IconCopy, Save as IconSave } from "lucide-react";

import { addIed, deleteIed } from '../../services/iedService';

interface IedFormProps {
  iedKey?: string;
  onDeleteForm?: () => void;
}

export default function IedForm({ iedKey, onDeleteForm }: IedFormProps) {
  const { parameters, defaultValues } = iedData;
  const [formValues, setFormValues] = useState<Record<string, string | number | boolean | string[] | null>>(defaultValues);

  useEffect(() => {
    async function fetchIedFromDb() {
      if (iedKey) {
        const ieds = await getIeds();
        if (ieds && ieds[iedKey]) {
          setFormValues(ieds[iedKey]);
        }
      }
    }
    fetchIedFromDb();
  }, [iedKey]);

  const handleChange = (field: string, value: string | number | boolean) => {
    setFormValues(prev => ({ ...prev, [field]: value }));
  };

  // Delete the IED from Firebase and optionally remove the form
  const handleDeleteIed = async () => {
    if (iedKey) {
      await deleteIed(iedKey);
    }
    if (onDeleteForm) {
      onDeleteForm();
    }
  };

  // Function to save the IED state to Firebase using the service
  const handleSaveIed = async () => {
    try {
      const iedToSave: import('../../types/iedType').IedType = {
        id: Number(formValues.id),
        groupId: formValues.groupId === null ? null : Number(formValues.groupId),
        name: String(formValues.name),
        gocbRef: String(formValues.gocbRef),
        timestamp: Number(formValues.timestamp),
        function: Array.isArray(formValues.function) ? formValues.function as ("PUBLISHER"|"SUBSCRIBER")[] : [String(formValues.function) as "PUBLISHER"|"SUBSCRIBER"],
        datSet: String(formValues.datSet),
        stNum: Number(formValues.stNum),
        sqNum: Number(formValues.sqNum),
        sourceAdress: String(formValues.sourceAdress),
        addLegitimateMessages: Boolean(formValues.addLegitimateMessages),
      };
      await addIed(iedToSave);
      alert('IED saved successfully!');
    } catch {
      alert('Error saving IED!');
    }
  };

  return (
    <div className="border border-blue-500 rounded-md p-6 mt-4">
      <div className="grid grid-cols-3 gap-4">
        {Object.entries(parameters).map(([key, type]) => {
          if (type === "boolean") {
            return (
              <label key={key} className="flex items-center gap-2">
                <Switch
                  checked={!!formValues[key]}
                  onCheckedChange={(e) => handleChange(key, e.checked)}
                />
                <span className="font-medium">
                  {key.charAt(0).toUpperCase() + key.slice(1)}
                </span>
                <button
                  type="button"
                  onClick={handleSaveIed}
                  className="ml-auto p-2 rounded border border-green-300 bg-green-100 text-green-600 hover:bg-green-200"
                  title="Salvar"
                >
                  <IconSave size={24} />
                </button>
                <button
                  type="button"
                  onClick={() => navigator.clipboard.writeText(key)}
                  className="ml-auto p-2 rounded border border-blue-300 bg-blue-100 text-blue-600 hover:bg-blue-200"
                  title="Copiar"
                >
                  <IconCopy size={24}/>
                </button>
                <button
                  type="button"
                  onClick={handleDeleteIed}
                  className="ml-auto p-2 rounded border border-red-300 bg-red-100 text-red-600 hover:bg-red-200"
                  title="Excluir IED"
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
                  value={
                    Array.isArray(formValues[key])
                      ? (formValues[key] as string[])[0] ?? ""
                      : typeof formValues[key] === "boolean"
                        ? ""
                        : (formValues[key] as string | number) ?? ""
                  }
                  onChange={e => handleChange(key, e.target.value)}
                  className="border rounded px-2 py-1"
                >
                  {type.map((option: string) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </label>
            );
          }
          if (key === "groupId" || key === "id") {
            return null;
          }
          return (
            <label key={key} className="flex flex-col">
              <span className="font-medium mb-1">
                {key.charAt(0).toUpperCase() + key.slice(1)}
              </span>
              <input
                type={type === "number" ? "number" : "text"}
                value={
                  typeof formValues[key] === "boolean"
                    ? ""
                    : Array.isArray(formValues[key])
                      ? (formValues[key] as string[])[0] ?? ""
                      : formValues[key] ?? ""
                }
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