// Discover: ONE screen where you tell the app what you love (top) and the AI's picks update live (bottom).
import { api } from '../api.js';
import { el, icon, clear, toast, modal, SOURCE, store, CATEGORY_EMOJI, CATEGORY_COLOR, greeting, skeletonGrid, emptyState } from '../ui.js';
import { stackedBar } from '../charts.js';
import { itemCard, categoryChips } from './cards.js';

const LEVELS = ['Not interested', 'A little', 'Interested', 'Really into it', 'Big fan', 'Obsessed'];
const MAX_TAGS = 15;
const DNA_SHADES = ['#FFD60A', '#ffffff', '#a3a3a3', '#ffe666', '#d4d4d4', '#7a7a7a', '#fff0a3', '#5c5c5c', '#fffadf', '#c9a800', '#e8e8e8', '#8f8f8f', '#ffeb85', '#bfbfbf'];

const PRESETS = [
  { label: 'Tech lover', emoji: '📱', cats: { 'Phones & Tablets': 5, 'Laptops & PCs': 4, 'Wearables & Smart Home': 3, Gaming: 3 }, tags: ['smartphone', 'wireless', 'smart-home'] },
  { label: 'Creator', emoji: '🎥', cats: { Cameras: 5, Audio: 4, 'Laptops & PCs': 4 }, tags: ['creator', 'video', 'photography'] },
  { label: 'Bookworm', emoji: '📖', cats: { Books: 5, Movies: 4, 'Food & Drink': 3 }, tags: ['sci-fi', 'classic', 'fiction'] },
  { label: 'Gamer', emoji: '🕹️', cats: { Gaming: 5, 'Laptops & PCs': 4, 'Toys & Hobbies': 3 }, tags: ['gaming', 'rgb', 'esports'] },
  { label: 'Fit & active', emoji: '🏃', cats: { 'Fitness & Outdoors': 5, 'Wearables & Smart Home': 4, 'Food & Drink': 3 }, tags: ['running', 'fitness', 'outdoor'] },
  { label: 'Style icon', emoji: '🕶️', cats: { Fashion: 5, 'Beauty & Wellness': 4, Audio: 2 }, tags: ['sneakers', 'streetwear', 'skincare'] },
  { label: 'Cosy home', emoji: '🛋️', cats: { 'Home & Kitchen': 5, 'Food & Drink': 4, 'Beauty & Wellness': 3 }, tags: ['coffee', 'kitchen', 'relax'] },
  { label: 'Family fun', emoji: '🎲', cats: { 'Toys & Hobbies': 5, Movies: 4, 'Food & Drink': 3 }, tags: ['family', 'board-game', 'animation'] },
];

