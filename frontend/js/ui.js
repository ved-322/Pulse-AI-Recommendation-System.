// Small DOM toolkit. Elements are built with textContent / properties (never innerHTML with data),
// which makes XSS through user- or server-supplied strings impossible by construction.

export function el(tag, props = {}, ...kids) {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k === 'class') n.className = v;
    else if (k === 'dataset') Object.assign(n.dataset, v);
    else if (k === 'style') (typeof v === 'string') ? (n.style.cssText = v) : Object.assign(n.style, v);
    else if (k.startsWith('on') && typeof v === 'function') n.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'for' || k.startsWith('aria-') || k === 'role' || k === 'title' || k === 'data') n.setAttribute(k, v);
    else if (v === true) n.setAttribute(k, '');
    else if (k in n) n[k] = v;
    else n.setAttribute(k, v);
  }
  append(n, kids);
  return n;
}

export function append(parent, kids) {
  for (const k of kids.flat(Infinity)) {
    if (k === null || k === undefined || k === false) continue;
    parent.append(k instanceof Node ? k : document.createTextNode(String(k)));
  }
  return parent;
}

export function clear(node) { node.replaceChildren(); return node; }

// ---- icons (static, trusted markup) ----
const ICONS = {
  sparkles: '<path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9L12 3z"/><path d="M19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8L19 16z"/>',
  compass: '<circle cx="12" cy="12" r="10"/><polygon points="16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88 16.24 7.76"/>',
  sliders: '<line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/>',
  chart: '<line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/>',
  user: '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
  users: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  heart: '<path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>',
  cart: '<circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/>',
  x: '<line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>',
  eye: '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>',
  logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>',
  refresh: '<polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>',
  search: '<circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>',
  moon: '<path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>',
  sun: '<circle cx="12" cy="12" r="4"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>',
  trash: '<polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4h6v2"/>',
  check: '<polyline points="20 6 9 17 4 12"/>',
  info: '<circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>',
  alert: '<path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>',
  trend: '<polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/>',
  zap: '<polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>',
  menu: '<line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="18" x2="21" y2="18"/>',
};

export function icon(name, size = 18) {
  const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  s.setAttribute('viewBox', '0 0 24 24');
  s.setAttribute('width', size);
  s.setAttribute('height', size);
  s.setAttribute('fill', 'none');
  s.setAttribute('stroke', 'currentColor');
  s.setAttribute('stroke-width', '2');
  s.setAttribute('stroke-linecap', 'round');
  s.setAttribute('stroke-linejoin', 'round');
  s.setAttribute('aria-hidden', 'true');
  s.classList.add('icon');
  s.innerHTML = ICONS[name] || '';
  return s;
}

// ---- toasts ----
export function toast(message, kind = 'ok') {
  const host = document.getElementById('toasts');
  const t = el('div', { class: `toast toast-${kind}` }, icon(kind === 'error' ? 'alert' : 'check', 18), el('span', {}, message));
  host.append(t);
  setTimeout(() => t.classList.add('leaving'), 3200);
  setTimeout(() => t.remove(), 3600);
}

// ---- modal ----
export function modal(title, body, actions = [], opts = {}) {
  const previous = document.activeElement;
  const close = () => { overlay.remove(); document.removeEventListener('keydown', onKey); previous?.focus?.(); opts.onClose?.(); };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  const dialog = el('div', { class: `modal ${opts.wide ? 'wide' : ''}`, role: 'dialog', 'aria-modal': 'true', 'aria-label': title },
    el('div', { class: 'modal-head' }, el('h3', {}, title), el('button', { class: 'icon-btn', 'aria-label': 'Close', onclick: close }, icon('x'))),
    el('div', { class: 'modal-body' }, body),
    actions.length ? el('div', { class: 'modal-foot' }, actions.map((a) =>
      el('button', { class: `btn ${a.kind || 'ghost'}`, onclick: async () => { if ((await a.onClick?.()) !== false) close(); } }, a.label))) : null);
  const overlay = el('div', { class: 'overlay', onclick: (e) => { if (e.target === overlay) close(); } }, dialog);
  document.body.append(overlay);
  document.addEventListener('keydown', onKey);
  dialog.querySelector('button.btn.primary, button.btn.danger, .icon-btn')?.focus();
  return close;
}

export function confirmDialog(title, message, confirmLabel, kind = 'danger') {
  return new Promise((resolve) => {
    let settled = false;
    const done = (v) => { if (!settled) { settled = true; resolve(v); } };
    const close = modal(title, el('p', { class: 'muted' }, message), [
      { label: 'Cancel', kind: 'ghost', onClick: () => done(false) },
      { label: confirmLabel, kind, onClick: () => done(true) },
    ]);
    // closing via Esc / overlay resolves as cancelled
    const obs = new MutationObserver(() => { if (!document.querySelector('.overlay')) { done(false); obs.disconnect(); } });
    obs.observe(document.body, { childList: true });
    void close;
  });
}

