(() => {
  const badge = document.createElement('div');
  badge.textContent = 'Extension Hub active';
  badge.style.cssText = [
    'position:fixed',
    'right:12px',
    'bottom:12px',
    'z-index:2147483647',
    'padding:8px 10px',
    'border-radius:10px',
    'background:#111',
    'color:#fff',
    'font:12px sans-serif',
    'opacity:.88'
  ].join(';');
  document.documentElement.appendChild(badge);
  setTimeout(() => badge.remove(), 3000);
})();
