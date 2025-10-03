export function buildNestedAttackObject(paramValues: Record<string, string | number | boolean>, selectedAttack: string): object {
  const result: Record<string, any> = {};
  Object.entries(paramValues).forEach(([flatKey, value]) => {
    // Only process keys that start with 'attack.'
    if (!flatKey.startsWith('attack.')) return;
    const key = flatKey.slice(7); 
    const parts = key.split('.');
    
    if (parts[0] === selectedAttack) {
      parts.shift();
    }
    let current = result;
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i];
      if (i === parts.length - 1) {
        current[part] = value;
      } else {
        if (!current[part]) current[part] = {};
        current = current[part];
      }
    }
  });
  return result;
}
