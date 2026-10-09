import { test, expect } from '@playwright/test';

// Exercise Capacitor's real registerPlugin proxy. Only the Android bridge boundary
// is replaced; production components and plugin registration remain unchanged.
async function mockAndroid(page, overrides = {}) {
  await page.addInitScript(options => {
    const state = {
      granted: false,
      receiveGranted: false, sendGranted: false,
      debug: { appVersion: '0.3.2', androidVersion: 35,
        deviceManufacturer: 'Test maker', deviceModel: 'Test phone',
        receiveGranted: false, sendGranted: false,
        receiveRequested: false, sendRequested: false,
        receiveRationale: false, sendRationale: false,
        active: true, forwardingReady: false, ruleCount: 1, emailConfigured: false },
      history: [],
      email: { configured: false, provider: 'smtp', host: 'smtp.gmail.com', port: 587, address: '' },
      ...options.state,
    };
    Object.assign(state.debug, {
      receiveGranted: state.receiveGranted, sendGranted: state.sendGranted,
      emailConfigured: state.email.configured,
    });
    const rule = { id: 'email-rule', name: 'E-mail továbbítás', senderType: 'any', sender: '', keywords: '', keywordMode: 'any', channel: 'email', target: 'recipient@example.com', targets: ['recipient@example.com'], enabled: true, sample: false };
    localStorage.setItem('smsfwd.rules', JSON.stringify([rule]));
    localStorage.setItem('smsfwd.active', JSON.stringify(options.active ?? true));
    window.__androidCalls = [];
    window.__androidState = state;
    window.androidBridge = {};
    const methods = ['getState', 'configure', 'enableSms', 'enableSmsSending', 'openAppSettings', 'saveEmail', 'queueTestEmail', 'retryEmail', 'clearHistory', 'revealEmailPassword'];
    window.Capacitor = {
      PluginHeaders: [
        { name: 'SmsForwarder', methods: methods.map(name => ({ name, rtype: 'promise' })) },
        { name: 'GmailAuth', methods: ['connect', 'disconnect'].map(name => ({ name, rtype: 'promise' })) },
      ],
      nativePromise: async (plugin, method, args) => {
        window.__androidCalls.push({ plugin, method, args });
        if (method === 'getState') return structuredClone(state);
        if (method === 'enableSms') {
          state.debug.receiveRequested = true;
          return structuredClone(state);
        }
        if (method === 'enableSmsSending') {
          state.debug.sendRequested = true;
          return structuredClone(state);
        }
        if (plugin === 'GmailAuth' && method === 'connect') {
          if (options.connectError) throw new Error(options.connectError);
          state.email = { configured: true, provider: 'gmail', address: 'connected@gmail.com' };
          return structuredClone(state.email);
        }
        if (plugin === 'GmailAuth' && method === 'disconnect') {
          state.email = options.disconnectState || { configured: false, provider: 'smtp', address: '' };
          return { ...structuredClone(state.email), ...(options.disconnectWarning ? { warning: options.disconnectWarning } : {}) };
        }
        return {};
      },
    };
  }, overrides);
}

async function openSettings(page) {
  await page.getByRole('button', { name: 'Beállítások', exact: true }).first().click();
}

test('SMS receiving keeps email forwarding active when SMS sending is denied', async ({ page }) => {
  await mockAndroid(page, { state: { receiveGranted: true, sendGranted: false } });
  await page.goto('/');
  await openSettings(page);
  const receiving = page.locator('.setting-row').filter({ has: page.getByRole('heading', { name: 'SMS fogadása', exact: true }) });
  const sending = page.locator('.setting-row').filter({ has: page.getByRole('heading', { name: 'SMS küldése', exact: true }) });
  await expect(receiving).toContainText('Engedélyezve');
  await expect(sending).toContainText('Nincs engedélyezve');
  await expect.poll(() => page.evaluate(() => window.__androidCalls.filter(call => call.method === 'configure').at(-1)?.args)).toMatchObject({
    active: true,
    rules: [expect.objectContaining({ channel: 'email', target: 'recipient@example.com' })],
  });
  await page.getByRole('button', { name: 'SMS-küldés engedélyezése', exact: true }).click();
  await expect.poll(() => page.evaluate(() => window.__androidCalls.filter(call => call.method === 'enableSmsSending').length)).toBe(1);
  expect(await page.evaluate(() => window.__androidCalls.some(call => call.method === 'enableSms'))).toBe(false);
  await expect(page.getByRole('switch', { name: 'Továbbítás bekapcsolása', exact: true })).toHaveAttribute('aria-checked', 'true');
});

