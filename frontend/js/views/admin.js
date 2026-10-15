import { api } from '../api.js';
import { el, icon, clear, toast, modal, confirmDialog, fmt, SOURCE, CATEGORY_EMOJI, emptyState, store } from '../ui.js';
import { trendChart, barList, stackedBar } from '../charts.js';

// ---------------------------------------------------------------- Analytics dashboard

export async function analytics(root) {
  let days = 14;
  const body = el('div', {});
  const range = el('div', { class: 'segmented wide', role: 'tablist' });

  const drawRange = () => {
    clear(range);
    [[7, '7 days'], [14, '14 days'], [30, '30 days'], [90, '90 days']].forEach(([d, label]) =>
      range.append(el('button', { class: `seg ${d === days ? 'on' : ''}`, role: 'tab', 'aria-selected': String(d === days), onclick: () => { days = d; drawRange(); load(); } }, label)));
  };
  drawRange();

  root.append(
    el('header', { class: 'page-head' },
      el('div', {}, el('h1', {}, 'Recommendation analytics'), el('p', { class: 'muted' }, 'How well are the recommendations performing — and what should we tune?')),
      range),
    body);

  async function load() {
    body.classList.add('loading');
    try {
      const d = await api.get(`/api/admin/analytics?days=${days}`);
      clear(body).append(...render(d));
    } catch (e) {
      clear(body).append(emptyState('⚠️', 'Could not load analytics', e.message));
    } finally { body.classList.remove('loading'); }
  }
  await load();
}

function kpi(label, value, hint, iconName, tone = '') {
  return el('div', { class: `kpi ${tone}` },
    el('div', { class: 'kpi-icon' }, icon(iconName, 20)),
    el('div', {}, el('div', { class: 'kpi-label' }, label), el('div', { class: 'kpi-value' }, value), el('div', { class: 'kpi-hint muted' }, hint)));
}

function render(d) {
  const k = d.kpis;
  const sources = d.bySource.map((s) => ({ ...s, label: SOURCE[s.key]?.label || s.key, color: SOURCE[s.key]?.color }));
  const cats = [...d.byCategory].sort((a, b) => b.ctr - a.ctr).map((c) => ({ ...c, label: `${CATEGORY_EMOJI[c.key] || ''} ${c.key}` }));

  return [
    el('section', { class: 'kpis' },
      kpi('Recommendations shown', fmt.int(k.impressions), `${fmt.int(k.activeUsers)} of ${fmt.int(k.users)} users reached`, 'sparkles'),
      kpi('Click-through rate', fmt.pct(k.ctr), `${fmt.int(k.clicks)} clicks`, 'trend', 'good'),
      kpi('Like rate', fmt.pct(k.likeRate), 'liked or bought', 'heart'),
      kpi('Conversion', fmt.pct(k.conversionRate), 'purchases per impression', 'cart'),
      kpi('Dismiss rate', fmt.pct(k.dismissRate), 'marked “not interested”', 'x', k.dismissRate > 0.12 ? 'warn' : ''),
      kpi('Catalogue', fmt.int(k.items), `across ${store.categories.length} categories`, 'compass')),

    el('section', { class: 'panel' },
      el('div', { class: 'panel-head' }, el('h2', {}, 'Engagement over time'), el('span', { class: 'muted small' }, `Last ${d.windowDays} days`)),
      trendChart(d.daily)),

    el('div', { class: 'two-col' },
      el('section', { class: 'panel' },
        el('div', { class: 'panel-head' }, el('h2', {}, 'Which signal works best?'), el('span', { class: 'muted small' }, 'Click-through by algorithm')),
        sources.length ? barList(sources, { colorOf: (r) => r.color, caption: (r) => `${fmt.int(r.impressions)} shown · ${fmt.int(r.clicks)} clicked` }) : emptyState('📭', 'No data', 'No recommendations in this period.')),
      el('section', { class: 'panel' },
        el('div', { class: 'panel-head' }, el('h2', {}, 'Category performance'), el('span', { class: 'muted small' }, 'Click-through by category')),
        cats.length ? barList(cats, { colorOf: () => 'var(--brand)', caption: (r) => `${fmt.int(r.impressions)} shown · ${fmt.pct(r.dismissRate, 0)} dismissed` }) : emptyState('📭', 'No data', 'No recommendations in this period.'))),

    el('div', { class: 'two-col' },
      el('section', { class: 'panel' },
        el('div', { class: 'panel-head' }, el('h2', {}, 'Top performing items')),
        d.topItems.length ? el('table', { class: 'table' },
          el('thead', {}, el('tr', {}, ['Item', 'Shown', 'Clicks', 'CTR'].map((h) => el('th', {}, h)))),
          el('tbody', {}, d.topItems.map((t) => el('tr', {}, el('td', {}, t.key), el('td', {}, fmt.int(t.impressions)), el('td', {}, fmt.int(t.clicks)), el('td', {}, fmt.pct(t.ctr)))))) : emptyState('📭', 'No data', '')),
      el('section', { class: 'panel' },
        el('div', { class: 'panel-head' }, el('h2', {}, 'Suggested improvements')),
        el('div', { class: 'suggestions' }, d.suggestions.map(suggestionCard)))),
  ];
}

