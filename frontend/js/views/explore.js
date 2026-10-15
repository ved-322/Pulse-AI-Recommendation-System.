import { api } from '../api.js';
import { el, icon, clear, store, loadSummaries, emptyState } from '../ui.js';
import { itemCard } from './cards.js';

const PAGE = 48;

export async function explore(root) {
  let category = '';
  let query = '';
  let sort = 'featured';
  let shown = PAGE;
  let items = [];
  const results = el('div', {});
  const filterHost = el('div', {});
  const count = el('span', { class: 'muted small' });
  let timer;

  await loadSummaries();

  const search = el('input', { type: 'search', placeholder: 'Search products, brands, authors or tags…', 'aria-label': 'Search catalogue', oninput: (e) => {
    clearTimeout(timer);
    timer = setTimeout(() => { query = e.target.value.trim(); load(); }, 250);
  } });
  const sorter = el('select', { 'aria-label': 'Sort products', onchange: (e) => { sort = e.target.value; draw(); } },
    [['featured', 'Sort: Featured'], ['price-asc', 'Price: low to high'], ['price-desc', 'Price: high to low'], ['name', 'Name A–Z'], ['brand', 'Brand A–Z']]
      .map(([v, l]) => el('option', { value: v }, l)));

  const chips = el('div', { class: 'chips scroll' });
  const drawChips = () => {
    clear(chips);
    [{ name: '', emoji: '', label: 'All' }, ...store.categories.map((c) => ({ ...c, label: `${c.emoji} ${c.name}` }))].forEach((c) =>
      chips.append(el('button', { class: `chip ${category === c.name ? 'on' : ''}`, onclick: () => { category = c.name; drawChips(); load(); } }, c.label || c.name)));
  };
  drawChips();
  filterHost.append(chips);

  root.append(
    el('header', { class: 'page-head' },
      el('div', {}, el('h1', {}, 'Explore'), el('p', { class: 'muted' }, 'Browse real products from brands like Sony, Apple, Nike and more. Everything you do here teaches the recommender.')), count),
    el('div', { class: 'toolbar' }, el('div', { class: 'search' }, icon('search', 18), search), sorter),
    filterHost, results);

  async function load() {
    try {
      const params = new URLSearchParams();
      if (category) params.set('category', category);
      if (query) params.set('q', query);
      items = (await api.get(`/api/items?${params}`)).items;
      shown = PAGE;
      draw();
    } catch (e) { clear(results).append(emptyState('⚠️', 'Could not load the catalogue', e.message)); }
  }

  function draw() {
    clear(results);
    count.textContent = `${items.length} product${items.length === 1 ? '' : 's'}`;
    const sorted = [...items];
    const by = { 'price-asc': (a, b) => a.price - b.price, 'price-desc': (a, b) => b.price - a.price,
      name: (a, b) => a.name.localeCompare(b.name), brand: (a, b) => a.brand.localeCompare(b.brand) }[sort];
    if (by) sorted.sort(by);
    if (!sorted.length) { results.append(emptyState('🔎', 'No matches', 'Try a different search term or category.')); return; }
    results.append(el('div', { class: 'grid' }, sorted.slice(0, shown).map((i, n) => itemCard(i, { index: n % 12 }))));
    if (shown < sorted.length) {
      results.append(el('div', { class: 'more' }, el('button', { class: 'btn ghost', onclick: () => { shown += PAGE; draw(); } }, `Show more (${sorted.length - shown} left)`)));
    }
  }
  await load();
}
