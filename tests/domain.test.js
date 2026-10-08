import test from 'node:test';
import assert from 'node:assert/strict';
import { matchRules, validateRule, normalizePhone, keywordMatches, getTargets } from '../src/domain.js';
const base = { id: '1', name: 'Iroda', enabled: true, senderType: 'any', sender: '', keyword: 'munka', channel: 'email', target: 'iroda@example.com' };
test('a keyword matches case-insensitively while preserving accents', () => {
  assert.equal(matchRules([base], { sender: 'BANK', body: 'MUNKA érkezett' }).length, 1);
  assert.equal(matchRules([{ ...base, keyword: 'érkezett' }], { sender: 'BANK', body: 'erkezett' }).length, 0);
});
test('sender and keyword conditions both apply', () => {
  const rule = { ...base, senderType: 'specific', sender: '+36 30 123-4567' };
  assert.equal(matchRules([rule], { sender: '0036301234567', body: 'munka' }).length, 1);
  assert.equal(matchRules([rule], { sender: '+36309999999', body: 'munka' }).length, 0);
  assert.equal(matchRules([rule], { sender: '+36301234567', body: 'otthon' }).length, 0);
});
test('disabled rules are ignored and repeated destinations collapse', () => {
  assert.equal(matchRules([{ ...base, enabled: false }], { sender: 'BANK', body: 'munka' }).length, 0);
  assert.equal(matchRules([base, { ...base, id: '2' }], { sender: 'BANK', body: 'munka' }).length, 1);
});
test('different events with the same content still match independently', () => {
  const event = { sender: 'BANK', body: 'munka' };
  assert.equal(matchRules([base], event).length, 1);
  assert.equal(matchRules([base], event).length, 1);
});
test('SMS destination validation requires an international number', () => {
  assert.notEqual(validateRule({ ...base, channel: 'sms', target: '06301234567' }), '');
  assert.equal(validateRule({ ...base, channel: 'sms', target: '+36 30 123 4567' }), '');
  assert.equal(normalizePhone('0036 (30) 123-4567'), '+36301234567');
});
test('email and rule name validation', () => {
  assert.equal(validateRule(base), '');
  assert.notEqual(validateRule({ ...base, target: 'invalid' }), '');
  assert.notEqual(validateRule({ ...base, name: ' ' }), '');
});
test('screenshot message phrases match without requiring the changing code', () => {
  const rule = { ...base, keywords: 'egyszer használatos jelszava\nInfoCert', keywordMode: 'any' };
  assert.equal(keywordMatches(rule, 'Az Ön egyszer használatos jelszava: 00000000'), true);
  assert.equal(keywordMatches(rule, 'OTP: 00000000 Codice di verifica InfoCert generato alle ore: 10:00:00'), true);
  assert.equal(keywordMatches(rule, 'Szia, találkozunk holnap?'), false);
});
test('whole words, all conditions, whitespace and Unicode are consistent', () => {
  assert.equal(keywordMatches({ keywords: 'OTP' }, 'NOTP'), false);
  assert.equal(keywordMatches({ keywords: 'OTP' }, 'OTP: 00000000'), true);
  assert.equal(keywordMatches({ keywords: 'OTP\nInfoCert', keywordMode: 'all' }, 'OTP: 00000000'), false);
  assert.equal(keywordMatches({ keywords: 'OTP\nInfoCert', keywordMode: 'all' }, 'otp: 00000000 INFOCERT'), true);
  assert.equal(keywordMatches({ keywords: 'egyszer használatos' }, 'egyszer\n használatos'), true);
  assert.equal(keywordMatches({ keywords: 'érkezett' }, 'ÉRKEZETT'.normalize('NFD')), true);
});
test('multiple normalized phone targets expand and deduplicate across rules', () => {
  const rule = { ...base, channel: 'sms', target: '+36 30 1112233\n0036301112233\n+36302223344', keyword: '' };
  assert.deepEqual(getTargets(rule), ['+36301112233', '+36302223344']);
  assert.equal(validateRule(rule), '');
  const matches = matchRules([rule, { ...rule, id: '2', target: '+36301112233' }], { sender: 'BANK', body: 'üzenet' });
  assert.deepEqual(matches.map(r => r.target), ['+36301112233', '+36302223344']);
  assert.notEqual(validateRule({ ...rule, target: '+36301112233\ninvalid' }), '');
});
