// Shared product-card building blocks used by Discover and Explore.
import { api } from '../api.js';
import { el, icon, clear, toast, fmt, SOURCE, CATEGORY_EMOJI, addPhoto, ratingLine } from '../ui.js';
import { openProduct } from './product.js';

export const interact = (itemId, type) => api.post('/api/interactions', { itemId, type });

export function artStyle(item) {
  return {
    background: `radial-gradient(circle at 22% 18%, rgba(255, 214, 10, .38), transparent 48%), linear-gradient(135deg, #232323, #0a0a0a 70%)`,
  };
}

export function itemCard(item, { rec, onDismiss, featured = false, index = 0 } = {}) {
  let liked = false;
  const likeBtn = el('button', { class: 'icon-btn like', title: 'Like', 'aria-label': `Like ${item.name}`, onclick: async (e) => {
    e.stopPropagation();
    if (liked) return;
    try {
      await interact(item.id, 'LIKE');
      liked = true;
      likeBtn.classList.add('on');
      toast('Saved to your taste profile');
    } catch (ex) { toast(ex.message, 'error'); }
  } }, icon('heart', 18));

  const buyBtn = el('button', { class: 'icon-btn', title: 'Buy', 'aria-label': `Buy ${item.name}`, onclick: async (e) => {
    e.stopPropagation();
    try {
      await interact(item.id, 'PURCHASE');
      toast(`Order placed for ${item.name} (demo)`);
      card.classList.add('purchased');
      buyBtn.disabled = true;
      likeBtn.disabled = true;
      card.querySelector('.card-art').append(el('span', { class: 'ribbon' }, 'Purchased'));
    } catch (ex) { toast(ex.message, 'error'); }
  } }, icon('cart', 18));

  const parts = rec ? Object.entries(rec.breakdown).map(([k, v]) => ({ label: SOURCE[k].label, value: v, color: SOURCE[k].color })) : [];
  const total = parts.reduce((a, p) => a + p.value, 0) || 1;
  const rating = ratingLine(item);

  const card = el('article', { class: `card item-card ${featured ? 'featured' : ''}`, style: `--i:${Math.min(index, 12)}`, tabIndex: 0, role: 'button', 'aria-label': `${item.name} by ${item.brand}. Open details`,
    onclick: () => openProduct(item, rec),
    onkeydown: (e) => { if ((e.key === 'Enter' || e.key === ' ') && e.target === card) { e.preventDefault(); openProduct(item, rec); } } },
    el('div', { class: `card-art ${['Books', 'Movies'].includes(item.category) ? 'portrait' : ''}`, style: artStyle(item) },
      el('span', { class: 'watermark', 'aria-hidden': 'true' }, item.brand),
      el('span', { class: 'emoji' }, item.emoji),
      featured ? el('span', { class: 'spot-tag' }, icon('sparkles', 14), 'Top pick for you') : null,
      rec ? el('span', { class: 'match', title: 'Blended score from all signals' }, `${rec.matchPercent}% match`) : null),
    el('div', { class: 'card-main' },
      el('div', { class: 'card-body' },
        el('div', { class: 'card-top' }, el('span', { class: 'badge', style: `--c:${item.accent}` }, `${CATEGORY_EMOJI[item.category] || ''} ${item.category}`), el('strong', { class: 'price' }, fmt.money(item.price))),
        el('div', { class: 'brand-line' }, item.brand, item.meta ? el('span', { class: 'meta' }, ` · ${item.meta}`) : null),
        el('h3', { class: 'card-title' }, item.name),
        rating,
        el('p', { class: 'desc' }, item.description),
        el('div', { class: 'tags' }, item.tags.slice(0, 3).map((t) => el('span', { class: 'tag' }, `#${t}`))),
        rec ? el('div', { class: 'why' },
          el('div', { class: 'why-line' }, icon('sparkles', 14), el('span', {}, rec.reason)),
          el('div', { class: 'signal', title: parts.map((p) => `${p.label}: ${Math.round((p.value / total) * 100)}%`).join(' · ') },
            parts.map((p) => el('i', { style: { flexGrow: Math.max(p.value, 0.001), background: p.color } })))) : null),
      el('div', { class: 'card-foot' },
        el('button', { class: 'btn ghost sm', onclick: (e) => { e.stopPropagation(); openProduct(item, rec); } }, icon('eye', 16), 'Details'),
        el('div', { class: 'actions' },
          likeBtn, buyBtn,
          onDismiss ? el('button', { class: 'icon-btn muted-btn', title: 'Not interested', 'aria-label': `Not interested in ${item.name}`, onclick: async (e) => {
            e.stopPropagation();
            try {
              await interact(item.id, 'DISMISS');
              card.classList.add('leaving');
              setTimeout(() => { card.remove(); onDismiss(item); }, 260);
            } catch (ex) { toast(ex.message, 'error'); }
          } }, icon('x', 18)) : null))));
  addPhoto(card.querySelector('.card-art'), item);
  return card;
}

export function categoryChips(categories, active, onPick) {
  const wrap = el('div', { class: 'chips scroll' });
  const draw = () => {
    clear(wrap);
    [['', 'All'], ...categories.map((c) => [c, `${CATEGORY_EMOJI[c] || ''} ${c}`])].forEach(([val, label]) => {
      wrap.append(el('button', { class: `chip ${active() === val ? 'on' : ''}`, onclick: () => { onPick(val); draw(); } }, label));
    });
  };
  draw();
  return wrap;
}
