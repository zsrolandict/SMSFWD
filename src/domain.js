export function normalizePhone(value) {
  const cleaned = value.trim().replace(/[\s()\-]/g, '');
  return cleaned.startsWith('00') ? `+${cleaned.slice(2)}` : cleaned;
}
export function validateRule(rule) {
  if (!rule.name?.trim()) return 'Adj nevet a szabálynak.';
  if (rule.senderType === 'specific' && !rule.sender?.trim()) return 'Add meg a feladót.';
  if (rule.channel === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(rule.target.trim())) return 'Adj meg egy érvényes e-mail címet.';
  if (rule.channel === 'sms' && !/^\+[1-9]\d{7,14}$/.test(normalizePhone(rule.target))) return 'A telefonszámot országkóddal add meg, például +36301234567.';
  return '';
}
export function matchRules(rules, message) {
  const seen = new Set();
  return rules.filter(rule => {
    if (!rule.enabled) return false;
    if (rule.senderType === 'specific' && normalizePhone(rule.sender).toLocaleLowerCase('hu') !== normalizePhone(message.sender).toLocaleLowerCase('hu')) return false;
    if (rule.keyword && !message.body.toLocaleLowerCase('hu').includes(rule.keyword.toLocaleLowerCase('hu'))) return false;
    const target = rule.channel === 'sms' ? normalizePhone(rule.target) : rule.target.trim();
    const key = `${rule.channel}:${target}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}
export const sampleRules = [
  { id: 'sample-1', name: 'Munkahelyi üzenetek', senderType: 'any', sender: '', keyword: 'munka', channel: 'email', target: 'iroda@ceg.example', enabled: true, sample: true },
  { id: 'sample-2', name: 'Fontos értesítések', senderType: 'any', sender: '', keyword: 'fontos', channel: 'sms', target: '+36301234567', enabled: false, sample: true },
];
