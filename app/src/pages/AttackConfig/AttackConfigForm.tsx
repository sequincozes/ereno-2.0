
import { useState } from "react";
import attackData from "../../data/attacks.json";
import { Trash2 as IconTrash } from "lucide-react";
import { useFloating, autoUpdate, offset, flip, shift, useHover, useFocus, useDismiss, useRole, useInteractions } from '@floating-ui/react';

interface AttackFormProps {
  attackIndex: number;
  onDelete: () => void;
}

export default function AttackForm({ attackIndex, onDelete }: AttackFormProps) {
  const compromisedGroups = attackData.iedConfiguration.compromisedIED;
  const attackCategories = Object.keys(attackData.attacks);

  const [selectedGroup, setSelectedGroup] = useState(compromisedGroups[0] || "");
  const [selectedCategory, setSelectedCategory] = useState(attackCategories[0] || "");
  const [selectedAttack, setSelectedAttack] = useState("");

  const specificAttacks = selectedCategory ? Object.keys(attackData.attacks[selectedCategory]) : [];
  const attackParams = selectedCategory && selectedAttack
    ? attackData.attacks[selectedCategory][selectedAttack]?.parameters || []
    : [];

  const [paramValues, setParamValues] = useState({});

  const [isOpen, setIsOpen] = useState(false);

  const { refs, floatingStyles, context } = useFloating({
    open: isOpen,
    onOpenChange: setIsOpen,
    middleware: [offset(10), flip(), shift()],
    placement: 'top',
    whileElementsMounted: autoUpdate
  });

  const hover = useHover(context, { move: false });
  const focus = useFocus(context);
  const dismiss = useDismiss(context);
  const role = useRole(context, { role: 'tooltip' });

  const { getReferenceProps, getFloatingProps } = useInteractions([hover, focus, dismiss, role]);

  const handleParamChange = (name, value) => {
    setParamValues(prev => ({ ...prev, [name]: value }));
  };

  const handleDelete = (field: string) => {
    setParamValues(prev => {
      const updated = { ...prev };
      if (field in updated) {
        updated[field] = "";
      }
      return updated;
    });
  };

  return (
    <div className="border border-blue-500 rounded-xl p-6 mt-4">
      <div className="flex items-center gap-4 mb-4">
        <input type="checkbox" checked={true} className="accent-black" readOnly />
        <input type="text" value={`Ataque ${attackIndex + 1}`} className="border rounded px-3 py-2 bg-white" readOnly />
        <button className="ml-auto p-2 rounded border border-red-300 bg-red-100 text-red-600 hover:bg-red-200" onClick={onDelete}>
          <IconTrash size={20} />
        </button>
      </div>
      <hr className="my-4 border-blue-200" />
      <div className="flex flex-col gap-4">
        {/* Target Group */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Target Group</label>
          <select
            value={selectedGroup}
            onChange={e => setSelectedGroup(e.target.value)}
            className="w-full border rounded px-3 py-2"
          >
            {compromisedGroups.map(g => (
              <option key={g} value={g}>{g}</option>
            ))}
          </select>
        </div>
        {/* Attack Category */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Attack Category</label>
          <select
            value={selectedCategory}
            onChange={e => {
              setSelectedCategory(e.target.value);
              setSelectedAttack("");
            }}
            className="w-full border rounded px-3 py-2"
          >
            {attackCategories.map(cat => (
              <option key={cat} value={cat}>{cat}</option>
            ))}
          </select>
        </div>
        {/* Specific Attack */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Specific Attack</label>
          {/* <span className="text-xs text-gray-500 mb-1 block">{selectedAttack ? "" : "Selecione o ataque específico"}</span> */}
          <select
            value={selectedAttack}
            onChange={e => setSelectedAttack(e.target.value)}
            className="w-full border rounded px-3 py-2"
          >
            <option value="">Select Specific Attack</option>
            {specificAttacks.map(a => (
              <option key={a} value={a}>{a}</option>
            ))}
          </select>
        </div>
        {/* Dinamic Parameters */}
        {attackParams.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
            {attackParams.map(param => (
              <div key={param.name} className="flex flex-col">
                <label className="text-sm font-medium text-gray-700 mb-1">{param.name}</label>
                {Array.isArray(param.type) ? (
                  <select
                    value={paramValues[param.name] ?? param.defaultValue}
                    onChange={e => handleParamChange(param.name, e.target.value)}
                    className="border rounded px-2 py-1"
                  >
                    {param.type.map(opt => (
                      <option key={opt} value={opt}>{opt}</option>
                    ))}
                  </select>
                ) : (
                  <input
                    type={param.type === "int" ? "number" : "text"}
                    value={paramValues[param.name] ?? param.defaultValue}
                    onChange={e => handleParamChange(param.name, param.type === "int" ? Number(e.target.value) : e.target.value)}
                    className="border rounded px-2 py-1"
                    ref={refs.setReference} {...getReferenceProps()}
                  />
                )}
                {isOpen && (
                  <div ref={refs.setFloating} style={floatingStyles} className="floating card preset-filled p-4" {...getFloatingProps()}>
                    {param.hint}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}