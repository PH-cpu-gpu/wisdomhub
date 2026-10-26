/* ============================================================
   WisdomHub landing — interactions
   ============================================================ */

const SEED = "LHXSKg2Lx6oY39gpAMv0OXoqTqqSM7ZpZifjg1SE5WuDsNYB7OkMLdaeQ3bB0tnM";
const SVG_NS = "http://www.w3.org/2000/svg";
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/* ── nav ─────────────────────────────────────────────────── */

const nav = document.getElementById("nav");
const onScroll = () => nav.classList.toggle("is-scrolled", window.scrollY > 16);
onScroll();
window.addEventListener("scroll", onScroll, { passive: true });

/* ── seed grid ───────────────────────────────────────────── */

const grid = document.getElementById("seedGrid");
const stage = document.getElementById("seedStage");
const edgeSvg = document.getElementById("seedEdges");

const count = new Map();
for (const ch of SEED) count.set(ch, (count.get(ch) || 0) + 1);

const cells = [...SEED].map((ch, i) => {
  const cell = document.createElement("span");
  cell.className = "cell";
  if (/[0-9]/.test(ch)) cell.classList.add("cell--digit");
  else if (/[A-Z]/.test(ch)) cell.classList.add("cell--upper");
  else cell.classList.add("cell--lower");
  if ((count.get(ch) || 0) >= 3) cell.classList.add("cell--repeat");
  if (ch === "M") cell.classList.add("cell--m");
  cell.dataset.char = ch;
  cell.dataset.index = String(i);
  cell.textContent = ch;
  cell.title = `${ch} · cell ${i + 1}`;
  return cell;
});
grid.append(...cells);

function centerOf(cell) {
  return {
    x: cell.offsetLeft + cell.offsetWidth / 2,
    y: cell.offsetTop + cell.offsetHeight / 2,
  };
}

function drawSeedEdges() {
  const w = stage.clientWidth;
  const h = stage.clientHeight;
  if (!w || !h) return;

  edgeSvg.setAttribute("viewBox", `0 0 ${w} ${h}`);
  edgeSvg.replaceChildren();

  for (const [ch, n] of count) {
    if (n < 3) continue;
    const peers = cells.filter((c) => c.dataset.char === ch);
    const color = ch === "M" ? "#ff3b1f" : "#3b1dff";

    for (let p = 0; p < peers.length - 1; p++) {
      const a = centerOf(peers[p]);
      const b = centerOf(peers[p + 1]);
      const dx = b.x - a.x;
      const dy = b.y - a.y;
      const len = Math.hypot(dx, dy) || 1;
      const sign = p % 2 === 0 ? 1 : -1;
      const bend = Math.min(26, 9 + len * 0.04) * sign;
      const cx = (a.x + b.x) / 2 - (dy / len) * bend;
      const cy = (a.y + b.y) / 2 + (dx / len) * bend;

      const path = document.createElementNS(SVG_NS, "path");
      path.setAttribute(
        "d",
        `M ${a.x.toFixed(1)} ${a.y.toFixed(1)} Q ${cx.toFixed(1)} ${cy.toFixed(1)} ${b.x.toFixed(1)} ${b.y.toFixed(1)}`
      );
      path.setAttribute("stroke", color);
      path.setAttribute("stroke-width", ch === "M" ? "1.8" : "1.3");
      if (ch !== "M") path.setAttribute("stroke-dasharray", "4 5");
      path.setAttribute("class", "edge");
      path.dataset.char = ch;
      edgeSvg.append(path);
    }
  }
}

function setSeedFocus(char) {
  grid.classList.add("has-hover");
  edgeSvg.classList.add("has-hover");
  for (const cell of cells) {
    cell.classList.toggle("is-peer", cell.dataset.char === char);
  }
  for (const path of edgeSvg.querySelectorAll(".edge")) {
    path.classList.toggle("is-peer", path.dataset.char === char);
  }
}

