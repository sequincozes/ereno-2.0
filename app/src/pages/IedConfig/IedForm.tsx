import { useContext, useState, useEffect } from "react";
import iedData from "../../data/ied_properties.json";
import { Trash2 as IconTrash, Copy as IconCopy, Save as IconSave } from "lucide-react";
import { addIed, deleteIed } from '../../services/iedService';
import AuthContext from "../../context/authContext";
import type { IedFormProps } from "../../types/iedType";

const IedForm: React.FC<IedFormProps> = ({ iedKey, onDeleteForm }) => {
  const { parameters, defaultValues } = iedData;
  const [formValues, setFormValues] = useState<Record<string, string | number | boolean | string[] | null>>(defaultValues);
  const authContext = useContext(AuthContext);
  const user = authContext?.user;

  useEffect(() => {
    async function fetchIedFromDb() {
      if (user && iedKey) {
        const { getIeds } = await import('../../services/iedService');
        const ieds = await getIeds(user.uid);
        if (ieds && ieds[iedKey]) {
          setFormValues({ ...defaultValues, ...ieds[iedKey] });
        }
      }
    }
    fetchIedFromDb();
  }, [user, iedKey]);

  const handleChange = (field: string, value: string | number | boolean) => {
    setFormValues(prev => ({ ...prev, [field]: value }));
  };

  // Delete the IED from Firebase and optionally remove the form
  const handleDeleteIed = async () => {
    if (iedKey && user) {
      await deleteIed(iedKey, user.uid);
      alert('IED deleted successfully!');
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
        name: String(formValues.name),
        gocbRef: String(formValues.gocbRef),
        timestamp: Number(formValues.timestamp),
        datSet: String(formValues.datSet),
        stNum: Number(formValues.stNum),
        sqNum: Number(formValues.sqNum),
        minTime: Number(formValues.minTime),
        maxTime: Number(formValues.maxTime),
      };
      if (!user) {
        alert('User not authenticated!');
        return;
      }
      await addIed(iedToSave, user.uid);
      alert('IED saved successfully!');
    } catch {
      alert('Error saving IED!');
    }
  };

  return (
    <div className="border border-blue-500 rounded-md p-6 mt-4">
      <div className="grid grid-cols-3 gap-4 items-end">
        {Object.entries(parameters).map(([key, type]) => {
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
        <div className="flex gap-2 justify-end col-start-3">
          <button
            type="button"
            onClick={handleSaveIed}
            className="p-2 rounded border border-green-300 bg-green-100 text-green-600 hover:bg-green-200"
            title="Salvar"
          >
            <IconSave size={24} />
          </button>
          <button
            type="button"
            onClick={() => navigator.clipboard.writeText(JSON.stringify(formValues))}
            className="p-2 rounded border border-blue-300 bg-blue-100 text-blue-600 hover:bg-blue-200"
            title="Copiar"
          >
            <IconCopy size={24}/>
          </button>
          <button
            type="button"
            onClick={handleDeleteIed}
            className="p-2 rounded border border-red-300 bg-red-100 text-red-600 hover:bg-red-200"
            title="Excluir IED"
          >
            <IconTrash size={24} />
          </button>
        </div>
      </div>
    </div>
  );
};

export default IedForm;