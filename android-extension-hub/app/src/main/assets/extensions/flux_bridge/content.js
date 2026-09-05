(() => {
  if (window.top !== window) return;

  const marker = document.createElement('meta');
  marker.name = 'flux-webos-extension-runtime';
  marker.content = '0.1.0';
  document.head?.appendChild(marker);

  console.debug('[Flux WebOS] built-in extension active on', location.href);
})();
