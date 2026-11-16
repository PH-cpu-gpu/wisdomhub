/* ============================================================
   WisdomHub landing — interactions
   ============================================================ */

const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/* ── nav ─────────────────────────────────────────────────── */

const nav = document.getElementById("nav");
const onScroll = () => nav.classList.toggle("is-scrolled", window.scrollY > 16);
onScroll();
window.addEventListener("scroll", onScroll, { passive: true });

/* ── Scroll progress bar ───────────────────────────────── */

(function initScrollProgress() {
  const progressBar = document.querySelector(".scroll-progress");
  if (!progressBar) return;
  
  function updateProgress() {
    const scrollTop = window.scrollY;
    const docHeight = document.documentElement.scrollHeight - window.innerHeight;
    const progress = docHeight > 0 ? (scrollTop / docHeight) * 100 : 0;
    progressBar.style.width = `${progress}%`;
  }
  
  window.addEventListener("scroll", updateProgress, { passive: true });
  updateProgress();
})();

/* ── deterministic frames for screenshot QA (?still=1) ───── */

if (new URLSearchParams(location.search).has("still")) {
  document.documentElement.classList.add("is-still");
}

/* ── Hero registration crosshair ───────────────────────── */

(function initHeroRegister() {
  const hero = document.querySelector(".hero");
  const stage = hero?.querySelector(".hero__stage");
  const cross = hero?.querySelector(".hero__cross");
  const readout = hero?.querySelector("[data-hero-readout]");
  if (!hero || !stage || !cross || !readout) return;
  if (reduceMotion) return;
  if (!window.matchMedia("(hover: hover) and (pointer: fine)").matches) return;

  hero.addEventListener("pointermove", (e) => {
    const hr = hero.getBoundingClientRect();
    const cr = cross.getBoundingClientRect();
    if (!cr.width) return;
    if (e.clientX < cr.left) {
      hero.classList.remove("is-pointer");
      return;
    }
    const nx = (e.clientX - hr.left) / hr.width;
    const ny = (e.clientY - hr.top) / hr.height;
    hero.classList.add("is-pointer");
    hero.style.setProperty("--cx", `${(e.clientX - cr.left).toFixed(1)}px`);
    hero.style.setProperty("--cy", `${(e.clientY - cr.top).toFixed(1)}px`);
    const tx = (nx - 0.5) * -10;
    const ty = (ny - 0.5) * -8;
    stage.style.transform = `translateY(-50%) translate(${tx.toFixed(1)}px, ${ty.toFixed(1)}px)`;
    const x = String(Math.round(nx * 1440)).padStart(4, "0");
    const y = String(Math.round(ny * 900)).padStart(4, "0");
    readout.textContent = `REGISTER · X ${x} Y ${y}`;
  });

  hero.addEventListener("pointerleave", () => hero.classList.remove("is-pointer"));

  window.addEventListener("resize", () => {
    if (window.innerWidth > 900) return;
    hero.classList.remove("is-pointer");
    stage.style.transform = "";
  }, { passive: true });
})();

/* ── Custom cursor ─────────────────────────────────────── */

