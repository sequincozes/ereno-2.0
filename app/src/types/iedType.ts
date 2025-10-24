export interface IedType {
  id: number;
  name: string;
  gocbRef: string;
  timestamp: number;
  minTime: number;
  maxTime: number;
  datSet: string;
  stNum: number;
  sqNum: number;
}
export interface IedFormProps {
  iedKey?: string;
  onDeleteForm?: () => void;
  // Optional initial values when opening a new unsaved form (e.g. from copy)
  initialValues?: Record<string, string | number | boolean | string[] | null>;
  onCopy?: (values: Record<string, string | number | boolean | string[] | null>) => void;
  // localId is the parent's form id (may be temp). onSave notifies parent to replace localId with newKey when saved.
  localId?: string;
  onSave?: (localId: string, newKey: string) => void;
}