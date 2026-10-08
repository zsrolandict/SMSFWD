export function normalizePhone(value) {
  const cleaned = value.trim().replace(/[\s()\-]/g, '');
  return cleaned.startsWith('00') ? `+${cleaned.slice(2)}` : cleaned;
}
export function getTargets(rule) {
  const values = Array.isArray(rule.targets) ? rule.targets : String(rule.target || '').split(/[\n,;]+/);
  return [...new Set(values.map(v => rule.channel === 'sms' ? normalizePhone(v) : v.trim()).filter(Boolean))];
}
export function keywordMatches(rule, body) {
  if (rule.keywords == null) return !rule.keyword || body.toLocaleLowerCase('hu').includes(rule.keyword.toLocaleLowerCase('hu'));
  const terms = rule.keywords.split('\n').map(v => v.trim()).filter(Boolean);
  if (!terms.length) return true;
  const normalized = body.normalize('NFC').toLocaleLowerCase('hu').replace(/\s+/g, ' ');
  const matches = terms.map(term => {
    const escaped = term.normalize('NFC').toLocaleLowerCase('hu').replace(/\s+/g, ' ').replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return new RegExp(`(?<![\\p{L}\\p{N}_])${escaped}(?![\\p{L}\\p{N}_])`, 'u').test(normalized);
  });
  return rule.keywordMode === 'all' ? matches.every(Boolean) : matches.some(Boolean);
}
export function validateRule(rule) {
  if (!rule.name?.trim()) return 'Adj nevet a szabálynak.';
  if (rule.senderType === 'specific' && !rule.sender?.trim()) return 'Add meg a feladót.';
  const targets = getTargets(rule);
  if (!targets.length) return 'Adj meg legalább egy címzettet.';
  if (rule.channel === 'email' && targets.some(v => !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v))) return 'Minden sorban érvényes e-mail cím szerepeljen.';
  if (rule.channel === 'sms' && targets.some(v => !/^\+[1-9]\d{7,14}$/.test(v))) return 'Minden telefonszámot országkóddal adj meg, például +36301234567.';
  return '';
}
export function matchRules(rules, message) {
  const seen = new Set();
  return rules.flatMap(rule => {
    if (!rule.enabled) return [];
    if (rule.senderType === 'specific' && normalizePhone(rule.sender).toLocaleLowerCase('hu') !== normalizePhone(message.sender).toLocaleLowerCase('hu')) return [];
    if (!keywordMatches(rule, message.body)) return [];
    return getTargets(rule).filter(target => { const key = `${rule.channel}:${target}`; if (seen.has(key)) return false; seen.add(key); return true; }).map(target => ({ ...rule, target }));
  });
}
export const sampleRules = [
  { id: 'sample-1', name: 'Munkahelyi üzenetek', senderType: 'any', sender: '', keyword: 'munka', channel: 'email', target: 'iroda@ceg.example', enabled: true, sample: true },
  { id: 'sample-2', name: 'Fontos értesítések', senderType: 'any', sender: '', keyword: 'fontos', channel: 'sms', target: '+36301234567', enabled: false, sample: true },
];