(function initCustomCursor() {
  // Only enable on desktop with fine pointer
  if (!window.matchMedia("(hover: hover) and (pointer: fine)").matches) return;
  
  const cursor = document.querySelector(".custom-cursor");
  const cursorDot = document.querySelector(".custom-cursor-dot");
  const cursorGlow = document.querySelector(".cursor-glow");
  if (!cursor || !cursorDot) return;
  
  let mouseX = 0, mouseY = 0;
  let cursorX = 0, cursorY = 0;
  let dotX = 0, dotY = 0;
  let glowX = 0, glowY = 0;
  let isVisible = false;
  let trailThrottle = 0;
  
  // Smooth cursor follow
  function animateCursor() {
    // Outer ring - slower follow
    cursorX += (mouseX - cursorX) * 0.12;
    cursorY += (mouseY - cursorY) * 0.12;
    cursor.style.left = `${cursorX}px`;
    cursor.style.top = `${cursorY}px`;
    
    // Inner dot - faster follow
    dotX += (mouseX - dotX) * 0.25;
    dotY += (mouseY - dotY) * 0.25;
    cursorDot.style.left = `${dotX}px`;
    cursorDot.style.top = `${dotY}px`;
    
    // Glow - slowest follow
    if (cursorGlow) {
      glowX += (mouseX - glowX) * 0.08;
      glowY += (mouseY - glowY) * 0.08;
      cursorGlow.style.left = `${glowX}px`;
      cursorGlow.style.top = `${glowY}px`;
    }
    
    requestAnimationFrame(animateCursor);
  }
  
  // Mouse move handler
  document.addEventListener("mousemove", (e) => {
    mouseX = e.clientX;
    mouseY = e.clientY;
    
    if (!isVisible) {
      isVisible = true;
      cursor.classList.add("is-visible");
      cursorDot.classList.add("is-visible");
      if (cursorGlow) cursorGlow.classList.add("is-visible");
    }
    
    // Create trail particles (throttled)
    trailThrottle++;
    if (trailThrottle % 3 === 0) {
      createTrail(e.clientX, e.clientY);
    }
  });
  
  // Mouse leave
  document.addEventListener("mouseleave", () => {
    isVisible = false;
    cursor.classList.remove("is-visible");
    cursorDot.classList.remove("is-visible");
    if (cursorGlow) cursorGlow.classList.remove("is-visible");
  });
  
  // Hover effects on interactive elements
  const hoverTargets = document.querySelectorAll("a, button, [role='button'], input, .fcard, .gnode, .cell");
  hoverTargets.forEach(target => {
    target.addEventListener("mouseenter", () => {
      cursor.classList.add("is-hovering");
      if (cursorGlow) cursorGlow.classList.add("is-hovering");
    });
    target.addEventListener("mouseleave", () => {
      cursor.classList.remove("is-hovering");
      if (cursorGlow) cursorGlow.classList.remove("is-hovering");
    });
  });
  
  // Click effect
  document.addEventListener("mousedown", () => {
    cursor.classList.add("is-clicking");
  });
  document.addEventListener("mouseup", () => {
    cursor.classList.remove("is-clicking");
  });
  
  // Create trail particle
  function createTrail(x, y) {
    const trail = document.createElement("div");
    trail.className = "cursor-trail";
    trail.style.left = `${x}px`;
    trail.style.top = `${y}px`;
    document.body.appendChild(trail);
    
    setTimeout(() => trail.remove(), 500);
  }
  
  // Start animation loop
  animateCursor();
})();

/* ── Hero title character animation ────────────────────── */

(function initHeroTitleAnimation() {
  const title = document.querySelector("[data-split-text]");
  if (!title) return;
  
  // Skip animation if user prefers reduced motion
  if (reduceMotion) return;
  
  const lines = title.querySelectorAll(".line");
  let globalCharIndex = 0;
  
  lines.forEach((line, lineIndex) => {
    // Get all text nodes and wrap each character
    function wrapTextNodes(element) {
      const childNodes = Array.from(element.childNodes);
      
      childNodes.forEach(node => {
        if (node.nodeType === Node.TEXT_NODE) {
          const text = node.textContent;
          if (text.trim() === '') return;
          
          const fragment = document.createDocumentFragment();
          [...text].forEach(char => {
            if (char === ' ') {
              fragment.appendChild(document.createTextNode(' '));
            } else {
              const span = document.createElement('span');
              span.className = 'char';
              span.style.setProperty('--char-index', globalCharIndex);
              span.textContent = char;
              fragment.appendChild(span);
            }
            globalCharIndex++;
          });
          
          element.replaceChild(fragment, node);
        } else if (node.nodeType === Node.ELEMENT_NODE) {
          wrapTextNodes(node);
        }
      });
    }
    
    wrapTextNodes(line);
  });
})();

/* ── mobile menu ─────────────────────────────────────────── */

const menu = document.getElementById("mobileMenu");
const menuButton = document.querySelector(".nav__menu");
const menuClose = document.querySelector(".menu__close");

function closeMenu() {
  if (!menu || !menu.open) return;
  if (typeof menu.close === "function") menu.close();
  else menu.removeAttribute("open");
  document.body.classList.remove("menu-open");
  menuButton?.setAttribute("aria-expanded", "false");
}

