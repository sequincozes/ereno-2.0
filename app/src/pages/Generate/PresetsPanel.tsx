import { useCallback, useContext, useEffect, useState } from 'react';
import { Library as IconLibrary } from 'lucide-react';
import AuthContext from '../../context/authContext';
import { useRunDraft } from '../../context/runDraftContext';
import { deletePreset, listPresets, savePreset, type Preset } from '../../services/presetService';

/** Personal library of wizard configurations, stored in Firebase. Hidden when Firebase is off. */
export default function PresetsPanel() {
  const auth = useContext(AuthContext);
  const { draft, replace } = useRunDraft();
  const [presets, setPresets] = useState<Preset[]>([]);
  const [status, setStatus] = useState<string | null>(null);
  const uid = auth?.user?.uid;

  const refresh = useCallback(() => {
    if (!uid) return;
    listPresets(uid).then(setPresets).catch(e => setStatus(`Could not load presets: ${e.message}`));
  }, [uid]);

  useEffect(() => { refresh(); }, [refresh]);

  if (!auth?.authEnabled || !uid) return null;

  return (
    <div className="mt-6 border border-gray-200 rounded-md p-4 sm:p-6">
      <h4 className="flex items-center gap-2 font-semibold text-[#0051A2] mb-2">
        <IconLibrary className="w-4 h-4" /> My presets
      </h4>
      <div className="flex flex-wrap gap-2 mb-3">
        <button type="button" className="bg-blue-500 hover:bg-blue-700 text-white text-sm px-3 py-1.5 rounded"
          onClick={async () => {
            const name = draft.name.trim() || `Preset ${new Date().toLocaleString()}`;
            await savePreset(uid, name, draft);
            setStatus(`Saved “${name}”.`);
            refresh();
          }}>
          Save current configuration
        </button>
        {status && <span className="text-sm text-gray-600 self-center">{status}</span>}
      </div>
      {presets.length === 0 ? (
        <p className="text-sm text-gray-500">No presets saved yet.</p>
      ) : (
        <ul className="divide-y divide-gray-100">
          {presets.map(p => (
            <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
              <span><b>{p.name}</b> <span className="text-gray-500">· {new Date(p.savedAt).toLocaleString()}</span></span>
              <span className="flex gap-3">
                <button type="button" className="text-blue-700 underline" onClick={() => {
                  replace(p.draft);
                  setStatus(`Loaded “${p.name}”.`);
                }}>Load</button>
                <button type="button" className="text-red-700 underline" onClick={async () => {
                  await deletePreset(uid, p.id);
                  refresh();
                }}>Delete</button>
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
