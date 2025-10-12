export function formatAttackInputLabel(key: string): string {
  const label = key.startsWith('attack.') ? key.slice(7) : key;
  const parts = label.split('.');
  const attackName = parts[0].replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase()).trim();
  const paramPath = parts.slice(1).map(p => p.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase()).trim()).join(' - ');
  return paramPath ? `${attackName} - ${paramPath}` : attackName;
}
