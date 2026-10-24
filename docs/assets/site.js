// Set google-play-url in index.html when the public store listing is available.
const playUrl = document.querySelector('meta[name="google-play-url"]').content.trim();
if (playUrl && URL.canParse(playUrl)) {
  const url = new URL(playUrl);
  if (url.protocol === 'https:' && url.hostname === 'play.google.com' && url.pathname === '/store/apps/details') {
    document.querySelectorAll('[data-google-play]').forEach(link => {
      link.href = url.href;
      link.removeAttribute('aria-disabled');
      const label = link.querySelector('span');
      label.textContent = 'Descargar en Google Play';
      label.dataset.en = 'Get it on Google Play';
    });
  }
}

const languageButton = document.querySelector('#language');
const translated = [...document.querySelectorAll('[data-en]')];
const screenshots = [...document.querySelectorAll('[data-screen]')];
translated.forEach(element => { element.dataset.es = element.textContent; });
screenshots.forEach(element => { element.dataset.altEs = element.alt; });

function setLanguage(language) {
  const english = language === 'en';
  document.documentElement.lang = language;
  translated.forEach(element => { element.textContent = element.dataset[language]; });
  screenshots.forEach(element => {
    const base = `assets/screens/${element.dataset.screen}-${language}`;
    element.src = `${base}-720.webp`;
    element.srcset = `${base}-360.webp 360w, ${base}-720.webp 720w`;
    element.alt = english ? element.dataset.altEn : element.dataset.altEs;
  });
  languageButton.textContent = english ? 'ES ↗' : 'EN ↗';
  languageButton.setAttribute('aria-label', english ? 'Cambiar a español' : 'Switch to English');
  document.querySelector('nav').setAttribute('aria-label', english ? 'Main navigation' : 'Navegación principal');
  document.querySelector('meta[name="description"]').content = english
    ? 'Free, open-source music tools for Android. Tuner, recorder, songs, chords and metronome. No ads, works offline.'
    : 'Herramientas musicales gratuitas y de código abierto para Android. Afinador, grabador, canciones, acordes y metrónomo. Sin anuncios ni conexión.';
}

// Share either language by URL. The complete Spanish page also works without JS.
const requestedLanguage = new URLSearchParams(location.search).get('lang');
setLanguage(requestedLanguage === 'en' ? 'en' : 'es');
languageButton.hidden = false;
languageButton.addEventListener('click', () => {
  const language = document.documentElement.lang === 'es' ? 'en' : 'es';
  setLanguage(language);
  const url = new URL(location.href);
  url.searchParams.set('lang', language);
  history.replaceState(null, '', url);
});

// Decorative graphics. They are generated here so the HTML stays small; the page reads fine without them.
const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
const narrow = matchMedia('(max-width: 700px)').matches;

// Reveal on scroll, a slow drift for the large illustrations, and a small lift for the hero phone.
const revealed = [...document.querySelectorAll('.reveal')];
if (reduceMotion || !('IntersectionObserver' in window)) {
  revealed.forEach(element => element.classList.add('in'));
} else {
  const observer = new IntersectionObserver(entries => entries.forEach(entry => {
    if (entry.isIntersecting) { entry.target.classList.add('in'); observer.unobserve(entry.target); }
  }), { rootMargin: '0px 0px -10% 0px', threshold: .08 });
  revealed.forEach(element => observer.observe(element));

  const drifting = new Map();
  const driftObserver = new IntersectionObserver(entries => entries.forEach(entry => {
    if (entry.isIntersecting) drifting.set(entry.target, Number(entry.target.dataset.drift));
    else drifting.delete(entry.target);
  }), { rootMargin: '20% 0px' });
  document.querySelectorAll('[data-drift]').forEach(element => driftObserver.observe(element));

  const heroPhone = document.querySelector('.hero-phone');
  let pending = false;
  function update() {
    pending = false;
    const viewport = innerHeight;
    drifting.forEach((amount, element) => {
      const box = element.getBoundingClientRect();
      const progress = (box.top + box.height / 2 - viewport / 2) / viewport;
      element.style.setProperty('--dx', `${Math.round(progress * amount * (narrow ? .5 : 1))}px`);
    });
    if (scrollY < 900) heroPhone.style.setProperty('--py', `${Math.round(scrollY * -.06)}px`);
  }
  addEventListener('scroll', () => { if (!pending) { pending = true; requestAnimationFrame(update); } }, { passive: true });
}