function clearSeedFocus() {
  grid.classList.remove("has-hover");
  edgeSvg.classList.remove("has-hover");
  for (const cell of cells) cell.classList.remove("is-peer");
  for (const path of edgeSvg.querySelectorAll(".edge")) {
    path.classList.remove("is-peer");
  }
}

for (const cell of cells) {
  cell.addEventListener("pointerenter", () => setSeedFocus(cell.dataset.char));
}
grid.addEventListener("pointerleave", clearSeedFocus);

requestAnimationFrame(drawSeedEdges);
if (document.fonts && document.fonts.ready) {
  document.fonts.ready.then(() => requestAnimationFrame(drawSeedEdges));
}

let resizeFrame = 0;
new ResizeObserver(() => {
  cancelAnimationFrame(resizeFrame);
  resizeFrame = requestAnimationFrame(drawSeedEdges);
}).observe(stage);

/* ── knowledge graph ─────────────────────────────────────── */

const kg = document.getElementById("kg");
const kgNodes = [...kg.querySelectorAll(".gnode")];
const kgEdges = [...kg.querySelectorAll(".gedge")];
const kgLabels = [...kg.querySelectorAll(".gedge-label")];

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
      linked.add(edge.dataset.a);
      linked.add(edge.dataset.b);
    }
  }
  for (const label of kgLabels) {
    label.classList.toggle(
      "is-active",
      label.dataset.a === id || label.dataset.b === id
    );
  }
  for (const node of kgNodes) {
    node.classList.toggle("is-active", linked.has(node.dataset.id));
  }
}

function kgClear() {
  kg.classList.remove("has-focus");
  for (const el of [...kgNodes, ...kgEdges, ...kgLabels]) {
    el.classList.remove("is-active");
  }
}

for (const node of kgNodes) {
  const id = node.dataset.id;
  node.addEventListener("pointerenter", () => kgFocus(id));
  node.addEventListener("focus", () => kgFocus(id));
  node.addEventListener("pointerleave", kgClear);
  node.addEventListener("blur", kgClear);
}

/* ── reveal on scroll ────────────────────────────────────── */

const revealItems = document.querySelectorAll(".reveal");
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
const footText = termFoot.querySelector(".is-on");
let typingToken = 0;
let demoChosen = false;

function typeDemo(key) {
  const demo = DEMOS[key];
  if (!demo) return;

  const token = ++typingToken;
  termPrompt.textContent = demo.prompt;
  termOut.textContent = "";
  footText.textContent = "STREAMING ···";

  if (reduceMotion) {
    termOut.textContent = demo.text;
    footText.textContent = demo.foot;
    return;
  }

  let i = 0;
  const tick = () => {
    if (token !== typingToken) return;
    i = Math.min(i + 2, demo.text.length);
    termOut.textContent = demo.text.slice(0, i);
    if (i < demo.text.length) {
      window.setTimeout(tick, 12 + Math.random() * 16);
    } else {
      footText.textContent = demo.foot;
    }
  };
  window.setTimeout(tick, 220);
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
        if (!demoChosen) typeDemo("qa");
        demoObserver.disconnect();
      }
    },
    { threshold: 0.3 }
  );
  demoObserver.observe(demoRoot);
} else {
  typeDemo("qa");
}

/* ── placeholder CTA ─────────────────────────────────────── */

const toast = document.getElementById("toast");
let toastTimer = 0;

function showToast(message) {
  toast.textContent = message;
  toast.classList.add("is-on");
  window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => toast.classList.remove("is-on"), 3200);
}

for (const el of document.querySelectorAll("[data-soon]")) {
  el.addEventListener("click", (event) => {
    event.preventDefault();
    showToast("内测入口尚未开放 · 敬请期待");
  });
}

/* ── footer details marker ───────────────────────────────── */

const seedDetails = document.querySelector(".footer__seed");
if (seedDetails) {
  const sign = seedDetails.querySelector("summary span");
  seedDetails.addEventListener("toggle", () => {
    sign.textContent = seedDetails.open ? "−" : "＋";
  });
}