test('denied SMS receiving provides a working Android settings recovery', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockAndroid(page);
  await page.goto('/');
  await openSettings(page);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.locator('.setting-row').filter({ has: page.getByRole('heading', { name: 'SMS fogadása', exact: true }) }).getByRole('button', { name: 'SMS-fogadás engedélyezése', exact: true }).click();
  await expect.poll(() => page.evaluate(() => window.__androidCalls.filter(call => call.method === 'enableSms').length)).toBe(1);
  await expect(page.getByRole('status')).toContainText('alkalmazásengedélyeket');
  await page.getByRole('button', { name: 'Alkalmazásengedélyek megnyitása', exact: true }).last().click();
  await expect.poll(() => page.evaluate(() => window.__androidCalls.filter(call => call.method === 'openAppSettings').length)).toBe(1);
  // Model returning from Android settings after the user grants receiving.
  await page.evaluate(() => {
    window.__androidState.receiveGranted = true;
    window.__androidState.debug.receiveGranted = true;
  });
  await page.getByRole('button', { name: 'Debug frissítése', exact: true }).click();
  await expect(page.locator('.setting-row').filter({ has: page.getByRole('heading', { name: 'SMS fogadása', exact: true }) })).toContainText('Engedélyezve');
});

test('debug refresh exposes permission metadata without SMS contents or credentials', async ({ page }) => {
  await mockAndroid(page, { state: {
    receiveGranted: true, sendGranted: false,
    email: { configured: true, provider: 'smtp', address: 'private-sender@example.com', password: 'private-password-sentinel' },
    history: [{ id: 'private-event', sender: '+36301234567', body: 'private-SMS-content-sentinel', target: 'private-recipient@example.com', channel: 'email', status: 'blocked', at: '2026-10-09T10:00:00Z' }],
  } });
  await page.goto('/');
  await openSettings(page);
  const debug = page.locator('section').filter({ has: page.getByRole('heading', { name: 'Hibakeresés', exact: true }) }).last();
  await expect(debug.locator('.debug-details > div').filter({ hasText: 'SMS-küldés engedélyezve' })).toContainText('Nem');
  await page.evaluate(() => {
    window.__androidState.sendGranted = true;
    window.__androidState.debug.sendGranted = true;
  });
  await page.getByRole('button', { name: 'Debug frissítése', exact: true }).click();
  await expect(debug).toContainText('0.3.2');
  await expect(debug).toContainText('Test phone');
  await expect(debug.locator('.debug-details > div').filter({ hasText: 'SMS-fogadás engedélyezve' })).toContainText('Igen');
  await expect(debug.locator('.debug-details > div').filter({ hasText: 'SMS-küldés engedélyezve' })).toContainText('Igen');
  for (const privateValue of ['private-SMS-content-sentinel', 'private-password-sentinel', 'private-sender@example.com', 'private-recipient@example.com', '+36301234567']) {
    await expect(debug).not.toContainText(privateValue);
  }
  await expect.poll(() => page.evaluate(() => window.__androidCalls.filter(call => call.method === 'getState').length)).toBeGreaterThanOrEqual(2);
});

