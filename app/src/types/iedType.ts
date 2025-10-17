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
}