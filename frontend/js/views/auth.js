import { api } from '../api.js';
import { el, icon, clear, store } from '../ui.js';

export function renderAuth(root, onSuccess) {
  let mode = 'login';
  const chosen = new Set();

  const hero = el('aside', { class: 'auth-hero' },
    el('div', { class: 'brand brand-light' }, el('span', { class: 'brand-mark' }, icon('zap', 20)), el('span', {}, 'Pulse')),
    el('div', { class: 'hero-copy' },
      el('h1', {}, 'Discover what you’ll love next.'),
      el('p', {}, 'A hybrid recommendation engine that blends your tastes, people like you, and what’s trending — and shows you exactly why.')),
    el('ul', { class: 'hero-points' },
      el('li', {}, icon('sparkles', 18), el('span', {}, 'Personalised picks that learn from every click')),
      el('li', {}, icon('users', 18), el('span', {}, 'Collaborative filtering across the community')),
      el('li', {}, icon('chart', 18), el('span', {}, 'Transparent, tunable algorithm for admins'))),
    el('div', { class: 'floaters', 'aria-hidden': 'true' },
      ['🎧', '📘', '🚀', '☕', '🏋️', '🎮'].map((e, i) => el('span', { class: `floater f${i}` }, e))));

  const formHost = el('div', {});
  const panel = el('section', { class: 'auth-panel' },
    el('div', { class: 'auth-card' }, formHost));
  root.append(el('main', { class: 'auth-layout' }, hero, panel));
  draw();

  function draw() {
    clear(formHost);
    const err = el('div', { class: 'form-error', role: 'alert', hidden: true });
    const name = el('input', { id: 'name', type: 'text', autocomplete: 'name', placeholder: 'Your name', maxLength: 80 });
    const email = el('input', { id: 'email', type: 'email', autocomplete: 'email', placeholder: 'you@example.com', required: true });
    const password = el('input', { id: 'password', type: 'password', autocomplete: mode === 'login' ? 'current-password' : 'new-password', placeholder: mode === 'login' ? 'Your password' : 'At least 8 characters, letters + numbers', required: true });
    const submit = el('button', { class: 'btn primary block', type: 'submit' }, mode === 'login' ? 'Sign in' : 'Create account');

    const form = el('form', { class: 'form', novalidate: true, onsubmit: async (e) => {
      e.preventDefault();
      err.hidden = true;
      submit.disabled = true;
      submit.textContent = 'Please wait…';
      try {
        const body = mode === 'login'
          ? await api.post('/api/auth/login', { email: email.value, password: password.value })
          : await api.post('/api/auth/register', { name: name.value, email: email.value, password: password.value, categories: [...chosen] });
        onSuccess(body.user);
      } catch (ex) {
        err.textContent = ex.message;
        err.hidden = false;
        submit.disabled = false;
        submit.textContent = mode === 'login' ? 'Sign in' : 'Create account';
      }
    } },
      mode === 'register' ? field('Full name', name) : null,
      field('Email', email),
      field('Password', password),
      mode === 'register' ? el('div', { class: 'field' },
        el('label', {}, 'What are you into? ', el('span', { class: 'muted' }, '(optional)')),
        el('div', { class: 'chips' }, store.categories.map((cat) => {
          const c = cat.name;
          const chip = el('button', { type: 'button', class: 'chip', 'aria-pressed': 'false', onclick: () => {
            chosen.has(c) ? chosen.delete(c) : chosen.add(c);
            chip.classList.toggle('on', chosen.has(c));
            chip.setAttribute('aria-pressed', String(chosen.has(c)));
          } }, `${CATEGORY_EMOJI[c]} ${c}`);
          return chip;
        }))) : null,
      err, submit);

    const demo = mode === 'login' ? el('div', { class: 'demo' },
      el('p', { class: 'muted small' }, 'Presenting? Use a demo account:'),
      el('div', { class: 'demo-row' },
        el('button', { class: 'btn ghost sm', type: 'button', onclick: () => { email.value = 'alice@example.com'; password.value = 'User@123'; } }, icon('user', 16), 'Demo user'),
        el('button', { class: 'btn ghost sm', type: 'button', onclick: () => { email.value = 'admin@recsys.com'; password.value = 'Admin@123'; } }, icon('chart', 16), 'Demo admin'))) : null;

    formHost.append(
      el('h2', {}, mode === 'login' ? 'Welcome back' : 'Create your account'),
      el('p', { class: 'muted' }, mode === 'login' ? 'Sign in to see your personalised picks.' : 'Takes less than a minute.'),
      form, demo,
      el('p', { class: 'auth-switch muted' }, mode === 'login' ? 'New here? ' : 'Already have an account? ',
        el('button', { class: 'link', type: 'button', onclick: () => { mode = mode === 'login' ? 'register' : 'login'; draw(); } },
          mode === 'login' ? 'Create an account' : 'Sign in')));
    (mode === 'login' ? email : name).focus();
  }

  function field(label, input) {
    return el('div', { class: 'field' }, el('label', { for: input.id }, label), input);
  }
}