if (menu && menuButton) {
  menuButton.addEventListener("click", () => {
    if (menu.open) {
      closeMenu();
      return;
    }
    if (typeof menu.showModal === "function") menu.showModal();
    else menu.setAttribute("open", "");
    menuButton.setAttribute("aria-expanded", "true");
    document.body.classList.add("menu-open");
    menuClose?.focus({ preventScroll: true });
  });

  menuClose?.addEventListener("click", closeMenu);
  menu.addEventListener("close", () => {
    document.body.classList.remove("menu-open");
    menuButton.setAttribute("aria-expanded", "false");
  });
  menu.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", closeMenu);
  });
  window.addEventListener("resize", () => {
    if (window.innerWidth > 900) closeMenu();
  }, { passive: true });
}

/* ── knowledge graph ─────────────────────────────────────── */

const kg = document.getElementById("kg");
const kgNodes = [...kg.querySelectorAll(".gnode")];
const kgEdges = [...kg.querySelectorAll(".gedge")];
const kgLabels = [...kg.querySelectorAll(".gedge-label")];
const kgTooltip = document.getElementById("gnodeTooltip");

// Node metadata for tooltips
const nodeMetadata = {
  article: { title: "Redis 为什么使用跳表？", meta: "ARTICLE · 12 connections · 8 min read" },
  boot: { title: "Spring Boot", meta: "HUB · BASED_ON Spring · USES MyBatis, Redis" },
  spring: { title: "Spring", meta: "CORE · 8 relations · Prerequisite for Boot" },
  mybatis: { title: "MyBatis", meta: "ORM · USES MySQL · 5 relations" },
  mysql: { title: "MySQL", meta: "DATABASE · 6 relations · Storage engine" },
  redis: { title: "Redis", meta: "CACHE · HOT NODE · 11 connections" },
  cache: { title: "缓存", meta: "CONCEPT · PREREQUISITE for Redis" },
  lock: { title: "分布式锁", meta: "PATTERN · PREREQUISITE for Redis" },
};

for (const node of kgNodes) {
  const label = node.querySelector("text:not(.gnode__kind)");
  node.setAttribute("role", "img");
  if (label) node.setAttribute("aria-label", label.textContent.trim());
}

function kgFocus(id) {
  kg.classList.add("has-focus");
  const linked = new Set([id]);

  for (const edge of kgEdges) {
    const hit = edge.dataset.a === id || edge.dataset.b === id;
    edge.classList.toggle("is-active", hit);
    if (hit) {
      edge.classList.add("gedge--animated");
      linked.add(edge.dataset.a);
      linked.add(edge.dataset.b);
    } else {
      edge.classList.remove("gedge--animated");
    }
  }
  for (const label of kgLabels) {
    label.classList.toggle(
      "is-active",
      label.dataset.a === id || label.dataset.b === id
    );
  }
  for (const node of kgNodes) {
    const isActive = linked.has(node.dataset.id);
    node.classList.toggle("is-active", isActive);
    if (isActive && node.dataset.id !== id) {
      node.classList.add("is-related");
    } else {
      node.classList.remove("is-related");
    }
  }
  
  showKgTooltip(id);
}

function kgClear() {
  kg.classList.remove("has-focus");
  for (const el of [...kgNodes, ...kgEdges, ...kgLabels]) {
    el.classList.remove("is-active");
  }
  for (const edge of kgEdges) {
    edge.classList.remove("gedge--animated");
  }
  for (const node of kgNodes) {
    node.classList.remove("is-related");
  }
  hideKgTooltip();
}

function showKgTooltip(nodeId) {
  const node = kgNodes.find(n => n.dataset.id === nodeId);
  const meta = nodeMetadata[nodeId];
  if (!node || !meta || !kgTooltip) return;
  
  const tooltipTitle = kgTooltip.querySelector(".gnode-tooltip__title");
  const tooltipMeta = kgTooltip.querySelector(".gnode-tooltip__meta");
  
  tooltipTitle.textContent = meta.title;
  tooltipMeta.textContent = meta.meta;
  
  const nodeRect = node.getBoundingClientRect();
  const graphStage = document.querySelector(".graph__stage");
  if (!graphStage) return;
  const stageRect = graphStage.getBoundingClientRect();
  
  const left = nodeRect.left - stageRect.left + nodeRect.width / 2;
  const top = nodeRect.bottom - stageRect.top + 12;
  
  kgTooltip.style.left = `${left}px`;
  kgTooltip.style.top = `${top}px`;
  kgTooltip.classList.add("is-visible");
}

