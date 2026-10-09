import React, { useState, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { Capacitor, registerPlugin } from '@capacitor/core';
import { ArrowUpRight, ArrowRight, Plus, MessageSquare, Mail, Smartphone, ShieldCheck, Check, X, ChevronRight, SlidersHorizontal, Clock3, Zap, Send, MoreHorizontal, Pause, Play, Search, Trash2, Pencil, CheckCircle2, CircleHelp, Settings2, Activity, Wifi, ExternalLink, Bell, Inbox, Filter, Eye, EyeOff, Copy, Info, RefreshCw } from 'lucide-react';
import { matchRules, validateRule, normalizePhone, sampleRules, getTargets } from './domain';
import './styles.css';
import '@fontsource/dm-sans/latin-400.css';
import '@fontsource/dm-sans/latin-500.css';
import '@fontsource/dm-sans/latin-600.css';
import '@fontsource/dm-sans/latin-700.css';
import '@fontsource/manrope/latin-400.css';
import '@fontsource/manrope/latin-600.css';
import '@fontsource/manrope/latin-700.css';
import '@fontsource/manrope/latin-800.css';
import '@fontsource/dm-sans/latin-ext-400.css';
import '@fontsource/dm-sans/latin-ext-500.css';
import '@fontsource/dm-sans/latin-ext-600.css';
import '@fontsource/dm-sans/latin-ext-700.css';
import '@fontsource/manrope/latin-ext-400.css';
import '@fontsource/manrope/latin-ext-600.css';
import '@fontsource/manrope/latin-ext-700.css';
import '@fontsource/manrope/latin-ext-800.css';

const SmsBridge = registerPlugin('SmsForwarder');
const GmailBridge = registerPlugin('GmailAuth');
const native = Capacitor.isNativePlatform();
const uid = () => crypto.randomUUID();
function read(key, fallback) { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } }
function save(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); return true; } catch { return false; } }
const time = date => new Intl.DateTimeFormat('hu-HU', { hour: '2-digit', minute: '2-digit' }).format(new Date(date));
const tabs = [{ id: 'overview', title: 'Áttekintés', icon: Activity }, { id: 'rules', title: 'Szabályok', icon: SlidersHorizontal }, { id: 'history', title: 'Előzmények', icon: Clock3 }, { id: 'settings', title: 'Beállítások', icon: Settings2 }];
const freshRule = () => ({ id: uid(), name: '', senderType: 'any', sender: '', keyword: '', keywords: '', keywordMode: 'any', channel: 'email', target: '', enabled: true, sample: false });

