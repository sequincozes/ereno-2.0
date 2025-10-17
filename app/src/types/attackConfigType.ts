export interface AttackConfigType {
  category: string;
  parameters: Record<string, any>;
}
export interface AttackFormProps {
  attackIndex: number;
  onDelete: () => void;
}

export type AttackParameter = {
  name: string;
  type: string | string[];
  defaultValue: string | number | boolean;
  hint?: string;
};