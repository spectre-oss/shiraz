// SPDX-License-Identifier: GPL-3.0-only
import {setHeroicon, animateHeroicon, stopHeroicons} from './icons.js';
export function initInteractions() {
  const controller = new AbortController();
  // Each code block copies its own text and reports in its own status line.
  const feedbackTimeouts = [];
  document.querySelectorAll('.code-area').forEach((area, index) => {
    const copyButton = area.querySelector('.copy-button');
    const code = area.querySelector('pre');
    const copyStatus = area.parentElement.querySelector('.copy-status');
    const idleLabel = copyButton.getAttribute('aria-label');
    copyButton.addEventListener('click', async () => {
      clearTimeout(feedbackTimeouts[index]);
      try {
        await navigator.clipboard.writeText(code.textContent);
        setHeroicon(copyButton.querySelector('svg'), 'check');
        animateHeroicon(copyButton.querySelector('svg'));
        copyButton.setAttribute('aria-label', 'Commands copied');
        copyStatus.textContent = 'Copied. Your terminal is ready when you are.';
      } catch {
        const selection = window.getSelection();
        const range = document.createRange();
        range.selectNodeContents(code);
        selection.removeAllRanges();
        selection.addRange(range);
        copyStatus.textContent = 'Clipboard unavailable. Select and copy the commands above.';
      }
      feedbackTimeouts[index] = setTimeout(() => {
        setHeroicon(copyButton.querySelector('svg'), 'copy');
        copyButton.setAttribute('aria-label', idleLabel);
        copyStatus.textContent = '';
      }, 6000);
    }, {signal: controller.signal});
  });

  // Scroll only selects a layer and feeds two progress values; CSS draws the rest.
  const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)');
  const header = document.querySelector('.site-header');
  const diagram = document.querySelector('.stack-diagram');
  const equation = document.querySelector('.packaging-path');
  const layers = [...document.querySelectorAll('[data-layer]')];
  const steps = [...document.querySelectorAll('[data-step]')];
  const clamp = value => Math.max(0, Math.min(1, value));
  let framePending = false;
  let stackFrame = 0;
  function updateStack() {
    framePending = false;
    if (controller.signal.aborted) return;
    header?.classList.toggle('is-scrolled', scrollY > 24);
    if (motionPreference.matches) return;
    const readingLine = innerWidth <= 760 ? innerHeight * .72 : innerHeight * .5;
    const centers = steps.map(step => { const rect = step.getBoundingClientRect(); return rect.top + rect.height / 2; });
    let active = 0;
    centers.forEach((center, index) => { if (Math.abs(center - readingLine) < Math.abs(centers[active] - readingLine)) active = index; });
    layers.forEach((layer, index) => layer.classList.toggle('is-active', index === active));
    const span = centers[centers.length - 1] - centers[0];
    if (diagram && span > 0) diagram.style.setProperty('--stack-progress', clamp((readingLine - centers[0]) / span).toFixed(3));
    if (equation) {
      const rect = equation.getBoundingClientRect();
      equation.style.setProperty('--p', clamp((innerHeight - rect.top) / ((innerHeight + rect.height) / 2)).toFixed(3));
    }
  }
  function scheduleStack() {
    if (!framePending) { framePending = true; stackFrame = requestAnimationFrame(updateStack); }
  }
  function clearScrollState() {
    layers.forEach(layer => layer.classList.remove('is-active'));
    diagram?.style.removeProperty('--stack-progress');
    equation?.style.removeProperty('--p');
    art?.style.removeProperty('--sun');
  }
  function applyMotion() {
    const enabled = !motionPreference.matches;
    document.documentElement.classList.toggle('motion-enabled', enabled);
    if (!enabled) { stopHeroicons(); clearScrollState(); }
    scheduleStack();
  }

  // The beam leans a little toward the pointer, easing instead of snapping.
  const hero = document.querySelector('.hero');
  const art = document.querySelector('.hero-art');
  let sun = 0;
  let sunTarget = 0;
  let sunFrame = 0;
  function easeSun() {
    sunFrame = 0;
    if (controller.signal.aborted || motionPreference.matches || !art) return;
    sun += (sunTarget - sun) * .09;
    if (Math.abs(sunTarget - sun) < .002) sun = sunTarget;
    art.style.setProperty('--sun', sun.toFixed(3));
    if (sun !== sunTarget) sunFrame = requestAnimationFrame(easeSun);
  }
  hero?.addEventListener('pointermove', (event) => {
    if (event.pointerType !== 'mouse' || motionPreference.matches) return;
    sunTarget = Math.max(-1, Math.min(1, (event.clientX / innerWidth - .5) * 2));
    if (!sunFrame) sunFrame = requestAnimationFrame(easeSun);
  }, {passive: true, signal: controller.signal});
  hero?.addEventListener('pointerleave', () => {
    sunTarget = 0;
    if (!sunFrame && !motionPreference.matches) sunFrame = requestAnimationFrame(easeSun);
  }, {signal: controller.signal});

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

  return () => { controller.abort(); feedbackTimeouts.forEach(clearTimeout); cancelAnimationFrame(stackFrame); cancelAnimationFrame(sunFrame); cancelAnimationFrame(ghostFrame); clearScrollState(); document.documentElement.classList.remove("motion-enabled"); };
}