export async function discover(root, { user, setUser }) {
  const prefs = await api.get('/api/preferences');
  const state = { name: prefs.name, cats: { ...prefs.categories }, tags: new Set(prefs.tags) };
  let data = null;
  let filter = '';
  let saveTimer;
  let recToken = 0;

  // ------------------------------------------------------------ studio (interests)
  const statusPill = el('span', { class: 'save-pill saved', role: 'status' });
  const setStatus = (s, msg) => {
    statusPill.className = `save-pill ${s}`;
    statusPill.replaceChildren(icon(s === 'saving' ? 'refresh' : s === 'error' ? 'alert' : 'check', 14),
      s === 'saving' ? 'Tuning the AI…' : s === 'error' ? (msg || 'Could not save') : 'Synced · AI is up to date');
  };
  setStatus('saved');

  const tiles = new Map();
  const tileGrid = el('div', { class: 'tile-grid' }, store.categories.map((cat) => {
    const t = buildTile(cat);
    tiles.set(cat.name, t);
    return t.tile;
  }));

  function buildTile(cat) {
    const label = el('span', { class: 'tile-level' });
    const main = el('button', { class: 'tile-main', type: 'button', 'aria-pressed': 'false', onclick: () => setLevel(cat.name, (state.cats[cat.name] || 0) > 0 ? 0 : 3) },
      el('span', { class: 'tile-emoji' }, cat.emoji), el('span', { class: 'tile-name' }, cat.name));
    const dots = el('div', { class: 'dots', role: 'radiogroup', 'aria-label': `${cat.name} interest level` },
      [1, 2, 3, 4, 5].map((n) => el('button', { type: 'button', class: 'dot', role: 'radio', 'aria-label': LEVELS[n], title: LEVELS[n],
        onclick: () => setLevel(cat.name, state.cats[cat.name] === n ? 0 : n) })));
    const tile = el('div', { class: 'tile', style: `--c:${cat.color}` }, el('span', { class: 'tile-glow' }), main, label, dots);
    const update = () => {
      const v = state.cats[cat.name] || 0;
      tile.classList.toggle('on', v > 0);
      main.setAttribute('aria-pressed', String(v > 0));
      label.textContent = LEVELS[v];
      [...dots.children].forEach((d, i) => { d.classList.toggle('on', i < v); d.setAttribute('aria-checked', String(i + 1 === v)); });
    };
    update();
    return { tile, update };
  }

  function setLevel(name, v) {
    if (v > 0) state.cats[name] = v; else delete state.cats[name];
    const t = tiles.get(name);
    t.update();
    t.tile.classList.remove('pop'); void t.tile.offsetWidth; t.tile.classList.add('pop');
    changed();
  }

  // presets / helpers
  const presetRow = el('div', { class: 'presets' },
    el('span', { class: 'presets-label' }, 'Start from a vibe'),
    PRESETS.map((p) => el('button', { class: 'preset', type: 'button', onclick: () => applyPreset(p) }, el('span', {}, p.emoji), p.label)),
    el('button', { class: 'preset ghost', type: 'button', onclick: surprise }, icon('sparkles', 14), 'Surprise me'),
    el('button', { class: 'preset ghost', type: 'button', onclick: () => { state.cats = {}; state.tags.clear(); syncAll(); changed(); } }, icon('x', 14), 'Clear'));

  function applyPreset(p) {
    state.cats = {};
    for (const [c, v] of Object.entries(p.cats)) if (tiles.has(c)) state.cats[c] = v;
    state.tags = new Set(p.tags);
    syncAll();
    changed();
  }
  function surprise() {
    const names = store.categories.map((c) => c.name).sort(() => Math.random() - 0.5).slice(0, 4);
    state.cats = {};
    names.forEach((n, i) => { state.cats[n] = 5 - i % 3; });
    syncAll();
    changed();
  }
  function syncAll() { tiles.forEach((t) => t.update()); drawTags(); drawDNA(); }

  // tags / vibes
  const tagWrap = el('div', { class: 'chips vibes' });
  const customInput = el('input', { type: 'text', placeholder: 'Add your own topic…', maxLength: 24, 'aria-label': 'Add a custom topic', onkeydown: (e) => {
    if (e.key === 'Enter') { e.preventDefault(); addCustom(); }
  } });
  function addCustom() {
    const t = customInput.value.trim().toLowerCase();
    if (!t) return;
    if (!/^[a-z0-9][a-z0-9 -]{0,23}$/.test(t)) return toast('Topics may contain letters, numbers, spaces and dashes.', 'error');
    if (state.tags.size >= MAX_TAGS) return toast(`You can pick at most ${MAX_TAGS} topics.`, 'error');
    state.tags.add(t); customInput.value = ''; drawTags(); changed();
  }
  function drawTags() {
    clear(tagWrap);
    const selected = [...state.tags];
    const rest = store.tags.filter((t) => !state.tags.has(t));
    [...selected, ...rest].forEach((t) => tagWrap.append(el('button', { type: 'button', class: `chip ${state.tags.has(t) ? 'on' : ''}`, 'aria-pressed': String(state.tags.has(t)), onclick: () => {
      if (state.tags.has(t)) state.tags.delete(t);
      else if (state.tags.size >= MAX_TAGS) return toast(`You can pick at most ${MAX_TAGS} topics.`, 'error');
      else state.tags.add(t);
      drawTags(); changed();
    } }, `#${t}`)));
  }

  // taste DNA
  const dnaHost = el('div', { class: 'dna-wrap' });
  function drawDNA() {
    clear(dnaHost);
    const entries = Object.entries(state.cats).sort((a, b) => b[1] - a[1]);
    if (!entries.length) {
      dnaHost.append(el('p', { class: 'muted' }, 'Pick a few interests above to see your taste profile take shape.'));
      return;
    }
    const total = entries.reduce((a, [, v]) => a + v, 0);
    dnaHost.append(
      el('div', { class: 'dna', role: 'img', 'aria-label': 'Your taste profile' }, entries.map(([c, v], i) =>
        el('div', { class: 'dna-seg', style: { flexGrow: v, background: DNA_SHADES[i % DNA_SHADES.length] }, title: `${c}: ${Math.round((v / total) * 100)}%` }, v / total > 0.12 ? CATEGORY_EMOJI[c] : ''))),
      el('div', { class: 'legend' }, entries.map(([c, v], i) => el('span', { class: 'legend-item' }, el('i', { style: { background: DNA_SHADES[i % DNA_SHADES.length] } }), `${c} ${Math.round((v / total) * 100)}%`))));
  }

  // ------------------------------------------------------------ save + live recommendations
  function changed() {
    drawDNA();
    clearTimeout(saveTimer);
    setStatus('saving');
    saveTimer = setTimeout(save, 650);
  }

  async function save() {
    try {
      const res = await api.put('/api/preferences', { name: state.name, categories: state.cats, tags: [...state.tags] });
      if (res.name !== user.name) { user = { ...user, name: res.name }; setUser(user); }
      setStatus('saved');
      await loadRecs();
    } catch (e) {
      setStatus('error', e.message);
      toast(e.message, 'error');
    }
  }

  const results = el('div', { class: 'ai-results' });
  const filterHost = el('div', {});
  const weightsHost = el('div', { class: 'ai-weights' });
  const subtitle = el('p', { class: 'muted' });
  const refresh = el('button', { class: 'btn ghost', onclick: () => loadRecs(true) }, icon('refresh', 16), 'Refresh');

  async function loadRecs(manual = false) {
    const token = ++recToken;
    results.classList.add('updating');
    if (!data) clear(results).append(skeletonGrid(8));
    refresh.disabled = true;
    try {
      const next = await api.get('/api/recommendations');
      if (token !== recToken) return;
      data = next;
      drawAI();
      if (manual) toast('Recommendations refreshed');
    } catch (e) {
      if (token === recToken) clear(results).append(emptyState('⚠️', 'We could not load recommendations', e.message,
        el('button', { class: 'btn primary', onclick: () => loadRecs() }, 'Try again')));
    } finally {
      if (token === recToken) { results.classList.remove('updating'); refresh.disabled = false; }
    }
  }

  function drawAI() {
    const n = Object.keys(state.cats).length;
    subtitle.textContent = data.coldStart
      ? 'Choose a few interests above and the AI will build your personal feed. Until then, here is what is trending.'
      : `Based on ${n} interest${n === 1 ? '' : 's'}${state.tags.size ? `, ${state.tags.size} topic${state.tags.size === 1 ? '' : 's'}` : ''} and your activity — updates the moment you change anything.`;
    const w = data.weights;
    clear(weightsHost).append(
      stackedBar([
        { label: SOURCE.content.label, value: w.content, color: SOURCE.content.color },
        { label: SOURCE.collaborative.label, value: w.collaborative, color: SOURCE.collaborative.color },
        { label: SOURCE.popularity.label, value: w.popularity, color: SOURCE.popularity.color }]),
      el('div', { class: 'legend' }, Object.values(SOURCE).map((s) => el('span', { class: 'legend-item' }, el('i', { style: { background: s.color } }), s.label))));

    const recs = data.recommendations;
    const cats = [...new Set(recs.map((r) => r.item.category))].sort();
    if (filter && !cats.includes(filter)) filter = '';
    clear(filterHost);
    if (cats.length > 1) filterHost.append(categoryChips(cats, () => filter, (v) => { filter = v; drawAI(); }));

    clear(results);
    const shown = recs.filter((r) => !filter || r.item.category === filter);
    if (!shown.length) {
      results.append(emptyState('🎉', 'You’re all caught up', 'Nothing left to recommend right now. Add more interests above or explore the catalogue.',
        el('a', { class: 'btn primary', href: '#/explore' }, 'Explore catalogue')));
      return;
    }
    results.append(el('div', { class: 'grid' }, shown.map((r, i) => itemCard(r.item, {
      rec: r, index: i, featured: i === 0 && !filter && shown.length > 2,
      onDismiss: () => {
        data.recommendations = data.recommendations.filter((x) => x.item.id !== r.item.id);
        if (!results.querySelector('.item-card')) drawAI();
      },
    }))));
  }

  // ------------------------------------------------------------ page
  const first = () => state.name.split(' ')[0];
  const title = el('h1', {});
  const drawTitle = () => title.replaceChildren(`${greeting()}, ${first()} `, el('span', { class: 'wave' }, '👋'));
  drawTitle();
  const editName = el('button', { class: 'icon-btn sm', title: 'Edit display name', 'aria-label': 'Edit display name', onclick: () => {
    const input = el('input', { type: 'text', value: state.name, maxLength: 80, 'aria-label': 'Display name' });
    modal('Display name', el('div', { class: 'field' }, el('label', {}, 'What should we call you?'), input), [
      { label: 'Cancel', kind: 'ghost' },
      { label: 'Save', kind: 'primary', onClick: () => {
        const v = input.value.trim();
        if (v.length < 2) { toast('Name must be at least 2 characters.', 'error'); return false; }
        state.name = v; drawTitle(); changed();
      } }]);
  } }, icon('sliders', 14));

  root.append(
    el('section', { class: 'studio' },
      el('span', { class: 'aurora a1', 'aria-hidden': 'true' }), el('span', { class: 'aurora a2', 'aria-hidden': 'true' }), el('span', { class: 'aurora a3', 'aria-hidden': 'true' }),
      el('div', { class: 'studio-head' },
        el('div', {}, el('div', { class: 'eyebrow' }, icon('sparkles', 14), 'Interest studio'), el('div', { class: 'title-row' }, title, editName),
          el('p', { class: 'studio-sub' }, 'Tell the AI what you love. Tap a tile, then fine-tune how much with the dots — your picks below update instantly.')),
        statusPill),
      presetRow,
      tileGrid,
      el('div', { class: 'studio-cols' },
        el('div', { class: 'studio-panel' }, el('h3', {}, 'Vibes & topics'), el('p', { class: 'muted small' }, `Pick up to ${MAX_TAGS} things that describe your taste.`), tagWrap,
          el('div', { class: 'add-tag' }, customInput, el('button', { class: 'btn ghost sm', onclick: addCustom }, 'Add'))),
        el('div', { class: 'studio-panel' }, el('h3', {}, 'Your taste DNA'), el('p', { class: 'muted small' }, 'How your attention is split across categories.'), dnaHost))),

    el('div', { class: 'ai-divider' }, el('span', {}, icon('sparkles', 16), 'Picked for you by AI')),
    el('header', { class: 'page-head ai-head' },
      el('div', {}, el('h2', { class: 'ai-title' }, 'Your personal feed'), subtitle),
      el('div', { class: 'ai-tools' }, weightsHost, refresh)),
    filterHost, results);

  drawTags();
  drawDNA();
  await loadRecs();
}