test('a manual test email can be queued with SMS denied and forwarding paused', async ({ page }) => {
  await mockAndroid(page, { active: false, state: { email: { configured: true, provider: 'smtp', address: 'sender@gmail.com', host: 'smtp.gmail.com', port: 587 } } });
  await page.goto('/');
  await openSettings(page);
  await expect(page.getByRole('switch', { name: 'Továbbítás bekapcsolása', exact: true })).toHaveAttribute('aria-checked', 'false');
  await page.getByRole('textbox', { name: 'Valódi próba-e-mail címzettje', exact: true }).fill('test-recipient@example.com');
  await page.getByRole('button', { name: 'Próba-e-mail küldése', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('várólistára');
  await expect.poll(() => page.evaluate(() => window.__androidCalls.filter(call => call.method === 'queueTestEmail').at(-1)?.args)).toEqual({ target: 'test-recipient@example.com' });
  expect(await page.evaluate(() => window.__androidCalls.some(call => ['enableSms', 'enableSmsSending'].includes(call.method)))).toBe(false);
});

for (const width of [390, 1366]) {
  test(`Gmail information is readable and links to app passwords at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/');
    await openSettings(page);
    await page.getByRole('button', { name: 'Gmail beállítási útmutató', exact: true }).click();
    const help = page.getByRole('dialog');
    await expect(help.getByRole('heading', { name: 'Gmail beállítási útmutató', exact: true })).toBeVisible();
    await expect(help).toContainText('kétlépcsős');
    await expect(help.locator('a[href="https://myaccount.google.com/apppasswords"]')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.keyboard.press('Escape');
    await expect(help).toHaveCount(0);
  });
}

test('Google account connection and disconnection update email delivery without entering a password', async ({ page }) => {
  await mockAndroid(page);
  await page.goto('/');
  await openSettings(page);
  await page.getByRole('button', { name: 'Gmail összekapcsolása', exact: true }).click();
  await expect(page.getByText(/Küldő: connected@gmail\.com/)).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Valódi próba-e-mail címzettje', exact: true })).toBeVisible();
  await expect(page.getByLabel('Alkalmazásjelszó', { exact: true })).toHaveCount(0);
  expect(await page.evaluate(() => window.__androidCalls.some(call => ['saveEmail', 'revealEmailPassword'].includes(call.method)))).toBe(false);
  await expect.poll(() => page.evaluate(() => window.__androidCalls.filter(call => call.plugin === 'GmailAuth' && call.method === 'connect').length)).toBe(1);
  await page.getByRole('button', { name: 'Gmail leválasztása', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Gmail összekapcsolása', exact: true })).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Valódi próba-e-mail címzettje', exact: true })).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => window.__androidCalls.filter(call => call.plugin === 'GmailAuth' && call.method === 'disconnect').length)).toBe(1);
});

test('an unavailable Google connection explains the problem and preserves SMTP setup', async ({ page }) => {
  await mockAndroid(page, { connectError: 'A Google-fiók összekapcsolása ehhez az APK-hoz még nincs beállítva.' });
  await page.goto('/');
  await openSettings(page);
  await page.getByRole('button', { name: 'Gmail összekapcsolása', exact: true }).click();
  await expect(page.getByText('A Google-fiók összekapcsolása ehhez az APK-hoz még nincs beállítva.', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Postafiók beállítása', exact: true }).click();
  await expect(page.getByRole('textbox', { name: 'SMTP-kiszolgáló', exact: true })).toHaveValue('smtp.gmail.com');
  await expect(page.getByLabel('Alkalmazásjelszó', { exact: true })).toBeVisible();
});

test('a Gmail account needing consent can be reconnected without entering an app password', async ({ page }) => {
  await mockAndroid(page, { state: { email: { configured: false, provider: 'gmail', gmailConnected: true, address: 'connected@gmail.com', reconnectRequired: true } } });
  await page.goto('/');
  await openSettings(page);
  await page.getByRole('button', { name: 'Gmail újraengedélyezése', exact: true }).click();
  await expect.poll(() => page.evaluate(() => window.__androidCalls.filter(call => call.plugin === 'GmailAuth' && call.method === 'connect').length)).toBe(1);
  await expect(page.getByRole('button', { name: 'Gmail újraengedélyezése', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Gmail leválasztása', exact: true })).toBeVisible();
  await expect(page.getByLabel('Alkalmazásjelszó', { exact: true })).toHaveCount(0);
});


test('Gmail disconnect preserves SMTP fallback and explains incomplete Google revocation', async ({ page }) => {
  const warning = 'A helyi kapcsolat megszűnt, de a Google-engedély visszavonása nem igazolható.';
  await mockAndroid(page, {
    state: { email: { configured: true, provider: 'gmail', gmailConnected: true, address: 'connected@gmail.com' } },
    disconnectState: { configured: true, provider: 'smtp', address: 'fallback@example.com', host: 'smtp.gmail.com', port: 587 },
    disconnectWarning: warning,
  });
  await page.goto('/');
  await openSettings(page);
  await page.getByRole('button', { name: 'Gmail leválasztása', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText(warning);
  await expect(page.getByText(/Küldő: fallback@example\.com/)).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Valódi próba-e-mail címzettje', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Gmail összekapcsolása', exact: true })).toBeVisible();
});
