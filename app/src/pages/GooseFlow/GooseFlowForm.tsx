import { useState, useEffect } from "react";
import { getGooseFlows } from '../../services/gooseFlowService';
import gooseData from "../../data/goose.json";
import { Save as IconSave } from "lucide-react";
import { addGooseFlow } from '../../services/gooseFlowService';

export default function GooseFlowForm() {
  const { parameters, defaultValues } = gooseData;
  const [formValues, setFormValues] = useState<Record<string, string | number | boolean>>(defaultValues);

  // Fetch saved GooseFlow on mount
  useEffect(() => {
    async function fetchGoose() {
      const gooseFlows = await getGooseFlows();
      if (gooseFlows && typeof gooseFlows === 'object') {
        // Get the last saved GooseFlow
        const keys = Object.keys(gooseFlows);
        if (keys.length > 0) {
          setFormValues(gooseFlows[keys[keys.length - 1]]);
        }
      }
    }
    fetchGoose();
  }, []);

  const handleChange = (field: string, value: string | number | boolean) => {
    setFormValues((prev: Record<string, string | number | boolean>) => ({ ...prev, [field]: value }));
  };

  // Save GooseFlow state to Firebase
  const handleSaveGooseFlow = async () => {
    try {
      const gooseToSave: import('../../types/gooseFlowType').GooseFlowType = {
        GoID: String(formValues.GoID),
        numberOfMessages: Number(formValues.numberOfMessages),
        ethType: String(formValues.ethType),
        gooseAppid: String(formValues.gooseAppid),
        TPID: String(formValues.TPID),
        ndsCom: Boolean(formValues.ndsCom),
        Test: Boolean(formValues.Test),
        cbStatus: Boolean(formValues.cbStatus),
      };
      await addGooseFlow(gooseToSave);
      alert('GOOSE Flow saved successfully!');
    } catch {
      alert('Error saving GOOSE Flow!');
    }
  };

  return (
    <div className="border border-blue-500 rounded-md p-6 mt-4">
      <div className="grid grid-cols-3 gap-4 items-start">
        {Object.entries(parameters).map(([key, type]) => {
          if (type === "boolean") return null;
          return (
            <label key={key} className="flex flex-col">
              <span className="font-medium mb-1">
                {key.charAt(0).toUpperCase() + key.slice(1)}
              </span>
              <input
                type={type === "number" ? "number" : "text"}
                value={typeof formValues[key] === "boolean" ? "" : formValues[key] ?? ""}
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
        <div className="flex flex-row gap-6 mt-6 items-center">
          {Object.entries(parameters).map(([key, type]) => {
            if (type !== "boolean") return null;
            return (
              <label key={key} className="flex items-center gap-2">
                <input
                  className="checkbox"
                  type="checkbox"
                  checked={!!formValues[key as keyof typeof formValues]}
                  onChange={e => handleChange(key, e.target.checked)}
                />
                {key.charAt(0).toUpperCase() + key.slice(1)}
              </label>
            );
          })}
          <button
            type="button"
            onClick={handleSaveGooseFlow}
            className="p-2 rounded border border-green-300 bg-green-100 text-green-600 hover:bg-green-200"
            title="Salvar GooseFlow"
          >
            <IconSave size={24} />
          </button>
        </div>
      </div>
    </div>
  );
}