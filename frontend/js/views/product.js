// Product detail dialog: big photo, facts, ratings, and customer reviews (read + write).
import { api } from '../api.js';
import { el, icon, clear, toast, modal, fmt, store, loadSummaries, addPhoto, starText, CATEGORY_EMOJI } from '../ui.js';

const interact = (itemId, type) => api.post('/api/interactions', { itemId, type });

export function openProduct(item, rec) {
  interact(item.id, 'CLICK').catch(() => {});
  const pub = store.publicRatings[item.slug];
  const reviewsHost = el('div', { class: 'reviews' }, el('p', { class: 'muted' }, 'Loading reviews…'));

  const art = el('div', { class: 'detail-art', style: { background: 'radial-gradient(circle at 25% 18%, rgba(255, 214, 10, .38), transparent 48%), linear-gradient(135deg, #232323, #0a0a0a 70%)' } },
    el('span', { class: 'emoji' }, item.emoji));

  const body = el('div', { class: 'product' },
    art,
    el('div', { class: 'product-info' },
      el('div', { class: 'brand-line' }, item.brand, item.meta ? el('span', { class: 'meta' }, ` · ${item.meta}`) : null),
      el('h2', { class: 'product-title' }, item.name),
      el('div', { class: 'product-meta' },
        el('span', { class: 'badge', style: `--c:${item.accent}` }, `${CATEGORY_EMOJI[item.category] || ''} ${item.category}`),
        pub ? el('span', { class: 'pub-rating', title: `Public audience rating from ${pub.source}` }, `${pub.source} ${Number(pub.rating).toFixed(1)}${pub.votes ? ` · ${new Intl.NumberFormat('en', { notation: 'compact' }).format(pub.votes)} votes` : ''}`) : null),
      el('div', { class: 'product-price' }, fmt.money(item.price)),
      el('p', { class: 'product-desc' }, pub?.overview || item.description),
      el('div', { class: 'tags' }, item.tags.map((t) => el('span', { class: 'tag' }, `#${t}`))),
      rec ? el('div', { class: 'why-box' }, icon('sparkles', 16), el('span', {}, `${rec.matchPercent}% match · ${rec.reason}`)) : null,
      el('div', { class: 'product-actions' },
        el('button', { class: 'btn primary', onclick: async (e) => {
          const b = e.currentTarget; b.disabled = true;
          try { await interact(item.id, 'PURCHASE'); toast(`Order placed for ${item.name} (demo)`); b.textContent = 'Purchased ✓'; } catch (ex) { toast(ex.message, 'error'); b.disabled = false; }
        } }, icon('cart', 16), 'Buy now'),
        el('button', { class: 'btn ghost', onclick: async (e) => {
          const b = e.currentTarget;
          try { await interact(item.id, 'LIKE'); toast('Saved to your taste profile'); b.classList.add('liked'); } catch (ex) { toast(ex.message, 'error'); }
        } }, icon('heart', 16), 'Like'))),
    el('div', { class: 'product-reviews' }, el('h3', {}, 'Customer reviews'), reviewsHost));
  addPhoto(art, item);

  modal(item.name, body, [], { wide: true });
  loadReviews();

  async function loadReviews() {
    try {
      const { reviews } = await api.get(`/api/reviews?itemId=${item.id}`);
      draw(reviews);
    } catch (e) { clear(reviewsHost).append(el('p', { class: 'muted' }, e.message)); }
  }

  function draw(reviews) {
    clear(reviewsHost);
    const mine = reviews.find((r) => r.mine);
    const real = reviews.filter((r) => !r.sample);
    const avg = reviews.length ? reviews.reduce((a, r) => a + r.rating, 0) / reviews.length : 0;

    reviewsHost.append(el('div', { class: 'review-summary' },
      reviews.length ? el('div', { class: 'big-rating' }, el('strong', {}, avg.toFixed(1)), starText(avg), el('span', { class: 'muted' }, `${reviews.length} review${reviews.length === 1 ? '' : 's'}`)) : el('p', { class: 'muted' }, 'No reviews yet — be the first.'),
      reviews.length ? histogram(reviews) : null));

    reviewsHost.append(composer(mine));

    const note = reviews.some((r) => r.sample) ? el('p', { class: 'muted small sample-note' }, icon('info', 14), ` Reviews marked “Sample” are demo content for this project; ${real.length} written by real users of this site.`) : null;
    reviewsHost.append(note, el('div', { class: 'review-list' }, reviews.map((r) =>
      el('article', { class: `review ${r.mine ? 'mine' : ''}` },
        el('div', { class: 'review-head' },
          el('span', { class: 'avatar sm' }, r.author.charAt(0).toUpperCase()),
          el('strong', {}, r.mine ? 'You' : r.author),
          starText(r.rating),
          r.sample ? el('span', { class: 'badge sample' }, 'Sample review') : null,
          el('span', { class: 'muted small' }, fmt.date(r.createdAt))),
        el('p', {}, r.body),
        r.mine ? el('button', { class: 'link small', onclick: async () => {
          try { await api.del(`/api/reviews?itemId=${item.id}`); toast('Review removed'); await refresh(); } catch (ex) { toast(ex.message, 'error'); }
        } }, 'Delete my review') : null))));
  }

  function histogram(reviews) {
    const counts = [5, 4, 3, 2, 1].map((s) => reviews.filter((r) => r.rating === s).length);
    const max = Math.max(...counts, 1);
    return el('div', { class: 'histo' }, counts.map((n, i) => el('div', { class: 'histo-row' },
      el('span', {}, `${5 - i}★`), el('div', { class: 'bar-track' }, el('div', { class: 'bar-fill', style: { width: `${(n / max) * 100}%`, background: 'var(--c-pop)' } })), el('span', { class: 'muted' }, n))));
  }

  function composer(mine) {
    let rating = mine?.rating || 0;
    const text = el('textarea', { rows: 3, maxLength: 1000, placeholder: 'Share what you liked or didn’t — help others decide…', 'aria-label': 'Your review' });
    if (mine) text.value = mine.body;
    const picker = el('div', { class: 'star-picker', role: 'radiogroup', 'aria-label': 'Your rating' });
    const drawStars = () => {
      clear(picker);
      for (let i = 1; i <= 5; i++) {
        picker.append(el('button', { type: 'button', class: `star ${i <= rating ? 'on' : ''}`, role: 'radio', 'aria-checked': String(i === rating), 'aria-label': `${i} star${i > 1 ? 's' : ''}`, onclick: () => { rating = i; drawStars(); } }, '★'));
      }
    };
    drawStars();
    return el('div', { class: 'composer' },
      el('strong', {}, mine ? 'Update your review' : 'Write a review'), picker, text,
      el('button', { class: 'btn primary sm', onclick: async (e) => {
        if (!rating) return toast('Please pick a star rating.', 'error');
        const b = e.currentTarget; b.disabled = true;
        try {
          await api.post('/api/reviews', { itemId: item.id, rating, body: text.value });
          toast('Thanks! Your review helps the recommender too.');
          await refresh();
        } catch (ex) { toast(ex.message, 'error'); b.disabled = false; }
      } }, mine ? 'Update review' : 'Post review'));
  }

  async function refresh() { await loadSummaries(); await loadReviews(); }
}
