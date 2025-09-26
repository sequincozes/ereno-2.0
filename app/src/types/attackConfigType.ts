export interface AttackConfigType {
  targetGroup: string;
  category: string;
  specificAttack: string;
  parameters: Record<string, string | number | boolean>;
}