function hideKgTooltip() {
  if (kgTooltip) {
    kgTooltip.classList.remove("is-visible");
  }
}

for (const node of kgNodes) {
  const id = node.dataset.id;
  node.addEventListener("pointerenter", () => kgFocus(id));
  node.addEventListener("focus", () => kgFocus(id));
  node.addEventListener("pointerleave", kgClear);
  node.addEventListener("blur", kgClear);
  
  node.addEventListener("click", () => {
    node.classList.add("is-expanding");
    setTimeout(() => node.classList.remove("is-expanding"), 500);
  });
}

/* ── reveal on scroll ────────────────────────────────────── */

const revealItems = document.querySelectorAll(".reveal, .reveal-scale, .reveal-left, .reveal-right");
if ("IntersectionObserver" in window && !reduceMotion) {
  const io = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-in");
          io.unobserve(entry.target);
        }
      }
    },
    { rootMargin: "0px 0px -8% 0px", threshold: 0.08 }
  );
  revealItems.forEach((el) => io.observe(el));
} else {
  revealItems.forEach((el) => el.classList.add("is-in"));
}

window.setTimeout(() => {
  document.querySelectorAll(".reveal:not(.is-in), .reveal-scale:not(.is-in), .reveal-left:not(.is-in), .reveal-right:not(.is-in)").forEach((el) => {
    el.classList.add("is-in");
  });
}, 2000);

/* ── AI demo ─────────────────────────────────────────────── */

const DEMOS = {
  qa: {
    prompt: "Redis 为什么使用跳表？",
    text:
      "跳表用多层索引把有序链表的查找从 O(n) 降到期望 O(log n)。\n\n" +
      "相比红黑树，它实现更简单、范围查询更自然；插入时只需要改指针，不需要旋转和再平衡。Redis 的有序集合要频繁做范围操作，这就是它选择跳表的原因。\n\n" +
      "代价是额外的指针内存，但换来了更简单、更可预测的实现。",
    foot: "参考 3 篇文章 · 2 个问答",
  },
  path: {
    prompt: "我想系统掌握 Spring 生态。",
    text:
      "目标：Spring 生态 · 预计 6 周\n\n" +
      "第 1 阶段 · 地基（1 周）\n" +
      "  Java 基础 → Maven → JUnit\n\n" +
      "第 2 阶段 · 核心（2 周）\n" +
      "  Spring Core → Spring MVC → Spring Boot\n\n" +
      "第 3 阶段 · 数据（4 天）\n" +
      "  MyBatis → MySQL → Spring Cache\n\n" +
      "第 4 阶段 · 分布式（2 周）\n" +
      "  Redis → 消息队列 → Spring Cloud\n\n" +
      "每个阶段附参考文章与练习建议。",
    foot: "沿 PREREQUISITE 前置链生成 · 6 周",
  },
  analyze: {
    prompt: "分析这篇文章：MySQL MVCC 是怎么实现的",
    text:
      "文章：MySQL MVCC 是怎么实现的\n\n" +
      "主题：数据库事务与并发控制\n" +
      "技术实体：MySQL / InnoDB / MVCC / undo log / Read View\n" +
      "知识点：快照读 · 当前读 · 版本链 · 可重复读\n" +
      "建议关联：隔离级别 · 间隙锁 · binlog\n\n" +
      "→ 分析结果已写入，文章连接到 3 个技术节点。",
    foot: "实体解析完成 · ABOUT 边已更新",
  },
};

const tabs = [...document.querySelectorAll(".term-tab")];
const termPrompt = document.getElementById("termPrompt");
const termOut = document.getElementById("termOut");
const termFoot = document.getElementById("termFoot");
const termThinking = document.getElementById("termThinking");
const termElement = document.querySelector(".term");
const footText = termFoot?.querySelector(".is-on");
let typingToken = 0;
let demoChosen = false;
let demoStarted = false;

// Simple syntax highlighting for code-like content
function highlightSyntax(text) {
  return text
    // Highlight code blocks (text between backticks)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    // Highlight arrows and special chars
    .replace(/(→)/g, '<span style="color: var(--acid)">$1</span>')
    // Highlight numbers like O(log n)
    .replace(/(O\([^)]+\))/g, '<span style="color: var(--verm)">$1</span>');
}