function suggestionCard(s) {
  const iconName = { tip: 'zap', warn: 'alert', good: 'check', info: 'info' }[s.level] || 'info';
  return el('div', { class: `suggestion ${s.level}` },
    el('div', { class: 'sug-icon' }, icon(iconName, 18)),
    el('div', { class: 'sug-body' }, el('strong', {}, s.title), el('p', { class: 'muted' }, s.detail),
      s.apply ? el('button', { class: 'btn primary sm', onclick: async (e) => {
        const btn = e.currentTarget;
        btn.disabled = true;
        try {
          await api.put('/api/admin/settings', s.apply);
          toast('Suggestion applied to system settings');
          btn.replaceChildren(icon('check', 14), 'Applied');
        } catch (ex) { toast(ex.message, 'error'); btn.disabled = false; }
      } }, 'Apply suggestion') : null));
}

// ---------------------------------------------------------------- System settings

const SLIDERS = [
  { key: 'weightContent', label: 'Content-based weight', help: 'Matches items to your explicit interests and past behaviour.', min: 0, max: 1, step: 0.05, kind: 'weight', source: 'content' },
  { key: 'weightCollaborative', label: 'Collaborative weight', help: 'Learns from users whose behaviour resembles yours.', min: 0, max: 1, step: 0.05, kind: 'weight', source: 'collaborative' },
  { key: 'weightPopularity', label: 'Popularity weight', help: 'Boosts items that are trending across the community.', min: 0, max: 1, step: 0.05, kind: 'weight', source: 'popularity' },
  { key: 'maxResults', label: 'Recommendations per user', help: 'How many suggestions are shown in one list.', min: 1, max: 24, step: 1, kind: 'int' },
  { key: 'minScore', label: 'Minimum match score', help: 'Hide anything scoring below this threshold.', min: 0, max: 1, step: 0.01, kind: 'score' },
  { key: 'maxPerCategory', label: 'Max items per category', help: 'Keeps the list diverse.', min: 1, max: 10, step: 1, kind: 'int' },
  { key: 'halfLifeDays', label: 'Recency half-life (days)', help: 'Older behaviour counts half as much after this many days.', min: 1, max: 90, step: 1, kind: 'int' },
];