// ---- formatting helpers ----
export const fmt = {
  money: (n) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n),
  pct: (n, d = 1) => `${(n * 100).toFixed(d)}%`,
  int: (n) => new Intl.NumberFormat('en-US').format(n),
  date: (ms) => new Date(ms).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }),
  shortDay: (iso) => new Date(iso + 'T00:00:00').toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
};

export const SOURCE = {
  content: { label: 'Content match', color: 'var(--c-content)', short: 'Content' },
  collaborative: { label: 'Similar users', color: 'var(--c-collab)', short: 'Collaborative' },
  popularity: { label: 'Trending', color: 'var(--c-pop)', short: 'Popularity' },
};

// ---- shared, lazily loaded reference data ----
export const store = {
  categories: [],          // [{name, emoji, color}]
  tags: [],                // popular tags
  photos: new Set(),       // slugs that have a photo in img/
  publicRatings: {},       // slug -> {source, rating, votes, overview}
  summaries: {},           // itemId -> [avg, count] from in-app reviews
};
export const CATEGORY_EMOJI = {};   // name -> emoji (filled by loadStatic)
export const CATEGORY_COLOR = {};   // name -> colour

export async function loadStatic() {
  const get = async (url) => { try { const r = await fetch(url); return r.ok ? await r.json() : null; } catch (e) { return null; } };
  const [cats, photos, pub] = await Promise.all([get('/api/categories'), get('img/index.json'), get('data/public.json')]);
  if (cats) {
    store.categories = cats.categories;
    store.tags = cats.tags;
    cats.categories.forEach((c) => { CATEGORY_EMOJI[c.name] = c.emoji; CATEGORY_COLOR[c.name] = c.color; });
  }
  if (photos) photos.forEach((s) => store.photos.add(s));
  if (pub) store.publicRatings = pub;
}

export async function loadSummaries() {
  try {
    const r = await fetch('/api/reviews/summary', { credentials: 'same-origin' });
    if (r.ok) store.summaries = await r.json();
  } catch (e) { /* ratings are optional */ }
}

/** "4.3 ★ (12)" style rating line from in-app reviews, falling back to a public rating when we have one. */
export function ratingLine(item) {
  const mine = store.summaries[String(item.id)];
  const pub = store.publicRatings[item.slug];
  if (!mine && !pub) return null;
  const wrap = el('div', { class: 'rating-line' });
  if (mine) wrap.append(el('span', { class: 'stars', title: `${mine[0]} out of 5 from ${mine[1]} reviews` }, starText(mine[0]), el('b', {}, mine[0].toFixed(1)), el('span', { class: 'muted' }, `(${mine[1]})`)));
  if (pub) wrap.append(el('span', { class: 'pub-rating', title: `${pub.source} audience rating` }, `${pub.source} ${Number(pub.rating).toFixed(1)}${pub.votes ? ` · ${compact(pub.votes)}` : ''}`));
  return wrap;
}
export function starText(avg) {
  const full = Math.round(avg);
  return el('span', { class: 'star-glyphs', 'aria-hidden': 'true' }, '★'.repeat(full), el('i', {}, '★'.repeat(5 - full)));
}
const compact = (n) => new Intl.NumberFormat('en', { notation: 'compact' }).format(n);

// ---- product photos: frontend/img/<slug>.jpg, listed in img/index.json; items without one keep illustrated art ----
/** Layers the real photo (if we have one) over an item's illustrated art tile. */
export function addPhoto(artNode, item) {
  if (!store.photos.has(item.slug)) return;
  const img = new Image();
  img.className = 'photo';
  img.alt = item.name;
  img.decoding = 'async';
  img.addEventListener('load', () => { artNode.classList.add('has-photo'); artNode.prepend(img); });
  img.src = `img/${item.slug}.jpg`;
}

export function greeting() {
  const h = new Date().getHours();
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
}

export function skeletonGrid(n = 6) {
  return el('div', { class: 'grid' }, Array.from({ length: n }, () =>
    el('div', { class: 'card skeleton-card' }, el('div', { class: 'skeleton art' }), el('div', { class: 'skeleton line' }), el('div', { class: 'skeleton line short' }))));
}

export function emptyState(emoji, title, text, action) {
  return el('div', { class: 'empty' }, el('div', { class: 'empty-emoji' }, emoji), el('h3', {}, title), el('p', { class: 'muted' }, text), action || null);
}
