import { useState } from "react";
import gooseData from "../../data/goose.json";

export default function GooseFlowForm() {
  const { parameters, defaultValues } = gooseData;
  const [formValues, setFormValues] = useState(defaultValues);

  const handleChange = (field: string, value: any) => {
    setFormValues(prev => ({ ...prev, [field]: value }));
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
        <div className="flex flex-row gap-6 mt-6">
          {Object.entries(parameters).map(([key, type]) => {
            if (type !== "boolean") return null;
            return (
              <label key={key} className="flex items-center gap-2">
                <input
                  className="checkbox"
                  type="checkbox"
                  checked={!!formValues[key]}
                  onChange={e => handleChange(key, e.target.checked)}
                />
                {key.charAt(0).toUpperCase() + key.slice(1)}
              </label>
            );
          })}
        </div>
      </div>
    </div>
  );
}