export interface IedType {
  id: number;
  groupId: number | null;
  name: string;
  gocbRef: string;
  timestamp: number;
  function: ("PUBLISHER" | "SUBSCRIBER")[];
  datSet: string;
  stNum: number;
  sqNum: number;
  sourceAdress: string;
  addLegitimateMessages: boolean;
}