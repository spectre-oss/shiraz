// SPDX-License-Identifier: MIT
// SVG paths and motion adapted from heroicons-animated, copyright (c) 2026 Aniket Pawar.
// See licenses/heroicons-animated.txt and licenses/heroicons.txt.
const heroiconPaths = {
  "arrow": "<path d=\"M13.5 4.5 21 12m0 0-7.5 7.5\"/><path d=\"M21 12H3\"/>",
  "external": "<path d=\"m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25\"/>",
  "copy": "<path d=\"M8.25 7.5V6.10822C8.25 4.97324 9.09499 4.01015 10.2261 3.91627C10.5994 3.88529 10.9739 3.85858 11.3495 3.83619M15.75 18H18C19.2426 18 20.25 16.9926 20.25 15.75V6.10822C20.25 4.97324 19.405 4.01015 18.2739 3.91627C17.9006 3.88529 17.5261 3.85858 17.1505 3.83619M17.1505 3.83619C16.8672 2.91757 16.0116 2.25 15 2.25H13.5C12.4884 2.25 11.6328 2.91757 11.3495 3.83619M17.1505 3.83619C17.2152 4.04602 17.25 4.26894 17.25 4.5V5.25H11.25V4.5C11.25 4.26894 11.2848 4.04602 11.3495 3.83619\"/><path d=\"M6.75 7.5H4.875C4.25368 7.5 3.75 8.00368 3.75 8.625V20.625C3.75 21.2463 4.25368 21.75 4.875 21.75H14.625C15.2463 21.75 15.75 21.2463 15.75 20.625V16.5C15.75 11.5294 11.7206 7.5 6.75 7.5Z\"/><path d=\"M15.75 18.75V16.875C15.75 15.011 14.239 13.5 12.375 13.5H10.875C10.2537 13.5 9.75 12.9963 9.75 12.375V10.875C9.75 9.01104 8.23896 7.5 6.375 7.5H5.25\"/>",
  "check": "<path d=\"m4.5 12.75 6 6 9-13.5\"/>",
  "pause": "<path d=\"M15.75 5.25v13.5\"/><path d=\"M8.25 5.25v13.5\"/>",
  "play": "<path d=\"M5.25 5.653c0-.856.917-1.398 1.667-.986l11.54 6.347a1.125 1.125 0 0 1 0 1.972l-11.54 6.347a1.125 1.125 0 0 1-1.667-.986V5.653Z\"/>",
  "plus": "<path d=\"M5 12h14\"/><path d=\"M12 5v14\"/>",
  "down": "<path d=\"M19.5 13.5 12 21m0 0-7.5-7.5\"/><path d=\"M12 21V3\"/>",
  "turn": "<path d=\"m16.49 12 3.75 3.75m0 0-3.75 3.75m3.75-3.75H3.74V4.499\"/>"
};

const runningIconAnimations = new Set();
export function setHeroicon(svg, name) {
  if (!svg || !heroiconPaths[name]) return;
  if (svg.dataset.icon === name) return;
  svg.innerHTML = heroiconPaths[name];
  svg.dataset.icon = name;
}
export function stopHeroicons() {
  runningIconAnimations.forEach(animation => animation.cancel());
  runningIconAnimations.clear();
}
export function animateHeroicon(svg) {
  if (!svg || window.matchMedia('(prefers-reduced-motion: reduce)').matches ||
      document.documentElement.classList.contains('motion-paused') || !svg.animate) return;
  svg.getAnimations({subtree:true}).forEach(animation => animation.cancel());
  const path = svg.querySelector('path');
  const run = (element, frames, duration, easing = 'ease-in-out') => {
    if (!element) return;
    const animation = element.animate(frames, {duration, easing});
    runningIconAnimations.add(animation);
    const finish = () => runningIconAnimations.delete(animation);
    animation.addEventListener('finish', finish, {once:true});
    animation.addEventListener('cancel', finish, {once:true});
  };
  switch (svg.dataset.icon) {
    case 'arrow':
      run(path, [{transform:'translateX(0)'},{transform:'translateX(-3px)'},{transform:'translateX(0)'}], 400);
      run(svg.children[1], [{transform:'scaleX(1)'},{transform:'scaleX(.83333333)'},{transform:'scaleX(1)'}], 400);
      break;
    case 'external':
      run(path, [{transform:'translate(0,0) scale(1)'},{transform:'translate(-4px,4px) scale(.85)'},{transform:'translate(0,0) scale(1)'}], 500);
      break;
    case 'copy':
      run(path, [{transform:'translateY(0)'},{transform:'translateY(-1px)'},{transform:'translateY(0)'}], 300);
      [...svg.children].slice(1).forEach(item => run(item,[{transform:'translate(-4px,4px)',opacity:0},{transform:'translate(0,0)',opacity:1}],300,'ease-out'));
      break;
    case 'check': {
      const length = path.getTotalLength();
      run(path,[{strokeDasharray:String(length),strokeDashoffset:String(length),opacity:0,transform:'scale(.5)'},{strokeDasharray:String(length),strokeDashoffset:'0',opacity:1,transform:'scale(1)'}],400);
      break;
    }
    case 'pause':
      [...svg.children].forEach((bar,index) => run(bar,[{transform:'translateY(0)',offset:0},{transform:`translateY(${index?2:0}px)`,offset:.2},{transform:`translateY(${index?0:2}px)`,offset:.5},{transform:'translateY(0)',offset:1}],500));
      break;
    case 'play':
      run(path,[{transform:'translateX(0) rotate(0)',offset:0},{transform:'translateX(-1px) rotate(-10deg)',offset:.2},{transform:'translateX(2px) rotate(0)',offset:.5},{transform:'translateX(0) rotate(0)',offset:1}],500);
      break;
    case 'plus':
      run(svg,[{transform:'rotate(0deg)'},{transform:'rotate(180deg)'}],450);
      break;
    case 'down':
      run(path,[{transform:'translateY(0)'},{transform:'translateY(-3px)'},{transform:'translateY(0)'}],400);
      run(svg.children[1],[{transform:'scaleY(1)'},{transform:'scaleY(.83333333)'},{transform:'scaleY(1)'}],400);
      break;
    case 'turn':
      run(svg,[{transform:'scaleX(1) translateX(0)'},{transform:'scaleX(1.15) translateX(2px)'},{transform:'scaleX(1) translateX(0)'}],450);
      break;
  }
}
export function initIcons() {
const controller = new AbortController();
document.querySelectorAll('a,button,summary').forEach(control => {
  const animate = () => control.querySelectorAll('svg[data-icon]').forEach(animateHeroicon);
  control.addEventListener('pointerenter', animate, {signal: controller.signal});
  control.addEventListener('focus', animate, {signal: controller.signal});
  control.addEventListener('pointerleave', () => control.querySelectorAll('svg[data-icon]').forEach(svg => svg.getAnimations({subtree:true}).forEach(animation => animation.cancel())), {signal: controller.signal});
});

return () => { controller.abort(); stopHeroicons(); };
}