function showThinking() {
  if (termThinking) {
    termThinking.classList.add("is-active");
  }
  if (termElement) {
    termElement.classList.add("is-active");
  }
}

function hideThinking() {
  if (termThinking) {
    termThinking.classList.remove("is-active");
  }
}

function typeDemo(key) {
  const demo = DEMOS[key];
  if (!demo) return;

  demoStarted = true;
  const token = ++typingToken;
  
  // Show prompt with typing effect
  if (termPrompt) {
    termPrompt.textContent = "";
    const promptLine = termPrompt.parentElement;
    promptLine?.classList.add("is-typing");
    
    let promptIndex = 0;
    const typePrompt = () => {
      if (token !== typingToken) return;
      if (promptIndex < demo.prompt.length) {
        termPrompt.textContent += demo.prompt[promptIndex];
        promptIndex++;
        setTimeout(typePrompt, 30 + Math.random() * 20);
      } else {
        promptLine?.classList.remove("is-typing");
        // Start thinking phase
        setTimeout(() => {
          if (token !== typingToken) return;
          showThinking();
          setTimeout(() => {
            if (token !== typingToken) return;
            hideThinking();
            startStreaming();
          }, 800 + Math.random() * 400);
        }, 300);
      }
    };
    typePrompt();
  }
  
  function startStreaming() {
    if (termOut) {
      termOut.textContent = "";
      termOut.classList.add("is-streaming");
      termOut.classList.remove("has-response");
    }
    if (footText) {
      footText.textContent = "STREAMING ···";
    }

    if (reduceMotion) {
      if (termOut) {
        termOut.innerHTML = highlightSyntax(demo.text);
        termOut.classList.remove("is-streaming");
      }
      if (footText) footText.textContent = demo.foot;
      return;
    }

    let i = 0;
    const tick = () => {
      if (token !== typingToken) return;
      i = Math.min(i + 2, demo.text.length);
      if (termOut) {
        termOut.innerHTML = highlightSyntax(demo.text.slice(0, i));
      }
      if (i < demo.text.length) {
        window.setTimeout(tick, 10 + Math.random() * 14);
      } else {
        if (termOut) {
          termOut.classList.remove("is-streaming");
          termOut.classList.add("has-response");
        }
        if (footText) footText.textContent = demo.foot;
        if (termElement) {
          setTimeout(() => termElement.classList.remove("is-active"), 1000);
        }
      }
    };
    window.setTimeout(tick, 100);
  }
}

function activateTab(key) {
  demoChosen = true;
  for (const tab of tabs) {
    const on = tab.dataset.demo === key;
    tab.classList.toggle("is-active", on);
    tab.setAttribute("aria-pressed", String(on));
  }
  typeDemo(key);
}

for (const tab of tabs) {
  tab.addEventListener("click", () => activateTab(tab.dataset.demo));
}

const demoRoot = document.querySelector(".ai__demo");
if ("IntersectionObserver" in window) {
  const demoObserver = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) {
        if (!demoChosen && !demoStarted) typeDemo("qa");
        demoObserver.disconnect();
      }
    },
    { threshold: 0.3 }
  );
  demoObserver.observe(demoRoot);
} else {
  typeDemo("qa");
}

window.setTimeout(() => {
  if (!demoStarted) typeDemo("qa");
}, 2200);

/* ── 3D Tilt effect for feature cards ────────────────────── */

