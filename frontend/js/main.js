import { api, setUnauthorizedHandler } from './api.js';
import { el, icon, clear, toast, loadStatic, loadSummaries } from './ui.js';
import { renderAuth } from './views/auth.js';
import { discover } from './views/discover.js';
import { explore } from './views/explore.js';
import * as adminViews from './views/admin.js';

const app = document.getElementById('app');
const state = { user: null };

const ROUTES = {
  user: [
    { path: '/discover', label: 'Discover', icon: 'sparkles', view: discover },
    { path: '/explore', label: 'Explore', icon: 'compass', view: explore },
  ],
  admin: [
    { path: '/admin', label: 'Analytics', icon: 'chart', view: adminViews.analytics },
    { path: '/admin/settings', label: 'System settings', icon: 'sliders', view: adminViews.settings },
    { path: '/admin/users', label: 'Users', icon: 'users', view: adminViews.users },
  ],
};

setUnauthorizedHandler(() => {
  if (state.user) {
    state.user = null;
    toast('Your session has expired. Please sign in again.', 'error');
    location.hash = '';
    render();
  }
});

function toggleTheme() {
  const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', next);
  try { localStorage.setItem('pulse-theme', next); } catch (e) { /* storage unavailable */ }
}

async function logout() {
  try { await api.post('/api/auth/logout'); } catch (e) { /* ignore */ }
  state.user = null;
  location.hash = '';
  render();
}

function routesFor(user) { return user.role === 'ADMIN' ? ROUTES.admin : ROUTES.user; }

let renderToken = 0;

async function render() {
  const token = ++renderToken;
  clear(app);
  if (!state.user) {
    renderAuth(app, async (user) => {
      state.user = user;
      await loadSummaries();
      const target = `#${routesFor(user)[0].path}`;
      if (location.hash === target) render(); else location.hash = target; // hashchange re-renders
    });
    return;
  }
  const routes = routesFor(state.user);
  let path = location.hash.replace(/^#/, '') || routes[0].path;
  if (path === '/for-you' || path === '/preferences') path = '/discover'; // old links keep working
  const route = routes.find((r) => r.path === path) || routes[0];
  if (route.path !== path) { location.hash = `#${route.path}`; return; }

  const main = el('main', { class: 'content', id: 'main', tabIndex: -1 });
  const nav = el('nav', { class: 'nav', 'aria-label': 'Primary' }, routes.map((r) =>
    el('a', { href: `#${r.path}`, class: `nav-link ${r.path === route.path ? 'active' : ''}`, 'aria-current': r.path === route.path ? 'page' : null }, icon(r.icon, 18), el('span', {}, r.label))));

  const dark = () => document.documentElement.getAttribute('data-theme') === 'dark';
  const themeBtn = el('button', { class: 'icon-btn', 'aria-label': 'Toggle dark mode', title: 'Toggle theme', onclick: () => { toggleTheme(); themeBtn.replaceChildren(icon(dark() ? 'sun' : 'moon', 18)); } }, icon(dark() ? 'sun' : 'moon', 18));

  app.append(el('div', { class: 'shell' },
    el('a', { class: 'skip', href: '#main' }, 'Skip to content'),
    el('aside', { class: 'sidebar' },
      el('div', { class: 'brand' }, el('span', { class: 'brand-mark' }, icon('zap', 20)), el('span', {}, 'Pulse')),
      nav,
      el('div', { class: 'sidebar-foot' },
        el('div', { class: 'me' }, el('span', { class: 'avatar' }, state.user.name.charAt(0).toUpperCase()),
          el('div', {}, el('strong', {}, state.user.name), el('div', { class: 'muted small' }, state.user.role === 'ADMIN' ? 'Administrator' : 'Member'))),
        el('div', { class: 'foot-actions' }, themeBtn, el('button', { class: 'btn ghost sm', onclick: logout }, icon('logout', 16), 'Sign out')))),
    main));

  try {
    await route.view(main, { user: state.user, setUser: (u) => { state.user = u; document.querySelector('.me strong').textContent = u.name; } });
  } catch (e) {
    if (token !== renderToken) return;
    main.append(el('div', { class: 'empty' }, el('div', { class: 'empty-emoji' }, '⚠️'), el('h3', {}, 'Something went wrong'), el('p', { class: 'muted' }, e.message)));
  }
}

window.addEventListener('hashchange', () => { if (state.user) render(); });

(async function boot() {
  await loadStatic();
  try {
    const { user } = await api.get('/api/auth/me');
    state.user = user;
    await loadSummaries();
  } catch (e) { /* not signed in */ }
  render();
})();
