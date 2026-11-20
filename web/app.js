// Screen logic of the web app; mirrors MainActivity.kt. Pure logic lives in core.js.
import {
  Transport, parseTsv, resolve, recordHistory, encodeHistory, decodeHistory,
} from './core.js';

const STORAGE_KEY = 'recent_routes';
const LONG_PRESS_MS = 500;
const CAPTION = { [Transport.BUS]: 'Автобус', [Transport.TROLLEYBUS]: 'Троллейбус' };
// Short label keeps six chips readable in one row; screen readers get the full caption.
const CHIP_LABEL = { [Transport.BUS]: 'автобус', [Transport.TROLLEYBUS]: 'тролл.' };

const state = {
  number: '',
  transport: Transport.BUS,
  routes: [],
  recent: loadRecent(),
};

const $ = (id) => document.getElementById(id);
const ui = {
  number: $('number'),
  clear: $('clear'),
  hint: $('hint'),
  open: $('open'),
  openLabel: $('open-label'),
  recent: $('recent'),
  chips: $('chips'),
  zero: document.querySelector('[data-key="0"]'),
  segments: [...document.querySelectorAll('.segment')],
};

// Storage can be unavailable (private mode, blocked site data): history is then per-visit only.
function loadRecent() {
  try {
    return decodeHistory(localStorage.getItem(STORAGE_KEY));
  } catch {
    return [];
  }
}

function saveRecent() {
  try {
    localStorage.setItem(STORAGE_KEY, encodeHistory(state.recent));
  } catch {
    // Ignore: the shortcut list still works for this visit.
  }
}

async function loadRoutes() {
  try {
    const response = await fetch('routes.tsv');
    if (response.ok) {
      state.routes = parseTsv(await response.text());
    }
  } catch {
    // Offline before first cache: every number falls back to a 2ГИС search.
  }
  render();
}

function press(key) {
  if (key === 'C') {
    state.number = '';
  } else if (key === '⌫') {
    state.number = state.number.slice(0, -1);
  } else if (state.number.length < 3 && !(key === '0' && state.number === '')) {
    // Route numbers never start with 0 ("0", "05" are not routes).
    state.number += key;
  }
  render();
}

// Called after the link has been followed, so the href used for navigation is not changed under it.
function routeOpened(transport, number) {
  state.recent = recordHistory(state.recent, { transport, number });
  saveRecent();
  state.transport = transport;
  state.number = '';
  render();
}

function setLink(anchor, target) {
  if (target) {
    anchor.href = target.url;
    anchor.removeAttribute('aria-disabled');
  } else {
    anchor.removeAttribute('href');
    anchor.setAttribute('aria-disabled', 'true');
  }
}

function render() {
  const target = resolve(state.transport, state.number, state.routes);

  ui.number.textContent = state.number || '—';
  ui.clear.hidden = state.number === '';
  ui.zero.disabled = state.number === '';
  if (!target) {
    ui.hint.textContent = 'Введите номер маршрута';
  } else if (target.isDirect) {
    ui.hint.textContent = target.description;
  } else {
    ui.hint.textContent = 'Прямой ссылки нет — откроется поиск в 2ГИС';
  }

  setLink(ui.open, target);
  ui.openLabel.textContent = target && !target.isDirect ? 'Найти в 2ГИС' : 'Открыть маршрут в 2ГИС';

  for (const segment of ui.segments) {
    segment.setAttribute('aria-checked', String(segment.dataset.transport === state.transport));
  }
  renderRecent();
}

function renderRecent() {
  ui.recent.hidden = state.recent.length === 0;
  ui.chips.replaceChildren(...state.recent.map((route) => {
    const chip = document.createElement('a');
    chip.className = 'chip';
    chip.target = '_blank';
    chip.rel = 'noopener';
    chip.href = resolve(route.transport, route.number, state.routes).url;
    chip.setAttribute('aria-label', `${CAPTION[route.transport]} ${route.number}, открыть в 2ГИС`);
    if (route.transport === state.transport && route.number === state.number) {
      chip.classList.add('active');
    }
    const number = document.createElement('b');
    number.textContent = route.number;
    const label = document.createElement('span');
    label.textContent = CHIP_LABEL[route.transport];
    chip.append(number, label);
    chip.addEventListener('click', () => setTimeout(() => routeOpened(route.transport, route.number)));
    return chip;
  }));
}

// --- Events ---

let longPressTimer = 0;
let longPressFired = false;

const keypad = $('keypad');
keypad.addEventListener('click', (event) => {
  const key = event.target.closest('[data-key]');
  if (!key || key.disabled) {
    return;
  }
  if (longPressFired) {
    longPressFired = false; // the long press already cleared the number
    return;
  }
  press(key.dataset.key);
});

// Long press on ⌫ clears the whole number, as on Android.
const backspace = document.querySelector('[data-key="⌫"]');
backspace.addEventListener('pointerdown', () => {
  longPressFired = false;
  longPressTimer = setTimeout(() => {
    longPressFired = true;
    press('C');
  }, LONG_PRESS_MS);
});
for (const type of ['pointerup', 'pointerleave', 'pointercancel']) {
  backspace.addEventListener(type, () => clearTimeout(longPressTimer));
}
backspace.addEventListener('contextmenu', (event) => event.preventDefault());

ui.clear.addEventListener('click', () => press('C'));

for (const segment of ui.segments) {
  segment.addEventListener('click', () => {
    state.transport = segment.dataset.transport;
    render();
  });
}

ui.open.addEventListener('click', (event) => {
  if (ui.open.getAttribute('aria-disabled') === 'true') {
    event.preventDefault();
    return;
  }
  const { transport, number } = state;
  setTimeout(() => routeOpened(transport, number));
});

// Physical keyboard (desktop): digits, Backspace, Escape, Enter.
document.addEventListener('keydown', (event) => {
  if (event.ctrlKey || event.metaKey || event.altKey) {
    return;
  }
  if (/^[0-9]$/.test(event.key)) {
    press(event.key);
  } else if (event.key === 'Backspace') {
    press('⌫');
  } else if (event.key === 'Escape') {
    press('C');
  } else if (event.key === 'Enter' && document.activeElement === document.body) {
    ui.open.click();
  } else {
    return;
  }
  event.preventDefault();
});

render();
loadRoutes();

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js').catch(() => {
    // Offline mode is optional; the app works online without it.
  });
}