export async function settings(root) {
  const data = await api.get('/api/admin/settings');
  const values = { ...data.settings };
  const defaults = data.defaults;
  const preview = el('div', {});
  const rows = [];

  const share = () => {
    const t = values.weightContent + values.weightCollaborative + values.weightPopularity;
    return t > 0 ? { content: values.weightContent / t, collaborative: values.weightCollaborative / t, popularity: values.weightPopularity / t } : null;
  };

  function drawPreview() {
    clear(preview);
    const sh = share();
    if (!sh) {
      preview.append(el('div', { class: 'form-error' }, 'At least one weight must be above zero.'));
      return;
    }
    preview.append(
      stackedBar(Object.entries(sh).map(([k, v]) => ({ label: SOURCE[k].label, value: v, color: SOURCE[k].color }))),
      el('div', { class: 'legend' }, Object.entries(sh).map(([k, v]) =>
        el('span', { class: 'legend-item' }, el('i', { style: { background: SOURCE[k].color } }), `${SOURCE[k].label} ${Math.round(v * 100)}%`))));
  }

  const format = (s, v) => s.kind === 'weight' ? v.toFixed(2) : s.kind === 'score' ? v.toFixed(2) : String(v);

  const sliderRow = (s) => {
    const out = el('output', { class: 'slider-value' }, format(s, values[s.key]));
    const input = el('input', { type: 'range', min: s.min, max: s.max, step: s.step, value: values[s.key], id: `s-${s.key}`, oninput: (e) => {
      values[s.key] = Number(e.target.value);
      out.textContent = format(s, values[s.key]);
      if (s.kind === 'weight') drawPreview();
    } });
    rows.push({ s, input, out });
    return el('div', { class: 'slider-row' },
      el('div', { class: 'slider-head' }, el('label', { for: `s-${s.key}` }, s.label), out),
      input, el('p', { class: 'muted small' }, s.help));
  };

  const saveBtn = el('button', { class: 'btn primary', onclick: async () => {
    saveBtn.disabled = true;
    try {
      const res = await api.put('/api/admin/settings', values);
      Object.assign(values, res.settings);
      toast(res.message);
    } catch (e) { toast(e.message, 'error'); } finally { saveBtn.disabled = false; }
  } }, 'Save settings');

  const resetBtn = el('button', { class: 'btn ghost', onclick: async () => {
    if (!(await confirmDialog('Reset to defaults?', 'All algorithm parameters will return to their default values.', 'Reset', 'primary'))) return;
    try {
      const res = await api.del('/api/admin/settings');
      Object.assign(values, res.settings);
      rows.forEach(({ s, input, out }) => { input.value = values[s.key]; out.textContent = format(s, values[s.key]); });
      drawPreview();
      toast(res.message);
    } catch (e) { toast(e.message, 'error'); }
  } }, 'Reset to defaults');

  root.append(
    el('header', { class: 'page-head' }, el('div', {}, el('h1', {}, 'System settings'), el('p', { class: 'muted' }, 'Tune the recommendation algorithm. Changes apply to the next recommendation request.'))),
    el('div', { class: 'two-col wide-left' },
      el('section', { class: 'panel' },
        el('h2', {}, 'Signal weights'), el('p', { class: 'muted' }, 'Weights are normalised so they always add up to 100%.'),
        SLIDERS.filter((s) => s.kind === 'weight').map(sliderRow),
        el('div', { class: 'preview' }, el('h3', {}, 'Resulting blend'), preview)),
      el('section', { class: 'panel' },
        el('h2', {}, 'Criteria & parameters'),
        SLIDERS.filter((s) => s.kind !== 'weight').map(sliderRow))),
    el('div', { class: 'action-bar' }, resetBtn, saveBtn));
  drawPreview();
  void defaults;
}

// ---------------------------------------------------------------- Users

