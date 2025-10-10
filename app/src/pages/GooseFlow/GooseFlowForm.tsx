import { useContext, useState, useEffect } from "react";
import gooseData from "../../data/goose_properties.json";
import { Save as IconSave } from "lucide-react";
import { addGooseFlow } from '../../services/gooseFlowService';
import AuthContext from "../../context/authContext";

const GooseFlowForm = () => {
  const { parameters, defaultValues } = gooseData;
  const [formValues, setFormValues] = useState<Record<string, string | number | boolean>>(defaultValues);
  const authContext = useContext(AuthContext);
  const user = authContext?.user;

useEffect(() => {
  async function fetchGooseFlowFromDb() {
    if (user?.uid) {
      const { getGooseFlows } = await import('../../services/gooseFlowService');
      const gooseFlows = await getGooseFlows(user.uid);
      if (gooseFlows && typeof gooseFlows === 'object') {
        const keys = Object.keys(gooseFlows);
        if (keys.length > 0) {
          const firstGooseFlow = gooseFlows[keys[0]];
          setFormValues({ ...defaultValues, ...firstGooseFlow });
        }
      }
    }
  }
  fetchGooseFlowFromDb();
}, [user]);

  const handleChange = (field: string, value: string | number | boolean) => {
    setFormValues((prev: Record<string, string | number | boolean>) => ({ ...prev, [field]: value }));
  };

  // Save GooseFlow state to Firebase
  const handleSaveGooseFlow = async () => {
    try {
      const gooseToSave = {
        GoID: String(formValues.GoID),
        ethSrc: String(formValues.ethSrc),
        ethDst: String(formValues.ethDst),
        ethType: String(formValues.ethType),
        gooseAppid: String(formValues.gooseAppid),
        TPID: String(formValues.TPID),
        ndsCom: Boolean(formValues.ndsCom),
        Test: Boolean(formValues.Test),
        cbStatus: Boolean(formValues.cbStatus),
        message: {
          count: Number(formValues.numberOfMessages)
        },
        sv: {
          per: {
            goose: {
              multiplier: Number(formValues.svPerGoose)
            }
          }
        }
      };
      if (!user) {
        alert('User not authenticated!');
        return;
      }
      await addGooseFlow(gooseToSave, user.uid);
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
};

export default GooseFlowForm;