import test from 'node:test';
import assert from 'node:assert/strict';
import { matchRules, validateRule, normalizePhone } from '../src/domain.js';
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
