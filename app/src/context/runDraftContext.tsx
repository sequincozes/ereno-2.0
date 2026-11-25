import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, ApiError } from '../services/api';
import { draftFromCatalog } from '../run/runDraft';
import type { Catalog, FieldError, RunDraft } from '../types/run';

// Holds the run being configured across the wizard steps, plus the backend
// catalog it is built from. The draft is kept in localStorage as a per-browser
// convenience so a reload does not lose the form; it is never the source of truth.

const STORAGE_KEY = 'ereno.runDraft.v1';

type RunDraftContextValue = {
  catalog: Catalog | null;
  catalogError: string | null;
  reloadCatalog: () => void;
  draft: RunDraft;
  update: (fn: (d: RunDraft) => RunDraft) => void;
  replace: (d: RunDraft) => void;
  reset: () => void;
  /** Field errors from the last POST /api/runs, keyed by spec path. */
  errors: FieldError[];
  setErrors: (e: FieldError[]) => void;
};

const RunDraftContext = createContext<RunDraftContextValue | undefined>(undefined);

function loadStored(): RunDraft | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as RunDraft) : null;
  } catch {
    return null;
  }
}

export const RunDraftProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [stored] = useState<RunDraft | null>(loadStored);
  const [draft, setDraft] = useState<RunDraft>(() => stored ?? draftFromCatalog(null));
  const [errors, setErrors] = useState<FieldError[]>([]);

  const fetchCatalog = useCallback(() => {
    setCatalogError(null);
    api.catalog()
      .then(c => {
        setCatalog(c);
        // First visit: take the defaults from the core instead of the built-in fallback.
        if (!stored) setDraft(draftFromCatalog(c));
      })
      .catch((e: unknown) => setCatalogError(e instanceof ApiError ? e.message : String(e)));
  }, [stored]);

  useEffect(() => { fetchCatalog(); }, [fetchCatalog]);

  useEffect(() => {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(draft)); } catch { /* storage unavailable */ }
  }, [draft]);

  const update = useCallback((fn: (d: RunDraft) => RunDraft) => setDraft(d => fn(d)), []);
  const replace = useCallback((d: RunDraft) => { setDraft(d); setErrors([]); }, []);
  const reset = useCallback(() => { setDraft(draftFromCatalog(catalog)); setErrors([]); }, [catalog]);

  return (
    <RunDraftContext.Provider value={{
      catalog, catalogError, reloadCatalog: fetchCatalog, draft, update, replace, reset, errors, setErrors,
    }}>
      {children}
    </RunDraftContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export function useRunDraft(): RunDraftContextValue {
  const ctx = useContext(RunDraftContext);
  if (!ctx) throw new Error('useRunDraft must be used inside RunDraftProvider');
  return ctx;
}