function Toggle({ checked, onChange, label, disabled = false }) { return <button type="button" className={`toggle ${checked ? 'on' : ''}`} role="switch" aria-checked={checked} aria-label={label} onClick={onChange} disabled={disabled}><span /></button>; }
function App() {
  const [tab, setTab] = useState('overview');
  const [rules, setRules] = useState(() => read('smsfwd.rules', sampleRules));
  const [history, setHistory] = useState(() => read('smsfwd.history', []));
  const [active, setActive] = useState(() => read('smsfwd.active', true));
  const [limit, setLimit] = useState(() => read('smsfwd.limit', 20));
  const [modal, setModal] = useState(null);
  const [toast, setToast] = useState('');
  const [search, setSearch] = useState('');
  const [historyFilter, setHistoryFilter] = useState('all');
  const [permission, setPermission] = useState(false);
  const [sendPermission, setSendPermission] = useState(false);
  const [debugState, setDebugState] = useState(null);
  const [debugBusy, setDebugBusy] = useState(false);
  const [storageError, setStorageError] = useState(false);
  const [emailState, setEmailState] = useState({ configured: false, host: 'smtp.gmail.com', port: 587, address: '' });
  const [nativeError, setNativeError] = useState('');
  const applyNativeState = state => {
    const details = state.debug || state;
    setPermission(Boolean(state.receiveGranted ?? state.granted));
    setSendPermission(Boolean(state.sendGranted));
    if (state.email) setEmailState(state.email);
    setDebugState({
      appVersion: details.appVersion, androidVersion: details.androidVersion,
      deviceManufacturer: details.deviceManufacturer, deviceModel: details.deviceModel,
      receiveGranted: Boolean(state.receiveGranted ?? state.granted), sendGranted: Boolean(state.sendGranted),
      active: Boolean(details.active),
      receiveRequested: Boolean(details.receiveRequested), sendRequested: Boolean(details.sendRequested),
      receiveRationale: Boolean(details.receiveRationale), sendRationale: Boolean(details.sendRationale),
    });
    if (state.history?.length) setHistory(current => [...state.history, ...current.filter(item => item.simulated)].slice(0, 200));
    setNativeError('');
  };
  useEffect(() => { setStorageError(!save('smsfwd.rules', rules)); }, [rules]);
  useEffect(() => { if (!save('smsfwd.history', history)) setStorageError(true); }, [history]);
  useEffect(() => { save('smsfwd.active', active); save('smsfwd.limit', limit); }, [active, limit]);
  useEffect(() => { if (!toast) return; const handle = setTimeout(() => setToast(''), 4500); return () => clearTimeout(handle); }, [toast]);
  useEffect(() => {
    if (!native) return;
    const refresh = async () => { try { applyNativeState(await SmsBridge.getState()); } catch (e) { setNativeError(e.message); } };
    refresh(); const id = setInterval(refresh, 3000); return () => clearInterval(id);
  }, []);
  useEffect(() => { if (native) SmsBridge.configure({ rules: rules.filter(r => !r.sample).flatMap(r => getTargets(r).map(target => ({ ...r, target }))), active, dailyLimit: limit }).catch(e => setNativeError(e.message)); }, [rules, active, limit]);
  const enabledCount = rules.filter(r => r.enabled).length;
  const sentCount = history.filter(h => h.status === 'sent' && !h.simulated).length;
  const pendingCount = history.filter(h => ['pending', 'blocked'].includes(h.status)).length;
  const todayCount = history.filter(h => new Date(h.at).toDateString() === new Date().toDateString()).length;
  const notify = message => setToast(message);
  const editRule = rule => setModal({ type: 'rule', rule: { ...rule } });
  const copyRule = rule => setModal({ type: 'rule', rule: { ...rule, id: uid(), name: `${rule.name.slice(0, 70)} – másolat`, sample: false } });
  const toggleRule = id => setRules(current => current.map(r => r.id === id ? { ...r, enabled: !r.enabled } : r));
  const removeRule = id => { setRules(current => current.filter(r => r.id !== id)); setModal(null); notify('A szabály törölve.'); };
  const saveRule = rule => { const converted = { ...rule, sample: false, name: rule.name.trim(), targets: getTargets(rule), target: getTargets(rule)[0], keyword: (rule.keyword || '').trim(), sender: rule.sender.trim() }; setRules(current => current.some(r => r.id === rule.id) ? current.map(r => r.id === rule.id ? converted : r) : [...current, converted]); setModal(null); notify('A szabályt elmentettük.'); };
  const runTest = message => {
    const matches = active ? matchRules(rules, message) : [];
    if (!active) { notify('A továbbítás szünetel. Kapcsold be a teszteléshez.'); return { matches: [], paused: true }; }
    const at = new Date().toISOString();
    setHistory(current => [...matches.map(r => ({ id: uid(), sender: message.sender, body: message.body, target: r.target, channel: r.channel, ruleName: r.name, at, status: 'simulated', simulated: true })), ...current].slice(0, 200));
    return { matches, paused: false };
  };
  const enablePermission = async () => {
    try {
      const result = await SmsBridge.enableSms(); applyNativeState(result);
      const granted = result.receiveGranted ?? result.granted;
      notify(granted ? 'Az SMS-fogadás engedélyezve.' : 'Az SMS-fogadás nincs engedélyezve. A Beállításokban nyisd meg az alkalmazásengedélyeket.');
    } catch (e) { notify(e.message || 'Nem sikerült engedélyt kérni.'); }
  };
  const enableSendPermission = async () => {
    try {
      const result = await SmsBridge.enableSmsSending(); applyNativeState(result);
      notify(result.sendGranted ? 'Az SMS-küldés engedélyezve.' : 'Az SMS-küldés nincs engedélyezve. E-mailre továbbításhoz erre nincs szükség.');
    } catch (e) { notify(e.message || 'Nem sikerült SMS-küldési engedélyt kérni.'); }
  };
  const openAppSettings = async () => {
    try { await SmsBridge.openAppSettings(); }
    catch (e) { notify(e.message || 'Nem sikerült megnyitni az alkalmazás beállításait.'); }
  };
  const refreshDebug = async () => {
    if (!native) { notify('Az eszköz állapota az Android-alkalmazásban érhető el.'); return; }
    setDebugBusy(true);
    try { applyNativeState(await SmsBridge.getState()); notify('A debug állapota frissítve.'); }
    catch (e) { setNativeError(e.message); notify('Nem sikerült lekérni az eszköz állapotát.'); }
    finally { setDebugBusy(false); }
  };
  const retryEmail = async id => { try { await SmsBridge.retryEmail({ id }); notify('Az e-mailt várólistára tettük.'); } catch(e) { notify(e.message); } };
  return <div className={`app-shell ${native ? "is-native" : ""}`}>
    <header className="header"><div className="header-inner"><a href="#" className="brand" onClick={e => { e.preventDefault(); setTab('overview'); }} aria-label="SMSFWD kezdőlap"><span className="brand-mark"><MessageSquare size={22} strokeWidth={2.5} /><span /></span><span>SMS<span className="brand-light">FWD</span><span className="brand-dot">.</span></span></a><nav className="desktop-nav" aria-label="Főmenü">{tabs.map(t => <button key={t.id} className={tab === t.id ? 'selected' : ''} onClick={() => setTab(t.id)}>{t.title}</button>)}</nav><button className="header-test" onClick={() => editRule(freshRule())}>Új szabály <Plus size={16} /></button></div></header>
    <main>
      <section className="workspace container"><div className="workspace-heading"><div><div className="eyebrow"><span /> A TE TOVÁBBÍTÓ KÖZPONTOD</div><h2>{tabs.find(t => t.id === tab).title}</h2></div><div className={`status-pill ${!active ? 'paused' : ''}`}><span className="status-dot" />{active ? 'Továbbítás bekapcsolva' : 'Továbbítás szünetel'}<Toggle checked={active} label="Továbbítás bekapcsolása" onChange={() => { setActive(!active); notify(active ? 'A továbbítás szünetel.' : 'A továbbítás bekapcsolva.'); }} /></div></div>
      {!native && <div className="mode-strip"><span className="mode-tag">INTERAKTÍV BEMUTATÓ</span><span>A tesztüzenetek helyben futnak. Valódi SMS-t vagy e-mailt nem küldünk.</span><button onClick={() => setModal({ type: 'how' })}><CircleHelp size={16} /><span>Részletek</span></button></div>}
      {storageError && <div className="warning-strip">Nem sikerült menteni az eszközre. Ellenőrizd a böngésző tárhelybeállításait.</div>}
      {nativeError && <div className="warning-strip">Az Android-kapcsolat hibát jelzett: {nativeError}</div>}
      {native && !permission && <div className="mode-strip permission-strip"><span>Az automatikus továbbításhoz SMS-fogadási engedély kell. Ha az Android nem mutat engedélykérést, ellenőrizd az alkalmazásengedélyeket. A próba-e-mail ettől függetlenül használható.</span><button onClick={enablePermission}>SMS-fogadás engedélyezése <ArrowRight size={16} /></button><button onClick={openAppSettings}>Alkalmazásengedélyek megnyitása <ExternalLink size={16} /></button></div>}
      {tab === 'overview' && <><div className="stats-grid"><Stat icon={SlidersHorizontal} label="Aktív szabály" value={String(enabledCount).padStart(2, '0')} note={`${rules.length} szabályból`} /><Stat icon={Send} label="Elküldött üzenet" value={String(sentCount).padStart(2, '0')} note="Valódi küldések" /><Stat icon={Clock3} label="Várakozó üzenet" value={String(pendingCount).padStart(2, '0')} note="Előkészített küldések" /><Stat icon={MessageSquare} label="Mai esemény" value={String(todayCount).padStart(2, '0')} note="Teszt és eszközesemény" /></div><div className="dashboard-grid simple-dashboard"><section className="panel rules-panel"><div className="panel-heading"><div><h3>Továbbítási szabályok <span className="number-badge">{rules.length}</span></h3><p>Egy kis beállítás. Sokkal kevesebb feladat.</p></div><button className="small-add" onClick={() => editRule(freshRule())}><Plus size={16} /> Új szabály</button></div><div className="rule-list">{rules.slice(0, 3).map(rule => <RuleRow key={rule.id} rule={rule} onEdit={() => editRule(rule)} onToggle={() => toggleRule(rule.id)} />)}{rules.length === 0 && <Empty icon={SlidersHorizontal} title="Még nincs szabályod" body="Hozd létre az elsőt, és próbáld ki egy tesztüzenettel." />}</div><button className="panel-footer" onClick={() => setTab('rules')}>Összes szabály megtekintése <ArrowRight size={16} /></button></section></div><section className="panel activity-panel"><div className="panel-heading"><div><h3>Legutóbbi események</h3><p>Mindig tudod, mi történt az üzeneteiddel.</p></div><button className="text-button" onClick={() => setTab('history')}>Összes előzmény <ArrowRight size={16} /></button></div>{history.length ? <HistoryList items={history.slice(0, 4)} onRetry={native ? retryEmail : null} /> : <div className="empty-history"><span className="empty-icon"><Inbox size={23} /></span><div><h4>Tiszta lap. Minden a helyén.</h4><p>Az új bejövő SMS-ek továbbítási eseményei itt jelennek meg.</p></div></div>}</section></>}
      {tab === 'rules' && <section className="panel"><div className="panel-heading"><div><h3>A szabályaid <span className="number-badge">{rules.length}</span></h3><p>A mintaszabályokat szabadon átalakíthatod.</p></div><button className="button primary compact" onClick={() => editRule(freshRule())}><Plus size={16} /> Új szabály</button></div>{rules.map(rule => <RuleRow key={rule.id} rule={rule} onEdit={() => editRule(rule)} onToggle={() => toggleRule(rule.id)} />)}{!rules.length && <Empty icon={SlidersHorizontal} title="Készítsd el az első szabályt" body="Válassz egy feladót vagy kulcsszót, majd add meg a célt." />}</section>}
      {tab === 'history' && <section className="panel"><div className="panel-heading"><div><h3>Küldési előzmények</h3><p>A bemutató és a valódi eszközesemények külön jelölést kapnak.</p></div><button className="text-button" onClick={() => setModal({ type: 'clear' })} disabled={!history.length}><Trash2 size={16} /> Törlés</button></div><div className="history-controls"><label className="search-field"><Search size={17} /><input aria-label="Keresés az előzményekben" placeholder="Keresés feladó, címzett, szöveg alapján…" value={search} onChange={e => setSearch(e.target.value)} /></label><select aria-label="Események szűrése" value={historyFilter} onChange={e => setHistoryFilter(e.target.value)}><option value="all">Minden esemény</option><option value="simulated">Tesztüzenetek</option><option value="native">Eszközesemények</option></select></div>{(() => { const items = history.filter(h => `${h.sender} ${h.target} ${h.body}`.toLocaleLowerCase('hu').includes(search.toLocaleLowerCase('hu')) && (historyFilter === 'all' || (historyFilter === 'simulated' ? h.simulated : !h.simulated))); return items.length ? <HistoryList items={items} onRetry={native ? retryEmail : null} /> : <Empty icon={Inbox} title="Nincs megjeleníthető esemény" body="Próbálj ki egy tesztüzenetet, vagy módosítsd a szűrést." />; })()}</section>}
      {tab === 'settings' && <div className="settings-grid"><div className="settings-column"><section className="panel">
        <div className="panel-heading"><div><h3>Eszköz és működés</h3><p>Átlátható beállítások, meglepetések nélkül.</p></div><Settings2 size={20} /></div>
        <div className="setting-row"><div><h4>Környezet</h4><p>{native ? 'Android-alkalmazás' : 'Böngészős bemutató'}</p></div><span className="soft-badge">{native ? 'ANDROID' : 'DEMO'}</span></div>
        <div className="setting-row permission-row"><div><h4>SMS fogadása</h4><p>{native ? permission ? 'Engedélyezve. Az SMS-ek e-mailre és telefonra is továbbíthatók a szabályok alapján.' : 'Nincs engedélyezve. Az automatikus továbbításhoz szükséges.' : 'Android-telefonon érhető el.'}</p></div>{native ? <button className="small-add" onClick={enablePermission} disabled={permission}>SMS-fogadás engedélyezése</button> : <Smartphone size={21} />}</div>
        <div className="setting-row permission-row"><div><h4>SMS küldése</h4><p>{native ? sendPermission ? 'Engedélyezve. Telefonra továbbításkor a szolgáltatód díjszabása érvényes.' : 'Nincs engedélyezve. Csak telefonra továbbításhoz kell; e-mailhez nem szükséges.' : 'Android-telefonon érhető el.'}</p></div>{native ? <button className="small-add" onClick={enableSendPermission} disabled={sendPermission}>SMS-küldés engedélyezése</button> : <Smartphone size={21} />}</div>
        {native && <div className="setting-row permission-settings-row"><div><h4>Android-engedélyek</h4><p>Ha az engedélykérő ablak nem jelenik meg, itt ellenőrizheted az SMSFWD engedélyeit. A tiltás pontos okát a telefon beállításai mutathatják meg.</p></div><button className="small-add" onClick={openAppSettings}>Alkalmazásengedélyek megnyitása <ExternalLink size={15} /></button></div>}
        <div className="setting-row"><div><h4>Napi SMS-limit</h4><p>A küldhető SMS-részek száma. A hosszú SMS több részből állhat.</p></div><input className="limit-input" type="number" min="1" max="1000" aria-label="Napi SMS szegmenslimit" value={limit} onChange={e => { const n = Number(e.target.value); if (Number.isInteger(n) && n >= 1 && n <= 1000) setLimit(n); }} /></div>
        <div className="setting-row"><div><h4>Szabály ellenőrzése</h4><p>Opcionális szimuláció, valódi küldés nélkül.</p></div><button className="small-add" onClick={() => setModal({ type: 'test' })}>Szabály próba</button></div>
        <EmailSettings metadata={emailState} onSaved={setEmailState} notify={notify} />
      </section><DebugPanel state={debugState} emailConfigured={emailState.configured} active={active} rules={rules} busy={debugBusy} onRefresh={refreshDebug} /></div><section className="dark-card settings-help"><ShieldCheck size={27} /><h3>A saját eszközöd.<br />A saját szabályaid.</h3><p>A beállításokat helyben tároljuk. A bemutató nem küld adatot külső szolgáltatásnak.</p><p>Androidon az SMS a saját SIM-edről megy ki, a szolgáltatód díjszabása szerint. Az e-mailre továbbításhoz SMS-fogadás kell, SMS-küldési engedély nem. A próba-e-mailhez egyik SMS-engedély sem szükséges.</p></section></div>}
      <div className="bottom-note"><ShieldCheck size={15} /><span>{native ? 'SMSFWD 0.3.2 · Android · A küldés szolgáltatói díjjal járhat.' : 'SMSFWD 0.3.2 · Az első lépés a nyugodtabb munkanaphoz.'}</span><span className="made-for">Belső használatra tervezve <ArrowUpRight size={13} /></span></div>
      </section>
    </main>
    <nav className="mobile-nav" aria-label="Mobilmenü">{tabs.map(t => <button key={t.id} className={tab === t.id ? 'selected' : ''} onClick={() => { setTab(t.id); document.querySelector('.workspace')?.scrollIntoView({ behavior: 'smooth' }); }}><t.icon size={20} /><span>{t.title}</span></button>)}</nav>
    {modal && <Modal onClose={() => setModal(null)}>{modal.type === 'rule' ? <RuleForm key={modal.rule.id} initial={modal.rule} onCopy={copyRule} onSave={saveRule} onDelete={() => setModal({ type: 'delete', rule: modal.rule })} onClose={() => setModal(null)} existing={rules.some(r => r.id === modal.rule.id)} native={native} /> : modal.type === 'test' ? <TestForm onTest={runTest} onClose={() => setModal(null)} /> : modal.type === 'delete' ? <Confirm title="Törlöd a szabályt?" body={`A(z) „${modal.rule.name}” szabályt eltávolítjuk. A korábbi események megmaradnak.`} action="Szabály törlése" onConfirm={() => removeRule(modal.rule.id)} onClose={() => setModal(null)} /> : modal.type === 'clear' ? <Confirm title="Törlöd az előzményeket?" body="Ez az előzmények megjelenítését törli. A szabályok megmaradnak; a már várakozó vagy elkezdett küldéseket nem állítja le." action="Előzmények törlése" onConfirm={async () => { if (native) { try { await SmsBridge.clearHistory(); } catch { notify('Az Android-előzményeket nem sikerült törölni.'); return; } } setHistory([]); setModal(null); notify('Az előzmények törölve.'); }} onClose={() => setModal(null)} /> : <HowItWorks onClose={() => setModal(null)} />}</Modal>}
    {toast && <div className="toast" role="status"><CheckCircle2 size={18} />{toast}<button aria-label="Értesítés bezárása" onClick={() => setToast('')}><X size={16} /></button></div>}
  </div>;
}
function FlowIllustration() { return <div className="flow-art" aria-label="Illusztráció: SMS továbbítása e-mailre és telefonra"><div className="flow-grid" /><div className="flow-top-label"><span className="status-dot" /> ÍGY MŰKÖDIK AZ SMSFWD <span>01 / 03</span></div><div className="incoming-card"><div className="incoming-top"><span className="icon-tile turquoise"><MessageSquare size={23} /></span><div><b>Új üzenet érkezett</b><span>SMS · most</span></div><span className="tiny-dot" /></div><p>„Megérkezett a munkahelyi csomag.<br />Átvehető az irodában.”</p><div className="incoming-bottom"><span>BEÉRKEZŐ ÜZENET</span><span>+36 30 ••• ••67</span></div></div><div className="flow-connector"><span /><div><Zap size={18} /></div><span /></div><div className="target-cards"><div><span className="icon-tile blue"><Mail size={23} /></span><b>E-mailre</b><p>A megfelelő kollégának.</p><span className="target-check"><Check size={13} /> Szabály alapján</span></div><div><span className="icon-tile turquoise"><Smartphone size={23} /></span><b>Telefonra</b><p>A megfelelő pillanatban.</p><span className="target-check"><Check size={13} /> Saját SIM-ről</span></div></div><div className="flow-floating"><span className="floating-icon"><ShieldCheck size={21} /></span><div><span>EGYSZERŰ. ÁTLÁTHATÓ. AUTOMATIKUS.</span><b>Egy üzenet sem vész szem elől.</b></div><ArrowUpRight size={20} /></div></div>; }
function Stat({ icon: Icon, label, value, note }) { return <div className="stat"><div className="stat-top"><span>{label}</span><Icon size={18} /></div><div className="stat-bottom"><b>{value}</b><span>{note}</span></div></div>; }
function RuleRow({ rule, onEdit, onToggle }) { const Icon = rule.channel === 'email' ? Mail : Smartphone; return <div className={`rule-row ${!rule.enabled ? 'disabled-rule' : ''}`}><span className={`icon-tile ${rule.channel === 'email' ? 'turquoise' : 'blue'}`}><Icon size={21} /></span><button className="rule-copy" onClick={onEdit}><span className="rule-name">{rule.name} {rule.sample && <span className="sample-label">MINTA</span>}</span><span className="rule-detail">{(rule.keywords ?? rule.keyword) ? `„${(rule.keywords ?? rule.keyword).replaceAll('\n', ' / ')}”` : rule.senderType === 'any' ? 'Bármely feladó' : rule.sender} <ArrowRight size={12} /> {getTargets(rule).length > 1 ? `${getTargets(rule).length} címzett` : rule.target}</span></button><Toggle checked={rule.enabled} onChange={onToggle} label={`${rule.name} szabály bekapcsolása`} /><button className="icon-button" aria-label={`${rule.name} szerkesztése`} onClick={onEdit}><ChevronRight size={19} /></button></div>; }
const statusLabels = { simulated: 'Teszt sikeres', sent: 'Elküldve', pending: 'Várakozik', blocked: 'Beállítás szükséges', failed: 'Sikertelen', unknown: 'Bizonytalan küldés', skipped: 'Kihagyva', in_flight: 'Küldés alatt' };
function HistoryList({ items, onRetry }) { return <div className="history-list">{items.map(h => <div className="history-row" key={h.id}><span className={`icon-tile ${h.status === 'failed' ? 'red' : 'pale'}`}>{h.channel === 'email' ? <Mail size={19} /> : <Smartphone size={19} />}</span><div className="history-copy"><b>{h.ruleName || h.sender}</b><span>{h.target || h.sender}</span><p>{h.body}</p>{h.error && <small>{h.error}</small>}{onRetry && h.channel === "email" && h.status === "blocked" && <button className="text-button" onClick={() => onRetry(h.id)}>E-mail újraküldése <Send size={13} /></button>}</div><div className="history-meta"><span className={`event-badge ${['blocked', 'unknown', 'failed'].includes(h.status) ? 'amber' : ''}`}>{h.simulated && <span className="badge-dot" />}{statusLabels[h.status] || h.status}</span><span>{time(h.at)}{h.simulated ? ' · Szimuláció' : ' · Android'}</span></div></div>)}</div>; }
function Empty({ icon: Icon, title, body }) { return <div className="empty"><span className="empty-icon"><Icon size={25} /></span><h4>{title}</h4><p>{body}</p></div>; }
function Modal({ children, onClose }) { useEffect(() => { const before = document.activeElement; const handler = e => { if (e.key === 'Escape') onClose(); if (e.key === 'Tab') { const elements = [...document.querySelectorAll('.modal button:not(:disabled), .modal input, .modal select, .modal textarea, .modal a[href]')]; const first = elements[0], last = elements[elements.length - 1]; if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last?.focus(); } else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus(); } } }; const old = document.body.style.overflow; document.body.style.overflow = 'hidden'; document.addEventListener('keydown', handler); document.querySelector('.modal input, .modal button')?.focus(); return () => { document.body.style.overflow = old; document.removeEventListener('keydown', handler); before?.focus(); }; }, []); return <div className="modal-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) onClose(); }}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title"><button className="modal-close icon-button" aria-label="Bezárás" onClick={onClose}><X size={21} /></button>{children}</section></div>; }
function RuleForm({ initial, onSave, onClose, onDelete, onCopy, existing, native }) { const [rule, setRule] = useState(() => ({ ...initial, targets: undefined, target: getTargets(initial).join('\n'), keywords: initial.keywords ?? initial.keyword ?? '', keywordMode: initial.keywordMode || 'any' })); const [error, setError] = useState(''); const update = (key, value) => { setRule(r => ({ ...r, [key]: value })); setError(''); }; return <form onSubmit={e => { e.preventDefault(); const error = validateRule(rule); if (error) setError(error); else onSave(rule); }}><div className="eyebrow"><span /> EGY EGYSZERŰ BEÁLLÍTÁS</div><h2 id="modal-title">{existing ? 'Szabály szerkesztése' : 'Új továbbítási szabály'}</h2><p className="modal-intro">Mondd meg, melyik üzenet hová érkezzen.</p>{existing && <button type="button" className="small-add rule-copy-button" onClick={() => onCopy(rule)}><Copy size={16} /> Szabály másolása</button>}<label className="field">Szabály neve<input autoFocus value={rule.name} maxLength={80} onChange={e => update('name', e.target.value)} placeholder="Például: Irodai értesítések" /></label><div className="form-grid"><label className="field">Melyik feladótól?<select value={rule.senderType} onChange={e => update('senderType', e.target.value)}><option value="any">Bármely feladó</option><option value="specific">Megadott feladó</option></select></label><label className="field">Kulcsszavak <span className="optional">soronként egy szó vagy kifejezés</span><textarea value={rule.keywords} onChange={e => update('keywords', e.target.value)} placeholder="egyszer használatos jelszava&#10;InfoCert" rows={3} maxLength={1000} /></label></div><button type="button" className="small-add" onClick={() => { update('keywords', 'egyszer használatos jelszava\nInfoCert'); update('keywordMode', 'any'); }}>Jelszavas SMS-ek mintája</button><label className="field">Szövegfeltétel<select value={rule.keywordMode} onChange={e => update('keywordMode', e.target.value)}><option value="any">Bármelyik megadott szó vagy kifejezés szerepel</option><option value="all">Mindegyik megadott szó vagy kifejezés szerepel</option></select></label><p className="email-security">Teljes szavakat és kifejezéseket keresünk, kis- és nagybetűtől függetlenül. Az ékezetek számítanak. Üres listával nincs szövegszűrés.</p>{rule.senderType === 'specific' && <label className="field">Feladó száma vagy neve<input value={rule.sender} onChange={e => update('sender', e.target.value)} placeholder="+36301234567 vagy betűs feladó" /></label>}<div className="field">Hová továbbítsuk?<div className="channel-selector"><button type="button" className={rule.channel === 'email' ? 'chosen' : ''} onClick={() => update('channel', 'email')}><Mail size={19} /> E-mail címre</button><button type="button" className={rule.channel === 'sms' ? 'chosen' : ''} onClick={() => update('channel', 'sms')}><Smartphone size={19} /> Telefonszámra</button></div></div><label className="field">{rule.channel === 'email' ? 'Címzett e-mail címe' : 'Címzett telefonszáma'}<textarea value={rule.target} onChange={e => update('target', e.target.value)} placeholder={rule.channel === 'email' ? 'kollega@ceg.hu\nmasik@ceg.hu' : '+36301234567\n+36307654321'} rows={3} /><span className="email-security">Soronként egy címzett. SMS-nél minden címzett és minden üzenetrész külön díjazható.</span></label><div className="form-note"><ShieldCheck size={17} /><p>{native && rule.channel === 'sms' ? 'Engedélyezett SMS-hozzáféréssel az aktív szabály valódi, díjköteles SMS-t küldhet a saját SIM-edről.' : native ? 'Az automatikus e-mailhez állítsd be a küldő postafiókot a Beállításokban.' : 'A bemutatóban tesztelheted a szabályt. Valódi üzenetet nem küldünk.'}</p></div>{error && <p className="form-error" role="alert">{error}</p>}<div className="modal-actions">{existing ? <button className="text-button danger" type="button" onClick={onDelete}><Trash2 size={16} /> Törlés</button> : <button className="text-button" type="button" onClick={onClose}>Mégse</button>}<button className="button primary" type="submit">Szabály mentése <Check size={17} /></button></div></form>; }
function TestForm({ onTest, onClose }) { const [sender, setSender] = useState('+36309876543'); const [body, setBody] = useState('Megérkezett a munkahelyi csomag. Átvehető az irodában.'); const [result, setResult] = useState(null); return <form onSubmit={e => { e.preventDefault(); if (sender.trim() && body.trim()) setResult(onTest({ sender: sender.trim(), body: body.trim() })); }}><div className="eyebrow"><span /> KOCKÁZATMENTES PRÓBA</div><h2 id="modal-title">Próbáljuk ki együtt.</h2><p className="modal-intro">Egy tesztüzenettel megnézheted, melyik szabályod lép működésbe.</p><label className="field">Feladó<input value={sender} onChange={e => { setSender(e.target.value); setResult(null); }} required /></label><label className="field">Üzenet szövege<textarea value={body} onChange={e => { setBody(e.target.value); setResult(null); }} rows={4} required /></label><div className="form-note"><Zap size={17} /><p>Ez kizárólag szimuláció. Nem küld SMS-t vagy e-mailt, és nincs küldési költsége.</p></div>{result && <div className={`test-result ${result.matches.length ? 'success' : ''}`} role="status"><b>{result.paused ? 'A továbbítás szünetel.' : result.matches.length ? `${result.matches.length} célra továbbítaná az alkalmazás.` : 'Egyetlen szabály sem illeszkedik.'}</b>{result.matches.map(r => <span key={`${r.id}:${r.target}`}>{r.channel === 'email' ? <Mail size={15} /> : <Smartphone size={15} />}{r.target}</span>)}{!result.paused && !result.matches.length && <p>Ellenőrizd a feladót, a kulcsszót és a szabály bekapcsolását.</p>}</div>}<div className="modal-actions"><button className="text-button" type="button" onClick={onClose}>Bezárás</button><button className="button primary" type="submit">Teszt indítása <ArrowUpRight size={17} /></button></div></form>; }
function Confirm({ title, body, action, onConfirm, onClose }) { return <><h2 id="modal-title">{title}</h2><p className="modal-intro">{body}</p><div className="modal-actions"><button className="text-button" onClick={onClose}>Mégse</button><button className="button primary" onClick={onConfirm}>{action} <Trash2 size={16} /></button></div></>; }
function HowItWorks({ onClose }) { return <><div className="eyebrow"><span /> MINDEN ÜZENET JÓ HELYRE KERÜL</div><h2 id="modal-title">Három lépés. Ennyi.</h2><div className="how-step"><span>01</span><div><h3>Válaszd ki az üzeneteket.</h3><p>Feladó és kulcsszó alapján állíts be egyszerű szabályt.</p></div></div><div className="how-step"><span>02</span><div><h3>Add meg a címzettet.</h3><p>E-mail cím vagy telefonszám: te döntöd el, hol van szükség az üzenetre.</p></div></div><div className="how-step"><span>03</span><div><h3>Ellenőrizd egy teszttel.</h3><p>A szimuláció megmutatja, melyik célra menne az SMS, valódi küldés nélkül.</p></div></div><div className="form-note"><Smartphone size={19} /><p>Androidon a valódi SMS-továbbításhoz engedély és működő SIM kell. Az automatikus e-mailhez a Beállításokban add meg a küldő SMTP-postafiókot. A mintaszabályok csak a bemutatóban működnek.</p></div><button className="button primary full" onClick={onClose}>Értem, kezdjük <ArrowRight size={17} /></button></>; }
function DebugPanel({ state, emailConfigured, active, rules, busy, onRefresh }) {
  const detail = value => native ? value || 'Nem elérhető' : 'Androidon érhető el';
  const flag = value => !native ? 'Androidon érhető el' : !state ? 'Betöltés…' : value ? 'Igen' : 'Nem';
  const realRules = rules.filter(rule => !rule.sample);
  const rows = [
    ['Alkalmazásverzió', state?.appVersion || '0.3.2'],
    ['Android-verzió', detail(state?.androidVersion)],
    ['Gyártó', detail(state?.deviceManufacturer)],
    ['Készülékmodell', detail(state?.deviceModel)],
    ['SMS-fogadás engedélyezve', flag(state?.receiveGranted)],
    ['SMS-küldés engedélyezve', flag(state?.sendGranted)],
    ['SMS-fogadást már kértünk', flag(state?.receiveRequested)],
    ['SMS-küldést már kértünk', flag(state?.sendRequested)],
    ['Android új fogadási kérés előtt magyarázatot javasol', flag(state?.receiveRationale)],
    ['Android új küldési kérés előtt magyarázatot javasol', flag(state?.sendRationale)],
    ['Küldő postafiók', emailConfigured ? 'Beállítva' : 'Nincs beállítva'],
    ['Továbbítás kapcsolója', active ? 'Bekapcsolva' : 'Szünetel'],
    ['Eszközön mentett továbbítás', flag(state?.active)],
    ['Automatikus SMS-feldolgozás', !native ? 'Androidon érhető el' : active && state?.receiveGranted ? 'Engedély és kapcsoló rendben' : 'Engedélyre vagy bekapcsolásra vár'],
    ['Összes szabály', String(rules.length)],
    ['Valódi szabályok', String(realRules.length)],
    ['Aktív valódi szabályok', String(realRules.filter(rule => rule.enabled).length)],
  ];
  return <section className="panel debug-panel" aria-labelledby="debug-title"><div className="panel-heading"><div><h3 id="debug-title">Hibakeresés</h3><p>Engedélyek és működési állapot. SMS-szöveg, címek és jelszavak nélkül.</p></div><button className="small-add" onClick={onRefresh} disabled={busy}><RefreshCw size={15} />{busy ? 'Frissítés…' : 'Debug frissítése'}</button></div><dl className="debug-details">{rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl><p className="debug-note">Az Android jelzései segítik a hibakeresést, de önmagukban nem mondják meg, miért nem engedélyezhető az SMS. A telefon alkalmazásengedélyeinél további információ jelenhet meg.</p></section>;
}
function GmailGuide({ onClose }) {
  return <><div className="eyebrow"><span /> SEGÍTSÉG A POSTAFIÓKHOZ</div><h2 id="modal-title">Gmail beállítási útmutató</h2><p className="modal-intro">A Gmail alkalmazás telepítése önmagában nem ad hozzáférést az SMSFWD-nek. A küldéshez külön engedély vagy alkalmazásjelszó kell.</p>
    <div className="how-step"><span>01</span><div><h3>Kapcsolódás Google-fiókkal</h3><p>Androidon válaszd a „Gmail összekapcsolása” gombot. Válaszd ki a küldő Google-fiókot, majd engedélyezd az e-mail-küldést. A telefonon már meglévő fiók is kiválasztható. Az első használat előtt az SMSFWD-t regisztrálni kell a Google-nél; ennek lépéseit a <a href="https://github.com/zsrolandict/SMSFWD/blob/smsfwd-0.3.2-20261009/docs/google-cloud-beallitas.md" target="_blank" rel="noreferrer">Google Cloud beállítási útmutató</a> mutatja. Ha a Google nem engedi a kapcsolódást, használd az alábbi SMTP-beállítást.</p></div></div>
    <div className="how-step"><span>02</span><div><h3>SMTP: hozz létre alkalmazásjelszót</h3><p>A küldő Google-fiók Biztonság beállításában kapcsold be a kétlépcsős azonosítást, ha még nincs bekapcsolva. Ezután nyisd meg az <a href="https://myaccount.google.com/apppasswords" target="_blank" rel="noreferrer">Alkalmazásjelszavak oldalt</a>, és hozz létre egy „SMSFWD” nevű alkalmazásjelszót. Egyes munkahelyi vagy védett fiókokban ez a lehetőség nem érhető el. <a href="https://support.google.com/accounts/answer/185833?hl=hu" target="_blank" rel="noreferrer">Google segítség</a>.</p></div></div>
    <div className="how-step"><span>03</span><div><h3>Add meg a küldő postafiókot</h3><p>Az SMTP-beállításban a teljes Gmail-címedet, a smtp.gmail.com kiszolgálót és a 587 · STARTTLS kapcsolatot add meg. Az alkalmazásjelszó mezőbe a Google által létrehozott 16 karakteres jelszó kerül; a szóközöket kihagyhatod. A normál Google-jelszavad helyett ezt használd.</p></div></div>
    <div className="how-step"><span>04</span><div><h3>Ellenőrizd valódi próba-e-maillel</h3><p>Mentsd a postafiókot, add meg a próba címzettjét, és indítsd a küldést. Az Előzményekben látod az eredményt. Ehhez SMS-engedély és bekapcsolt továbbítás sem kell. Az automatikus SMS → e-mail továbbításhoz már SMS-fogadási engedély és aktív szabály szükséges.</p></div></div>
    <div className="form-note"><ShieldCheck size={18} /><p>Az alkalmazásjelszót csak a telefonodon add meg. A Google-kapcsolat vagy az alkalmazásjelszó később visszavonható a Google-fiókodban.</p></div><button className="button primary full" onClick={onClose}>Értem <Check size={17} /></button>
  </>;
}
function EmailSettings({ metadata, onSaved, notify }) {
  const [editing, setEditing] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [form, setForm] = useState({ host: 'smtp.gmail.com', port: 587, address: '', password: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [target, setTarget] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const googleConnected = metadata.provider === 'gmail' && (metadata.gmailConnected ?? Boolean(metadata.address));
  const reconnectRequired = googleConnected && metadata.reconnectRequired;
  const smtpConfigured = metadata.smtpConfigured ?? (metadata.configured && metadata.provider !== 'gmail');
  const smtpAddress = metadata.smtpAddress ?? (metadata.provider !== 'gmail' ? metadata.address : '');
  const editingSameSmtp = Boolean(smtpConfigured && form.address.trim().toLowerCase() === (smtpAddress || '').trim().toLowerCase()
    && form.host.trim().toLowerCase() === (metadata.host || 'smtp.gmail.com').trim().toLowerCase()
    && Number(form.port) === Number(metadata.port || 587));
  const change = (key, value) => { setForm(current => ({ ...current, [key]: value })); setError(''); };
  const open = () => {
    setForm({ host: metadata.host || 'smtp.gmail.com', port: metadata.port || 587, address: smtpConfigured ? smtpAddress || '' : '', password: '' });
    setError(''); setShowPassword(false); setEditing(!editing);
  };
  const updateAccount = async result => {
    const account = result?.email || (typeof result?.configured === 'boolean' ? result : (await SmsBridge.getState()).email);
    onSaved(account); setEditing(false); setForm(current => ({ ...current, password: '' })); setShowPassword(false);
    return account;
  };
  const connectGoogle = async () => {
    if (!native) { setError('A Google-fiókot az Android-alkalmazásban tudod összekapcsolni.'); return; }
    setConnecting(true); setError('');
    try { await updateAccount(await GmailBridge.connect()); notify('A Gmail-fiók összekapcsolva. Ellenőrizd valódi próba-e-maillel.'); }
    catch (e) { setError(e.message || 'A Google-fiók összekapcsolása nem sikerült. Próbáld újra, vagy használd az SMTP-postafiók beállítását.'); }
    finally { setConnecting(false); }
  };
  const disconnectGoogle = async () => {
    setConnecting(true); setError('');
    try {
      const result = await GmailBridge.disconnect();
      const account = await updateAccount(result);
      const warning = result?.warning || account?.warning;
      if (warning) setError(warning); else notify('A Gmail-kapcsolat leválasztva.');
    }
    catch (e) { setError(e.message || 'Nem sikerült leválasztani a Gmail-fiókot.'); }
    finally { setConnecting(false); }
  };
  const saveAccount = async e => {
    e.preventDefault();
    if (!native) { setError('A postafiókot az Android-alkalmazásban tudod menteni. Itt nem tárolunk jelszót.'); return; }
    if (!editingSameSmtp && !form.password.trim()) { setError('Új vagy módosított SMTP-postafiókhoz add meg az alkalmazásjelszót.'); return; }
    setBusy(true); setError('');
    try {
      await SmsBridge.saveEmail(form);
      onSaved({ ...metadata, configured: true, provider: 'smtp', host: form.host, port: Number(form.port), address: form.address, smtpConfigured: true, smtpAddress: form.address });
      setForm(current => ({ ...current, password: '' })); setEditing(false); notify('A postafiók mentve. Ellenőrizd valódi próba-e-maillel.');
    } catch (e) { setError(e.message || 'Nem sikerült menteni.'); }
    finally { setBusy(false); }
  };
  const togglePassword = async () => {
    if (!showPassword && !form.password && editingSameSmtp && native) {
      try { const saved = await SmsBridge.revealEmailPassword(); setForm(current => ({ ...current, password: saved.password })); }
      catch (e) { setError(e.message); return; }
    }
    setShowPassword(!showPassword);
  };
  const sendTest = async e => {
    e.preventDefault(); setBusy(true); setError('');
    try { await SmsBridge.queueTestEmail({ target }); notify('Valódi próba-e-mail várólistára téve. Az eredmény az Előzményekben látható.'); }
    catch (e) { setError(e.message || 'Nem sikerült a próba.'); }
    finally { setBusy(false); }
  };
  return <div className="email-settings"><div className="setting-row"><div><h4 className="setting-title">Automatikus e-mail <button className="info-button" type="button" aria-label="Gmail beállítási útmutató" aria-haspopup="dialog" onClick={() => setHelpOpen(true)}><Info size={17} /></button></h4><p>{reconnectRequired ? `Google-fiók: ${metadata.address}. A küldési engedély megújítására vár.` : metadata.configured ? `Küldő: ${metadata.address}${googleConnected ? ' · Google-kapcsolat' : ''}. Ellenőrizd valódi próba-e-maillel.` : 'A címzett mellé küldő postafiók is kell. Kapcsold össze a Gmail-fiókot, vagy állíts be SMTP-postafiókot.'}</p></div><button className="small-add" onClick={open} disabled={connecting || busy}>{editing ? 'Bezárás' : googleConnected ? 'SMTP-postafiók beállítása' : metadata.configured ? 'Módosítás' : 'Postafiók beállítása'}</button></div>
    <div className="google-connect-row"><p>{reconnectRequired ? 'A Google-fiók küldési engedélyét újra meg kell adni. Válaszd a Gmail újraengedélyezését.' : googleConnected ? 'A Google-fiók engedélyével küldünk. Alkalmazásjelszó nem szükséges ehhez a kapcsolathoz.' : 'Válaszd ki a telefonon lévő Google-fiókot, és engedélyezd az e-mail-küldést.'}</p><button className={`button ${googleConnected && !reconnectRequired ? 'secondary' : 'primary'} compact`} onClick={googleConnected && !reconnectRequired ? disconnectGoogle : connectGoogle} disabled={connecting || busy}>{connecting ? 'Kapcsolódás a Google-fiókhoz…' : reconnectRequired ? 'Gmail újraengedélyezése' : googleConnected ? 'Gmail leválasztása' : 'Gmail összekapcsolása'} {googleConnected && !reconnectRequired ? <X size={16} /> : <Mail size={16} />}</button>{reconnectRequired && <button className="text-button" onClick={disconnectGoogle} disabled={connecting || busy}>Gmail leválasztása <X size={16} /></button>}</div>
    {editing && <form className="email-form" onSubmit={saveAccount}><div className="form-note"><Mail size={18} /><p>Gmail: smtp.gmail.com, 587-es port és Google-alkalmazásjelszó. A lépéseket az e-mail-beállítás melletti ⓘ gombbal nyithatod meg. {googleConnected && 'A mentés az SMTP-postafiókot választja ki a küldéshez.'} Microsoft 365 esetén a normál jelszavas SMTP gyakran nem támogatott; ehhez a szolgáltatóhoz még nincs OAuth-bejelentkezés.</p></div><label className="field">Küldő e-mail címe<input type="email" required value={form.address} onChange={e => change('address', e.target.value)} autoComplete="username" placeholder="A küldő Gmail-fiók címe" /></label><div className="form-grid"><label className="field">SMTP-kiszolgáló<input required value={form.host} onChange={e => change('host', e.target.value)} placeholder="smtp.gmail.com" /></label><label className="field">Titkosított kapcsolat<select value={form.port} onChange={e => change('port', Number(e.target.value))}><option value={587}>587 · STARTTLS</option><option value={465}>465 · TLS</option></select></label></div><label className="field">Alkalmazásjelszó<span className="password-input"><input type={showPassword ? 'text' : 'password'} autoComplete="new-password" required={!editingSameSmtp} value={form.password} onChange={e => change('password', e.target.value)} placeholder={editingSameSmtp ? 'Üresen hagyva a mentett jelszó marad' : 'Csak itt, a telefonodon add meg'} /><button type="button" aria-label={showPassword ? 'Jelszó elrejtése' : 'Jelszó megjelenítése'} aria-pressed={showPassword} onClick={togglePassword}>{showPassword ? <EyeOff size={19} /> : <Eye size={19} />}</button></span></label><p className="email-security">A jelszó titkosítva marad a telefonon. A szem a beírt jelszót mutatja; üres mezőnél a mentettet kérheted le, telefonfeloldással, ha van képernyőzár. Nem mentjük a böngésző tárhelyére.</p><button disabled={busy || connecting} className="button primary" type="submit">{busy ? 'Mentés…' : 'Postafiók mentése'} <Check size={16} /></button></form>}
    {native && metadata.configured && <form className="email-form" onSubmit={sendTest}><label className="field">Valódi próba-e-mail címzettje<input type="email" required value={target} onChange={e => setTarget(e.target.value)} placeholder="A próba címzettjének e-mail-címe" /></label><p className="email-security">Ez valódi e-mailt küld. SMS-engedély és bekapcsolt továbbítás nélkül is működik. Az eredményt az Előzményekben ellenőrizheted.</p><button disabled={busy || connecting} className="button primary" type="submit">Próba-e-mail küldése <Send size={16} /></button></form>}
    {error && <p className="form-error" role="alert">{error}</p>}
    {helpOpen && <Modal onClose={() => setHelpOpen(false)}><GmailGuide onClose={() => setHelpOpen(false)} /></Modal>}
  </div>;
}
createRoot(document.getElementById('root')).render(<App />);
