/* float-cards.js — motor reutilizabil pentru carduri „vii" (fișe de studiu)
   Folosire:
   1. Pune orice grup de carduri într-un container cu clasa .floaty-zone
   2. Fiecare card individual are clasa .floaty, și în interior un .floaty-inner
      care ține conținutul vizual (background, padding, shadow etc.)
   3. Opțional: data-popup="text" pe .floaty → click deschide un pop-out cu textul
      Fără data-popup → click face doar un mic "nudge" (sări puțin în loc)
*/
(function () {
  const ZONE_SELECTOR = '.floaty-zone';
  const CARD_SELECTOR = '.floaty';
  const ATTRACT_RADIUS = 190;   // cât de aproape trebuie mouse-ul ca să atragă cardul
  const MAX_OFFSET = 18;        // cât de departe se poate deplasa un card față de poziția lui de bază
  const DAMPING = 0.93;         // frânare (mai aproape de 1 = plutire mai lentă/lină)

  function initZone(zone) {
    const cards = Array.from(zone.querySelectorAll(CARD_SELECTOR)).map((el) => ({
      el,
      x: 0, y: 0,
      vx: (Math.random() - 0.5) * 0.4,
      vy: (Math.random() - 0.5) * 0.4,
      wanderAngle: Math.random() * Math.PI * 2,
    }));

    let mouseX = null, mouseY = null, mouseActive = false;

    zone.addEventListener('mousemove', (e) => {
      const rect = zone.getBoundingClientRect();
      mouseX = e.clientX - rect.left;
      mouseY = e.clientY - rect.top;
      mouseActive = true;
    });
    zone.addEventListener('mouseleave', () => { mouseActive = false; });

    cards.forEach(({ el }) => {
      el.addEventListener('click', () => {
        const text = el.dataset.popup;
        if (text) togglePopup(el, text, zone);
        else nudge(el);
      });
    });

    function nudge(el) {
      const inner = el.querySelector('.floaty-inner') || el;
      inner.classList.remove('nudge');
      void inner.offsetWidth; // forțează reflow, ca animația să poată rula din nou
      inner.classList.add('nudge');
    }

    function togglePopup(el, text, zone) {
      const already = el.querySelector('.floaty-popup');
      if (already) { already.remove(); return; }
      zone.querySelectorAll('.floaty-popup').forEach((p) => p.remove());

      const bubble = document.createElement('div');
      bubble.className = 'floaty-popup';
      bubble.textContent = text;
      el.appendChild(bubble);
      requestAnimationFrame(() => bubble.classList.add('show'));
    }

    function frame() {
      const zoneRect = zone.getBoundingClientRect();

      cards.forEach((s) => {
        const rect = s.el.getBoundingClientRect();
        const cx = rect.left + rect.width / 2 - zoneRect.left;
        const cy = rect.top + rect.height / 2 - zoneRect.top;

        // rătăcire lentă, aleatorie, când mouse-ul nu e aproape
        s.wanderAngle += (Math.random() - 0.5) * 0.35;
        let ax = Math.cos(s.wanderAngle) * 0.012;
        let ay = Math.sin(s.wanderAngle) * 0.012;

        // atracție spre mouse, dacă e suficient de aproape
        if (mouseActive && mouseX !== null) {
          const dx = mouseX - cx, dy = mouseY - cy;
          const dist = Math.sqrt(dx * dx + dy * dy);
          if (dist < ATTRACT_RADIUS) {
            const force = (1 - dist / ATTRACT_RADIUS) * 0.07;
            ax += (dx / (dist || 1)) * force;
            ay += (dy / (dist || 1)) * force;
          }
        }

        s.vx = (s.vx + ax) * DAMPING;
        s.vy = (s.vy + ay) * DAMPING;
        s.x = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, s.x + s.vx));
        s.y = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, s.y + s.vy));

        const rotate = s.vx * 3.5;
        s.el.style.transform = `translate(${s.x.toFixed(2)}px, ${s.y.toFixed(2)}px) rotate(${rotate.toFixed(2)}deg)`;
      });

      requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  }

  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll(ZONE_SELECTOR).forEach(initZone);
  });
})();