// SPDX-License-Identifier: GPL-3.0-only
import {setHeroicon, animateHeroicon, stopHeroicons} from './icons.js';
export function initInteractions() {
  const controller = new AbortController();
  const copyButton = document.querySelector('.copy-button');
  const copyStatus = document.querySelector('.copy-status');
  let feedbackTimeout;
  copyButton.addEventListener('click', async () => {
    clearTimeout(feedbackTimeout);
    try {
      await navigator.clipboard.writeText('make build\nmake appimage APK=/path/to/app.apk');
      setHeroicon(copyButton.querySelector('svg'), 'check');
      animateHeroicon(copyButton.querySelector('svg'));
      copyButton.setAttribute('aria-label', 'Build commands copied');
      copyStatus.textContent = 'Copied. Your terminal is ready when you are.';
    } catch {
      const selection = window.getSelection();
      const range = document.createRange();
      range.selectNodeContents(document.querySelector('#build-command'));
      selection.removeAllRanges();
      selection.addRange(range);
      copyStatus.textContent = 'Clipboard unavailable. Select and copy the commands above.';
    }
    feedbackTimeout = setTimeout(() => {
      setHeroicon(copyButton.querySelector('svg'), 'copy');
      copyButton.setAttribute('aria-label', 'Copy build commands');
      copyStatus.textContent = '';
    }, 6000);
  }, {signal: controller.signal});

  // Keep the drawing subordinate to the explanation: scroll only selects a layer.
  const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)');
  const layers = [...document.querySelectorAll('[data-layer]')];
  const steps = [...document.querySelectorAll('[data-step]')];
  let framePending = false;
  let stackFrame = 0;
  function updateStack() {
    framePending = false;
    if (controller.signal.aborted) return;
    if (motionPreference.matches) return;
    const readingLine = innerWidth <= 700 ? innerHeight * .72 : innerHeight * .5;
    let active = 0;
    let closest = Infinity;
    steps.forEach((step, index) => {
      const rect = step.getBoundingClientRect();
      const distance = Math.abs(rect.top + rect.height / 2 - readingLine);
      if (distance < closest) { closest = distance; active = index; }
    });
    layers.forEach((layer, index) => layer.classList.toggle('is-active', index === active));
  }
  function scheduleStack() {
    if (!framePending) { framePending = true; stackFrame = requestAnimationFrame(updateStack); }
  }
  function applyMotion() {
    const enabled = !motionPreference.matches;
    document.documentElement.classList.toggle('motion-enabled', enabled);
    if (!enabled) stopHeroicons();
    if (!enabled) layers.forEach(layer => layer.classList.remove('is-active'));
    scheduleStack();
  }
  motionPreference.addEventListener('change', applyMotion, {signal: controller.signal});
  window.addEventListener('scroll', scheduleStack, {passive: true, signal: controller.signal});
  window.addEventListener('resize', scheduleStack, {passive: true, signal: controller.signal});
  applyMotion();


  // Keep the watermark inside the whole row; touch and reduced motion stay static.
  let ghostFrame = 0;
  const ghostRow = document.querySelector('.shiraz-row');
  if (ghostRow) {

    let ghostPointer;
    ghostRow.addEventListener('pointermove', (event) => {
      if (event.pointerType !== 'mouse' || motionPreference.matches) return;
      ghostPointer = { x: event.clientX, y: event.clientY };
      if (ghostFrame) return;
      ghostFrame = requestAnimationFrame(() => {
        ghostFrame = 0;
        if (controller.signal.aborted) return;
        if (motionPreference.matches) return;
        const bounds = ghostRow.getBoundingClientRect();
        const centerX = Math.max(0, (bounds.width - 90) / 2);
        const x = Math.max(0, Math.min(bounds.width - 90, centerX + (ghostPointer.x - bounds.left - bounds.width / 2) * 0.85));
        const centerY = Math.max(0, (bounds.height - 90) / 2);
        const y = Math.max(0, Math.min(bounds.height - 90, centerY + (ghostPointer.y - bounds.top - bounds.height / 2) * 0.65));
        ghostRow.style.setProperty('--ghost-x', `${x}px`);
        ghostRow.style.setProperty('--ghost-y', `${y}px`);
      });
    }, { passive: true, signal: controller.signal });
  }

  return () => { controller.abort(); clearTimeout(feedbackTimeout); cancelAnimationFrame(stackFrame); cancelAnimationFrame(ghostFrame); document.documentElement.classList.remove("motion-enabled"); };
}
