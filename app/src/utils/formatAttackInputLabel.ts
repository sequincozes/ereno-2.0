// Receives a property key like 'attack.randomReplay.delay.min' and returns a formatted label for the frontend
// Example: 'attack.randomReplay.delay.min' => 'Random Replay - Delay Min'
export function formatAttackInputLabel(key: string): string {
  // Remove 'attack.' prefix
  const label = key.startsWith('attack.') ? key.slice(7) : key;
  // Split by '.' and capitalize each part
  const parts = label.split('.');
  // First part is the attack name, rest are parameter path
  const attackName = parts[0].replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase()).trim();
  const paramPath = parts.slice(1).map(p => p.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase()).trim()).join(' - ');
  return paramPath ? `${attackName} - ${paramPath}` : attackName;
}