(function initTiltCards() {
  const cards = document.querySelectorAll(".fcard");
  const MAX_TILT = 8;
  
  cards.forEach((card) => {
    card.addEventListener("mousemove", (e) => {
      const rect = card.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      const centerX = rect.width / 2;
      const centerY = rect.height / 2;
      
      const rotateX = ((y - centerY) / centerY) * -MAX_TILT;
      const rotateY = ((x - centerX) / centerX) * MAX_TILT;
      
      card.style.transform = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateY(-8px)`;
      
      // Update CSS variables for glow effect
      card.style.setProperty("--mouse-x", `${(x / rect.width) * 100}%`);
      card.style.setProperty("--mouse-y", `${(y / rect.height) * 100}%`);
    });
    
    card.addEventListener("mouseleave", () => {
      card.style.transform = "";
    });
  });
})();

/* ── Stagger reveal for lists ────────────────────────────── */

(function initStaggerReveal() {
  const lists = document.querySelectorAll(".problem-list, .feature-grid");
  
  if ("IntersectionObserver" in window && !reduceMotion) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.2 }
    );
    
    lists.forEach((list) => observer.observe(list));
  } else {
    lists.forEach((list) => list.classList.add("is-visible"));
  }
})();

/* ── Feature card internal animations ──────────────────── */

(function initFeatureCardAnimations() {
  const cards = document.querySelectorAll(".fcard");
  
  if ("IntersectionObserver" in window && !reduceMotion) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            // Don't unobserve - keep watching for re-entry if needed
          }
        });
      },
      { threshold: 0.3, rootMargin: "0px 0px -10% 0px" }
    );
    
    cards.forEach((card) => observer.observe(card));
  } else {
    cards.forEach((card) => card.classList.add("is-visible"));
  }
})();

/* ── Section reveal animation ────────────────────────────── */

(function initSectionReveal() {
  const sections = document.querySelectorAll(".section");
  
  if ("IntersectionObserver" in window && !reduceMotion) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
          }
        });
      },
      { threshold: 0.1, rootMargin: "0px 0px -10% 0px" }
    );
    
    sections.forEach((section) => observer.observe(section));
  } else {
    sections.forEach((section) => section.classList.add("is-visible"));
  }
})();

/* ── CTA section reveal ──────────────────────────────────── */

(function initCtaReveal() {
  const cta = document.querySelector(".cta");
  if (!cta) return;
  
  if ("IntersectionObserver" in window && !reduceMotion) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            cta.classList.add("is-visible");
            observer.unobserve(cta);
          }
        });
      },
      { threshold: 0.3 }
    );
    
    observer.observe(cta);
  } else {
    cta.classList.add("is-visible");
  }
})();

/* ── Button ripple effect ────────────────────────────────── */

(function initButtonRipple() {
  const buttons = document.querySelectorAll(".btn--ink, .btn--primary");
  
  buttons.forEach(btn => {
    btn.addEventListener("mousemove", (e) => {
      const rect = btn.getBoundingClientRect();
      const x = ((e.clientX - rect.left) / rect.width) * 100;
      const y = ((e.clientY - rect.top) / rect.height) * 100;
      btn.style.setProperty("--ripple-x", `${x}%`);
      btn.style.setProperty("--ripple-y", `${y}%`);
    });
  });
})();

/* ── Parallax scrolling ───────────────────────────────────── */

(function initParallax() {
  if (reduceMotion) return;
  
  const parallaxElements = document.querySelectorAll("[data-parallax]");
  if (!parallaxElements.length) return;
  
  let ticking = false;
  
  function updateParallax() {
    const scrollY = window.scrollY;
    
    parallaxElements.forEach(el => {
      const speed = parseFloat(el.dataset.parallax) || 0.5;
      const yOffset = scrollY * speed;
      el.style.transform = `translate3d(0, ${yOffset}px, 0)`;
    });
    
    ticking = false;
  }
  
  function onScroll() {
    if (!ticking) {
      requestAnimationFrame(updateParallax);
      ticking = true;
    }
  }
  
  window.addEventListener("scroll", onScroll, { passive: true });
})();

/* ── Animated counter for stats ──────────────────────────── */

(function initCounters() {
  const counters = document.querySelectorAll(".graph__stats b");
  
  function animateCounter(el) {
    const target = parseInt(el.textContent, 10);
    if (isNaN(target)) return;
    
    const duration = 1500;
    const startTime = performance.now();
    const startValue = 0;
    
    function update(currentTime) {
      const elapsed = currentTime - startTime;
      const progress = Math.min(elapsed / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      const current = Math.round(startValue + (target - startValue) * eased);
      
      el.textContent = current;
      
      if (progress < 1) {
        requestAnimationFrame(update);
      }
    }
    
    requestAnimationFrame(update);
  }
  
  if ("IntersectionObserver" in window) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const counters = entry.target.querySelectorAll(".graph__stats b");
            counters.forEach(animateCounter);
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.5 }
    );
    
    const statsSection = document.querySelector(".graph__stats");
    if (statsSection) observer.observe(statsSection);
  }
})();