export async function users(root, { user: me }) {
  const { users: list } = await api.get('/api/admin/users');
  let filter = '';
  const tbody = el('tbody', {});
  const count = el('span', { class: 'muted small' });

  const search = el('input', { type: 'search', placeholder: 'Search by name or email…', 'aria-label': 'Search users', oninput: (e) => { filter = e.target.value.trim().toLowerCase(); draw(); } });

  function draw() {
    clear(tbody);
    const rows = list.filter((u) => !filter || u.name.toLowerCase().includes(filter) || u.email.toLowerCase().includes(filter));
    count.textContent = `${rows.length} of ${list.length} users`;
    rows.forEach((u) => tbody.append(row(u)));
    if (!rows.length) tbody.append(el('tr', {}, el('td', { colSpan: 6, class: 'muted center' }, 'No users match your search.')));
  }

  function row(u) {
    const self = u.id === me.id;
    const roleSel = el('select', { 'aria-label': `Role for ${u.name}`, disabled: self, onchange: async (e) => {
      try { await api.patch(`/api/admin/users/${u.id}`, { role: e.target.value }); u.role = e.target.value; toast('Role updated'); }
      catch (ex) { toast(ex.message, 'error'); e.target.value = u.role; }
    } }, ['USER', 'ADMIN'].map((r) => el('option', { value: r, selected: r === u.role }, r === 'ADMIN' ? 'Administrator' : 'User')));
    const toggle = el('button', { class: `switch ${u.active ? 'on' : ''}`, role: 'switch', 'aria-checked': String(u.active), 'aria-label': `${u.active ? 'Deactivate' : 'Activate'} ${u.name}`, disabled: self, onclick: async () => {
      try {
        await api.patch(`/api/admin/users/${u.id}`, { active: !u.active });
        u.active = !u.active;
        toggle.classList.toggle('on', u.active);
        toggle.setAttribute('aria-checked', String(u.active));
        toast(u.active ? 'User activated' : 'User deactivated');
      } catch (ex) { toast(ex.message, 'error'); }
    } }, el('span', { class: 'knob' }));
    return el('tr', {},
      el('td', {}, el('div', { class: 'user-cell' }, el('span', { class: 'avatar' }, u.name.charAt(0).toUpperCase()), el('div', {}, el('strong', {}, u.name, self ? el('span', { class: 'badge you' }, 'you') : null), el('div', { class: 'muted small' }, u.email)))),
      el('td', {}, roleSel),
      el('td', {}, fmt.int(u.interactions)),
      el('td', {}, fmt.date(u.createdAt)),
      el('td', {}, toggle),
      el('td', { class: 'right' },
        el('button', { class: 'btn ghost sm', onclick: () => showProfile(u) }, icon('eye', 14), 'Profile'),
        el('button', { class: 'icon-btn danger-btn', disabled: self, title: 'Delete user', 'aria-label': `Delete ${u.name}`, onclick: async () => {
          if (!(await confirmDialog('Delete this user?', `${u.name} and all of their preferences and history will be permanently removed.`, 'Delete user'))) return;
          try { await api.del(`/api/admin/users/${u.id}`); list.splice(list.indexOf(u), 1); draw(); toast('User deleted'); }
          catch (ex) { toast(ex.message, 'error'); }
        } }, icon('trash', 16))));
  }

  async function showProfile(u) {
    try {
      const p = await api.get(`/api/admin/users/${u.id}`);
      const cats = Object.entries(p.categories);
      modal(p.name, el('div', { class: 'profile' },
        el('p', { class: 'muted' }, `${p.email} · joined ${fmt.date(p.createdAt)}`),
        el('h4', {}, 'Category interest'),
        cats.length ? el('div', { class: 'bars' }, cats.map(([c, w]) => el('div', { class: 'bar-row' },
          el('div', { class: 'bar-label' }, `${CATEGORY_EMOJI[c] || ''} ${c}`),
          el('div', { class: 'bar-track' }, el('div', { class: 'bar-fill', style: { width: `${(w / 5) * 100}%`, background: 'var(--brand)' } })),
          el('div', { class: 'bar-value' }, `${w}/5`)))) : el('p', { class: 'muted' }, 'No category preferences set.'),
        el('h4', {}, 'Favourite topics'),
        p.tags.length ? el('div', { class: 'tags' }, p.tags.map((t) => el('span', { class: 'tag' }, `#${t}`))) : el('p', { class: 'muted' }, 'No topics chosen.')));
    } catch (e) { toast(e.message, 'error'); }
  }

  root.append(
    el('header', { class: 'page-head' }, el('div', {}, el('h1', {}, 'User management'), el('p', { class: 'muted' }, 'Review profiles, change roles and control access.')), count),
    el('div', { class: 'search' }, icon('search', 18), search),
    el('section', { class: 'panel flush' }, el('div', { class: 'table-scroll' }, el('table', { class: 'table users' },
      el('thead', {}, el('tr', {}, ['User', 'Role', 'Activity', 'Joined', 'Active', ''].map((h) => el('th', {}, h)))), tbody))));
  draw();
}
