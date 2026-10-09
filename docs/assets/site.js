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
    element.src = `assets/${element.dataset.screen}-${language}.png`;
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
