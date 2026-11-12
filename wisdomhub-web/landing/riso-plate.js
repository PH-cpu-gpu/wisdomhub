/* ============================================================
   WisdomHub landing — RISO MISREG
   Generated plate + risograph misregistration shader + paper tilt.
   Falls back to the static <img> whenever WebGL2 is unavailable.
   ============================================================ */

(function () {
  "use strict";

  const plate = document.querySelector("[data-riso-plate]");
  if (!plate) return;

  const img = plate.querySelector(".hero__plate-img");
  const canvas = plate.querySelector("[data-riso-canvas]");
  if (!img || !canvas) return;

  const params = new URLSearchParams(window.location.search);
  const still = params.has("still");
  const stillTime = Number.parseFloat(params.get("t") || "0") || 0;
  const shaderOff = params.get("riso") === "off";

  // Deterministic mode for screenshot review: settle every entry animation.
  if (still) document.documentElement.classList.add("is-still");

  const reduceQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
  const narrowQuery = window.matchMedia("(max-width: 900px)");
  const finePointer = window.matchMedia("(hover: hover) and (pointer: fine)");

  const pointer = { tx: 0.5, ty: 0.5, x: 0.5, y: 0.5 };
  const startedAt = performance.now();

  /* ── gentle paper tilt ───────────────────────────────────── */

  (function initTilt() {
    if (!finePointer.matches || reduceQuery.matches || narrowQuery.matches) return;

    const hero = document.querySelector(".hero");
    if (!hero) return;

    let tx = 0;
    let ty = 0;
    let cx = 0;
    let cy = 0;
    let raf = 0;

    function apply() {
      plate.style.setProperty("--plate-rx", `${(-cy * 3.2).toFixed(2)}deg`);
      plate.style.setProperty("--plate-ry", `${(cx * 3.2).toFixed(2)}deg`);
      plate.style.setProperty("--plate-mx", `${(cx * 7).toFixed(2)}px`);
      plate.style.setProperty("--plate-my", `${(cy * 5).toFixed(2)}px`);
    }

    function tick() {
      cx += (tx - cx) * 0.1;
      cy += (ty - cy) * 0.1;
      apply();
      if (Math.abs(tx - cx) > 0.002 || Math.abs(ty - cy) > 0.002) {
        raf = requestAnimationFrame(tick);
      } else {
        cx = tx;
        cy = ty;
        apply();
        raf = 0;
      }
    }

    function nudge() {
      if (!raf) raf = requestAnimationFrame(tick);
    }

    hero.addEventListener(
      "pointermove",
      (event) => {
        const rect = plate.getBoundingClientRect();
        if (!rect.width || !rect.height) return;
        const nx = ((event.clientX - rect.left) / rect.width) * 2 - 1;
        const ny = ((event.clientY - rect.top) / rect.height) * 2 - 1;
        tx = Math.max(-1, Math.min(1, nx));
        ty = Math.max(-1, Math.min(1, ny));
        pointer.tx = (tx + 1) / 2;
        pointer.ty = (ty + 1) / 2;
        nudge();
      },
      { passive: true }
    );

    hero.addEventListener("pointerleave", () => {
      tx = 0;
      ty = 0;
      pointer.tx = 0.5;
      pointer.ty = 0.5;
      nudge();
    });
  })();

  /* ── WebGL2 misregistration ──────────────────────────────── */

  const VERT = `#version 300 es
in vec2 a_pos;
out vec2 v_uv;
void main() {
  v_uv = a_pos * 0.5 + 0.5;
  gl_Position = vec4(a_pos, 0.0, 1.0);
}`;

  const FRAG = `#version 300 es
precision highp float;

in vec2 v_uv;
out vec4 outColor;

uniform sampler2D u_tex;
uniform vec2 u_res;
uniform vec2 u_texRes;
uniform vec2 u_pointer;
uniform float u_time;
uniform float u_strength;
uniform float u_dpr;

float luma(vec3 c) {
  return dot(c, vec3(0.299, 0.587, 0.114));
}

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

vec2 coverUV(vec2 uv) {
  vec2 ratio = u_res / u_texRes;
  float scale = max(ratio.x, ratio.y);
  return (uv - 0.5) * ratio / scale + 0.5;
}

void main() {
  vec2 uv = coverUV(v_uv);

  float drift = u_time * 0.21;
  vec2 dir = normalize(vec2(cos(drift), sin(drift * 0.83)) + 0.0001);
  vec2 aim = (u_pointer - 0.5) * 2.0;
  vec2 offset = (dir * 0.4 + aim * 0.6) * u_strength * u_dpr / u_res;

  vec4 base = texture(u_tex, uv);
  vec4 acidGhost = texture(u_tex, uv + offset);
  vec4 vermGhost = texture(u_tex, uv - offset * 0.8);

  float i0 = 1.0 - luma(base.rgb);
  float i1 = 1.0 - luma(acidGhost.rgb);
  float i2 = 1.0 - luma(vermGhost.rgb);

  vec3 col = base.rgb;

  float edgeAcid = clamp((i1 - i0) * 0.85, 0.0, 1.0);
  float edgeVerm = clamp((i2 - i0) * 0.75, 0.0, 1.0);
  col = mix(col, vec3(0.62, 0.90, 0.10), edgeAcid * 0.34);
  col = mix(col, vec3(0.95, 0.16, 0.08), edgeVerm * 0.28);

  float ink = max(i0, max(i1, i2));
  float grain = hash(gl_FragCoord.xy * 0.85);
  col *= 1.0 - (0.032 - grain * 0.064) * ink;

  outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}`;

  let gl = null;
  let program = null;
  let buffer = null;
  let texture = null;
  let uniform = {};
  let texReady = false;
  let contextLost = false;
  let running = false;
  let raf = 0;
  let lastDraw = 0;
  let drawCount = 0;
  let resizeRaf = 0;
  let disposed = false;

  function compile(type, source) {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      console.warn("[riso] shader compile failed", gl.getShaderInfoLog(shader));
      gl.deleteShader(shader);
      return null;
    }
    return shader;
  }

  function buildProgram() {
    const vs = compile(gl.VERTEX_SHADER, VERT);
    const fs = compile(gl.FRAGMENT_SHADER, FRAG);
    if (!vs || !fs) return false;

    const prog = gl.createProgram();
    gl.attachShader(prog, vs);
    gl.attachShader(prog, fs);
    gl.linkProgram(prog);
    gl.deleteShader(vs);
    gl.deleteShader(fs);

    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
      console.warn("[riso] program link failed", gl.getProgramInfoLog(prog));
      gl.deleteProgram(prog);
      return false;
    }

    program = prog;
    gl.useProgram(program);

    buffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    const loc = gl.getAttribLocation(program, "a_pos");
    gl.enableVertexAttribArray(loc);
    gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);

    uniform = {
      tex: gl.getUniformLocation(program, "u_tex"),
      res: gl.getUniformLocation(program, "u_res"),
      texRes: gl.getUniformLocation(program, "u_texRes"),
      pointer: gl.getUniformLocation(program, "u_pointer"),
      time: gl.getUniformLocation(program, "u_time"),
      strength: gl.getUniformLocation(program, "u_strength"),
      dpr: gl.getUniformLocation(program, "u_dpr"),
    };

    gl.uniform1i(uniform.tex, 0);
    gl.uniform1f(uniform.strength, 2.2);
    return true;
  }

  function uploadTexture() {
    texture = gl.createTexture();
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, texture);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.generateMipmap(gl.TEXTURE_2D);
    gl.uniform2f(uniform.texRes, img.naturalWidth, img.naturalHeight);
    texReady = true;
  }

  function resize() {
    const rect = plate.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
    const width = Math.max(1, Math.round(rect.width * dpr));
    const height = Math.max(1, Math.round(rect.height * dpr));
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    if (gl) {
      gl.viewport(0, 0, width, height);
      gl.uniform2f(uniform.res, width, height);
      gl.uniform1f(uniform.dpr, dpr);
    }
    render();
  }

  function render() {
    if (!gl || !texReady || contextLost || disposed) return;

    const t = still ? stillTime : (performance.now() - startedAt) / 1000;
    const follow = still ? 1 : 0.07;
    pointer.x += (pointer.tx - pointer.x) * follow;
    pointer.y += (pointer.ty - pointer.y) * follow;

    gl.uniform1f(uniform.time, t);
    gl.uniform2f(uniform.pointer, pointer.x, pointer.y);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    drawCount++;

    if (!plate.classList.contains("is-live")) plate.classList.add("is-live");
  }

  function frame(now) {
    if (!running) return;
    if (now - lastDraw >= 33) {
      lastDraw = now;
      render();
    }
    raf = requestAnimationFrame(frame);
  }

  function start() {
    if (running || !texReady || reduceQuery.matches || still) return;
    running = true;
    lastDraw = 0;
    raf = requestAnimationFrame(frame);
  }

  function stop() {
    running = false;
    if (raf) cancelAnimationFrame(raf);
    raf = 0;
  }

  function init() {
    if (shaderOff || narrowQuery.matches || !window.WebGL2RenderingContext) return false;

    const context = canvas.getContext("webgl2", {
      alpha: false,
      antialias: false,
      depth: false,
      stencil: false,
      premultipliedAlpha: false,
      powerPreference: "low-power",
      preserveDrawingBuffer: false,
    });
    if (!context) return false;

    gl = context;
    contextLost = false;
    if (!buildProgram()) {
      gl = null;
      return false;
    }

    canvas.addEventListener("webglcontextlost", onContextLost, false);
    canvas.addEventListener("webglcontextrestored", onContextRestored, false);

    resize();
    if (!img.complete || !img.naturalWidth) {
      img.addEventListener(
        "load",
        () => {
          if (disposed || !gl) return;
          uploadTexture();
          resize();
          if (reduceQuery.matches || still) render();
          else start();
        },
        { once: true }
      );
    } else {
      uploadTexture();
      resize();
      if (reduceQuery.matches || still) render();
      else start();
    }
    return true;
  }

  function onContextLost(event) {
    event.preventDefault();
    contextLost = true;
    stop();
    plate.classList.remove("is-live");
  }

  function onContextRestored() {
    if (disposed) return;
    contextLost = false;
    TexCleanup();
    if (init()) {
      plate.classList.add("is-live");
    }
  }

  function TexCleanup() {
    texReady = false;
    texture = null;
    buffer = null;
    program = null;
    gl = null;
  }

  /* ── lifecycle ───────────────────────────────────────────── */

  const enabled = init();

  if (enabled) {
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries[0].isIntersecting && !document.hidden;
        if (visible) start();
        else stop();
      },
      { rootMargin: "120px 0px", threshold: 0.01 }
    );
    observer.observe(plate);

    document.addEventListener("visibilitychange", () => {
      if (document.hidden) stop();
      else if (!reduceQuery.matches && !still) start();
    });

    if (typeof ResizeObserver === "function") {
      new ResizeObserver(() => {
        cancelAnimationFrame(resizeRaf);
        resizeRaf = requestAnimationFrame(resize);
      }).observe(plate);
    }

    window.addEventListener("resize", () => {
      cancelAnimationFrame(resizeRaf);
      resizeRaf = requestAnimationFrame(resize);
    }, { passive: true });

    reduceQuery.addEventListener?.("change", (event) => {
      if (event.matches) {
        stop();
        pointer.x = 0.5;
        pointer.y = 0.5;
        render();
      } else {
        start();
      }
    });

    window.addEventListener("pagehide", () => {
      disposed = true;
      stop();
      observer.disconnect();
    });

    /* deterministic hooks for frame-by-frame QA */
    window.__risoPlate = {
      renderAt(t) {
        if (!gl || !texReady) return false;
        gl.uniform1f(uniform.time, t);
        gl.uniform2f(uniform.pointer, 0.5, 0.5);
        gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
        plate.classList.add("is-live");
        return true;
      },
      setPointer(x, y) {
        pointer.tx = Math.max(0, Math.min(1, x));
        pointer.ty = Math.max(0, Math.min(1, y));
        if (still) {
          pointer.x = pointer.tx;
          pointer.y = pointer.ty;
          render();
        }
      },
      sample(u, v) {
        if (!gl || !texReady) return null;
        render();
        const pixel = new Uint8Array(4);
        gl.readPixels(
          Math.min(canvas.width - 1, Math.max(0, Math.round(u * canvas.width))),
          Math.min(canvas.height - 1, Math.max(0, Math.round((1 - v) * canvas.height))),
          1,
          1,
          gl.RGBA,
          gl.UNSIGNED_BYTE,
          pixel
        );
        return Array.from(pixel);
      },
      stop,
      start,
      get live() {
        return Boolean(gl && texReady && !contextLost);
      },
      get frames() {
        return drawCount;
      },
      get running() {
        return running;
      },
    };
  }
})();
