/* Scroll-scrubbed "press run" — maps page progress to video time. */
(() => {
  const section = document.querySelector("[data-press]");
  if (!section) return;

  const stage = section.querySelector("[data-press-stage]");
  const video = section.querySelector("[data-press-video]");
  const media = section.querySelector("[data-press-media]");
  const slabs = [...section.querySelectorAll("[data-press-slab]")];
  const plates = [...section.querySelectorAll("[data-press-plate]")];
  const meter = section.querySelector("[data-press-meter]");
  const num = section.querySelector("[data-press-num]");

  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)");
  const coarse = window.matchMedia("(pointer: coarse)");
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

  let progress = 0;
  let target = 0;
  let raf = 0;
  let ready = false;
  let duration = 0;
  let loaded = false;
  let inView = false;

  video.pause();

  const loadVideo = () => {
    if (loaded || reduce.matches) return;
    const src = video.dataset.pressSrc;
    if (!src) return;
    loaded = true;
    video.src = src;
    video.preload = "auto";
    video.load();
  };

  video.addEventListener("loadeddata", () => {
    duration = video.duration || 0;
    if (!duration) return;
    ready = true;
    stage.classList.add("is-video");
    video.pause();
    video.currentTime = 0;
  });

  video.addEventListener("error", () => {
    stage.classList.remove("is-video");
  });

  const measure = () => {
    const total = section.offsetHeight - stage.offsetHeight;
    if (total <= 0) {
      target = 0;
      return;
    }
    const top = section.getBoundingClientRect().top;
    target = clamp(-top / total, 0, 1);
  };

  const paint = (p) => {
    if (meter) meter.style.transform = `scaleX(${p})`;
    if (num) num.textContent = String(Math.round(p * 100)).padStart(2, "0");
    if (stage) {
      const t = clamp((p - 0.18) / 0.64, 0, 1);
      stage.style.setProperty("--press-end", (t * t * (3 - 2 * t)).toFixed(4));
    }
    const active = String(p < 0.5 ? 0 : 1);
    slabs.forEach((el) => el.classList.toggle("is-on", el.dataset.pressSlab === active));
    plates.forEach((el) => el.classList.toggle("is-on", el.dataset.pressPlate === active));
  };

  const seek = (p) => {
    if (!ready || !duration) return;
    const t = p * Math.max(duration - 0.06, 0);
    if (Math.abs(video.currentTime - t) > 0.016) video.currentTime = t;
  };

  const tick = () => {
    raf = 0;
    if (!inView) return;
    measure();
    progress += (target - progress) * 0.22;
    if (Math.abs(target - progress) < 0.0008) progress = target;
    seek(progress);
    paint(progress);
    if (progress !== target) raf = requestAnimationFrame(tick);
  };

  const request = () => {
    if (!raf) raf = requestAnimationFrame(tick);
  };

  if ("IntersectionObserver" in window) {
    const io = new IntersectionObserver(
      (entries) => {
        inView = entries.some((entry) => entry.isIntersecting);
        if (inView) {
          loadVideo();
          request();
        }
      },
      { rootMargin: "700px 0px" }
    );
    io.observe(section);
  } else {
    inView = true;
    loadVideo();
  }

  window.addEventListener("scroll", request, { passive: true });
  window.addEventListener("resize", () => {
    measure();
    progress = target;
    paint(progress);
    seek(progress);
  });

  if (!coarse.matches && !reduce.matches) {
    stage.addEventListener("pointermove", (event) => {
      const rect = stage.getBoundingClientRect();
      const mx = (event.clientX - rect.left) / rect.width - 0.5;
      const my = (event.clientY - rect.top) / rect.height - 0.5;
      media.style.setProperty("--press-mx", mx.toFixed(3));
      media.style.setProperty("--press-my", my.toFixed(3));
    });
    stage.addEventListener("pointerleave", () => {
      media.style.setProperty("--press-mx", "0");
      media.style.setProperty("--press-my", "0");
    });
  }

  measure();
  progress = target;
  paint(progress);

  if (reduce.matches) {
    slabs.forEach((el) => el.classList.add("is-on"));
  }

  window.__press = {
    get progress() { return progress; },
    get target() { return target; },
    get ready() { return ready; },
    get duration() { return duration; },
    video,
    seek(p) {
      progress = target = clamp(p, 0, 1);
      seek(progress);
      paint(progress);
    },
  };
})();
