// Dependency-free SVG charts (no CDN needed, so the demo works offline).
import { el, fmt } from './ui.js';

const NS = 'http://www.w3.org/2000/svg';
function s(tag, attrs = {}, ...kids) {
  const n = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, v);
  kids.flat().forEach((k) => k != null && n.append(k));
  return n;
}
const text = (t) => document.createTextNode(String(t));

/** Two-series area/line chart: impressions vs. clicks per day, with hover tooltips. */
export function trendChart(days) {
  const W = 720, H = 260, P = { l: 44, r: 16, t: 16, b: 32 };
  const iw = W - P.l - P.r, ih = H - P.t - P.b;
  const maxV = Math.max(4, ...days.map((d) => d.impressions));
  const niceMax = Math.ceil(maxV / 4) * 4;
  const x = (i) => P.l + (days.length === 1 ? iw / 2 : (i / (days.length - 1)) * iw);
  const y = (v) => P.t + ih - (v / niceMax) * ih;

  const svg = s('svg', { viewBox: `0 0 ${W} ${H}`, class: 'chart', role: 'img', 'aria-label': 'Impressions and clicks per day' });
  for (let g = 0; g <= 4; g++) {
    const v = (niceMax / 4) * g;
    svg.append(s('line', { x1: P.l, x2: W - P.r, y1: y(v), y2: y(v), class: 'grid-line' }),
      s('text', { x: P.l - 8, y: y(v) + 4, 'text-anchor': 'end', class: 'axis' }, text(Math.round(v))));
  }
  const step = Math.ceil(days.length / 7);
  days.forEach((d, i) => {
    if (i % step === 0 || i === days.length - 1) {
      svg.append(s('text', { x: x(i), y: H - 10, 'text-anchor': 'middle', class: 'axis' }, text(fmt.shortDay(d.key))));
    }
  });

  const series = [
    { key: 'impressions', color: 'var(--chart-1)', label: 'Impressions' },
    { key: 'clicks', color: 'var(--chart-2)', label: 'Clicks' },
  ];
  for (const ser of series) {
    const pts = days.map((d, i) => [x(i), y(d[ser.key])]);
    const line = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ');
    const area = `${line} L${pts[pts.length - 1][0]},${y(0)} L${pts[0][0]},${y(0)} Z`;
    svg.append(s('path', { d: area, fill: ser.color, opacity: 0.1 }),
      s('path', { d: line, fill: 'none', stroke: ser.color, 'stroke-width': 2.5, 'stroke-linejoin': 'round', 'stroke-linecap': 'round' }));
  }
  days.forEach((d, i) => {
    const hit = s('g', { class: 'hit' },
      s('rect', { x: x(i) - iw / days.length / 2, y: P.t, width: iw / days.length, height: ih, fill: 'transparent' }),
      s('circle', { cx: x(i), cy: y(d.impressions), r: 4, fill: 'var(--chart-1)', class: 'dot' }),
      s('circle', { cx: x(i), cy: y(d.clicks), r: 4, fill: 'var(--chart-2)', class: 'dot' }),
      s('title', {}, text(`${fmt.shortDay(d.key)} — ${d.impressions} shown, ${d.clicks} clicked (${fmt.pct(d.ctr)} CTR)`)));
    svg.append(hit);
  });

  const legend = el('div', { class: 'legend' }, series.map((ser) =>
    el('span', { class: 'legend-item' }, el('i', { style: { background: ser.color } }), ser.label)));
  return el('div', { class: 'chart-wrap' }, legend, svg);
}

/** Horizontal bars: one row per group with a track, a value label and a caption. */
export function barList(rows, { valueKey = 'ctr', max, colorOf, caption }) {
  const top = max ?? Math.max(0.01, ...rows.map((r) => r[valueKey]));
  return el('div', { class: 'bars' }, rows.map((r) =>
    el('div', { class: 'bar-row' },
      el('div', { class: 'bar-label' }, r.label ?? r.key),
      el('div', { class: 'bar-track', title: caption ? caption(r) : '' },
        el('div', { class: 'bar-fill', style: { width: `${Math.max(2, (r[valueKey] / top) * 100)}%`, background: colorOf ? colorOf(r) : 'var(--brand)' } })),
      el('div', { class: 'bar-value' }, fmt.pct(r[valueKey])),
      caption ? el('div', { class: 'bar-caption muted' }, caption(r)) : null)));
}

/** Stacked horizontal bar for showing how 100% is split (used for algorithm weights). */
export function stackedBar(parts) {
  return el('div', { class: 'stacked', role: 'img', 'aria-label': parts.map((p) => `${p.label} ${Math.round(p.value * 100)}%`).join(', ') },
    parts.map((p) => el('div', { class: 'stack-seg', style: { flexGrow: Math.max(p.value, 0.0001), background: p.color }, title: `${p.label}: ${Math.round(p.value * 100)}%` },
      p.value >= 0.12 ? `${Math.round(p.value * 100)}%` : '')));
}
