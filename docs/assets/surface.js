// Sound surface: a low-resolution mesh deformed in the vertex shader (WebGL2, no libraries).
// One draw call, no textures, no post-processing. The canvas sits over a static poster of the same
// frame, so browsers without WebGL2, and people who ask for reduced motion, still see the picture.
(() => {
  const VERT = `#version 300 es
  in vec2 aGrid;
  uniform mat4 uVP;
  uniform float uT;
  uniform vec4 uShape;   // x: half width, y: depth, z: amplitude, w: loudest x
  uniform vec3 uPointer; // x, z on the surface plane and how strongly it is felt
  uniform float uFreq;   // hill frequency: higher on narrow screens, where less of the surface is visible
  out vec3 vN; out vec3 vW; out float vH; out float vEdge;

  // Broad, slow hills: a few low-frequency waves that drift and interfere, with the swell centred behind the phone.
  float height(vec2 p0, float t) {
    vec2 p = vec2(p0.x - uShape.w, p0.y) * uFreq + vec2(uShape.w, 0.0);
    float spread = uShape.x * 0.75;
    float bell = exp(-pow((p.x - uShape.w) / spread, 2.0));
    float env = 0.35 + 0.65 * bell;
    float a = sin(p.x * 0.42 + t * 0.32 + sin(p.y * 0.26 + t * 0.15) * 1.5);
    float b = sin(p.y * 0.5 - t * 0.28 + p.x * 0.16);
    float c = sin(p.x * 0.9 + p.y * 0.4 - t * 0.45) * 0.28;
    float wave = env * uShape.z * (a * 0.8 + b * 0.6 + c);
    float d = length(p0 - uPointer.xy);
    wave += uPointer.z * uShape.z * 0.16 * exp(-d * d * 0.045) * cos(d * 0.8 - t * 1.6);
    return wave;
  }

  void main() {
    float z = mix(4.4, -uShape.y, pow(aGrid.y, 1.1));
    vec2 p = vec2(aGrid.x * uShape.x, z);
    float h = height(p, uT);
    float e = 0.1;
    float hx = height(p + vec2(e, 0.0), uT);
    float hz = height(p + vec2(0.0, e), uT);
    vN = normalize(vec3(h - hx, e, h - hz));
    vW = vec3(p.x, h, p.y);
    vH = h / uShape.z;
    vEdge = (1.0 - smoothstep(0.55, 1.0, abs(aGrid.x))) * (1.0 - smoothstep(0.55, 1.0, aGrid.y));
    gl_Position = uVP * vec4(vW, 1.0);
  }`;

  const FRAG = `#version 300 es
  precision mediump float;
  in vec3 vN; in vec3 vW; in float vH; in float vEdge;
  uniform vec3 uCam;
  uniform vec2 uRes;
  uniform float uBias;   // lifts the shading on small screens, where little of the surface is on show
  uniform vec4 uClear;   // visible from x0 to x1 (left ramp), fades out from y0 to y1 (top ramp), in screen units
  out vec4 outColor;

  void main() {
    vec3 n = normalize(vN);
    vec3 v = normalize(uCam - vW);
    vec3 l = normalize(vec3(-0.6, 0.55, 0.45));
    float diffuse = clamp(dot(n, l), 0.0, 1.0);
    float rim = pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 3.0);

    // wireframe: grid lines in world space, one pixel wide whatever the distance
    vec2 g = vW.xz / 0.17;
    vec2 q = abs(fract(g - 0.5) - 0.5) / fwidth(g);
    float line = (1.0 - clamp(min(q.x, q.y) - 0.15, 0.0, 1.0)) * (1.0 - smoothstep(0.35, 0.9, fwidth(g.x) + fwidth(g.y)));
    float glow = 0.12 + 1.5 * diffuse * diffuse + rim * 0.5 + smoothstep(0.3, 1.0, vH) * 0.5;
    float volume = smoothstep(-0.55, 0.85, vH + uBias);          // valleys fall into shadow, crests stay lit
    glow *= 0.01 + 0.99 * volume * volume;
    vec3 ink = vec3(1.0, 0.5, 0.13) * glow;
    vec3 fill = vec3(0.45, 0.16, 0.03) * (0.02 + 0.6 * diffuse * diffuse);
    vec3 col = ink * line * 0.8 + fill * volume;
    float cover = clamp(line * (0.08 + 0.72 * volume) + 0.3 * diffuse * volume, 0.0, 1.0);

    vec2 uv = gl_FragCoord.xy / uRes;
    float keep = smoothstep(uClear.x, uClear.y, uv.x) * (1.0 - smoothstep(0.86, 1.0, uv.x)) * (1.0 - smoothstep(uClear.z, uClear.w, uv.y));
    float alpha = vEdge * keep;
    alpha *= 1.0 - smoothstep(0.74, 0.98, uv.y);
    alpha *= smoothstep(0.0, 0.2, uv.y);
    outColor = vec4(col * alpha, cover * alpha);
  }`;

  // Per-instance composition. Shape: half width, depth, amplitude, loudest x. Clear: area kept free for text.
  const SCENES = {
    hero: {
      wide: { eye: [1.6, 2.6, 5.0], look: [1.8, -0.4, -4.5], fov: 46, shape: [13, 22, 1.5, 2.8], clear: [0.34, 0.62, 0.75, 0.97] },
      narrow: { eye: [0.4, 3.0, 5.2], look: [0.3, -0.7, -3.8], fov: 58, shape: [10, 20, 1.7, 0.6], freq: 1.5, bias: 0.95, clear: [-1, 0, 0.42, 0.58] },
      time: 3
    },
    closing: {
      wide: { eye: [0, 3.0, 5.0], look: [0, -0.6, -4.5], fov: 44, shape: [14, 22, 0.85, 0], clear: [-1, 0, 0.5, 0.78] },
      narrow: { eye: [0, 3.2, 5.6], look: [0, -0.8, -4], fov: 54, shape: [10, 20, 2.1, 0], freq: 2.2, bias: 0.5, clear: [-1, 0, 0.3, 0.5] },
      time: 3.2
    }
  };

  function perspective(fov, aspect, near, far) {
    const f = 1 / Math.tan(fov * Math.PI / 360), nf = 1 / (near - far);
    return [f / aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, (far + near) * nf, -1, 0, 0, 2 * far * near * nf, 0];
  }
  function lookAt(eye, target) {
    const sub = (a, b) => a.map((x, i) => x - b[i]);
    const norm = a => { const l = Math.hypot(...a); return a.map(x => x / l); };
    const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
    const z = norm(sub(eye, target)), x = norm(cross([0, 1, 0], z)), y = cross(z, x);
    const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    return [x[0], y[0], z[0], 0, x[1], y[1], z[1], 0, x[2], y[2], z[2], 0, -dot(x, eye), -dot(y, eye), -dot(z, eye), 1];
  }
  function multiply(a, b) {
    const out = new Array(16).fill(0);
    for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) for (let k = 0; k < 4; k++) out[c * 4 + r] += a[k * 4 + r] * b[c * 4 + k];
    return out;
  }

  function compile(gl, type, source) {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(shader));
    return shader;
  }

  function create(container, scene, options = {}) {
    const canvas = container.querySelector('canvas');
    const gl = canvas.getContext('webgl2', { alpha: true, premultipliedAlpha: true, antialias: (devicePixelRatio || 1) < 1.75, powerPreference: 'low-power', preserveDrawingBuffer: !!options.keep });
    if (!gl) return null;
    const program = gl.createProgram();
    gl.attachShader(program, compile(gl, gl.VERTEX_SHADER, VERT));
    gl.attachShader(program, compile(gl, gl.FRAGMENT_SHADER, FRAG));
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(program));
    gl.useProgram(program);

    // Grid: 160 x 160 vertices, indexed triangles.
    const cols = 160, rows = 160, grid = new Float32Array(cols * rows * 2);
    for (let j = 0; j < rows; j++) for (let i = 0; i < cols; i++) {
      grid[(j * cols + i) * 2] = i / (cols - 1) * 2 - 1;
      grid[(j * cols + i) * 2 + 1] = j / (rows - 1);
    }
    const indices = new Uint16Array((cols - 1) * (rows - 1) * 6);
    let n = 0;
    for (let j = 0; j < rows - 1; j++) for (let i = 0; i < cols - 1; i++) {
      const a = j * cols + i, b = a + 1, c = a + cols, d = c + 1;
      indices.set([a, c, b, b, c, d], n); n += 6;
    }
    const vao = gl.createVertexArray();
    gl.bindVertexArray(vao);
    gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
    gl.bufferData(gl.ARRAY_BUFFER, grid, gl.STATIC_DRAW);
    const loc = gl.getAttribLocation(program, 'aGrid');
    gl.enableVertexAttribArray(loc);
    gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, gl.createBuffer());
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, indices, gl.STATIC_DRAW);

    const u = name => gl.getUniformLocation(program, name);
    const uniforms = { vp: u('uVP'), t: u('uT'), shape: u('uShape'), cam: u('uCam'), res: u('uRes'), clear: u('uClear'), pointer: u('uPointer'), freq: u('uFreq'), bias: u('uBias') };
    gl.enable(gl.DEPTH_TEST);
    gl.depthFunc(gl.LEQUAL);
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
    gl.clearColor(0, 0, 0, 0);

    let width = 0, height = 0, config = scene.wide;
    function resize() {
      const box = container.getBoundingClientRect();
      // cap the cost: DPR at most 1.5 and at most about 1.6 megapixels
      let ratio = Math.min(devicePixelRatio || 1, 1.5);
      ratio = Math.min(ratio, Math.sqrt(1.6e6 / Math.max(box.width * box.height, 1)));
      width = Math.max(2, Math.round(box.width * ratio));
      height = Math.max(2, Math.round(box.height * ratio));
      if (canvas.width !== width || canvas.height !== height) { canvas.width = width; canvas.height = height; }
      config = box.width < 700 ? scene.narrow : scene.wide;
    }

    const pointer = { x: 0, z: 0, strength: 0, tx: 0, tz: 0, ts: 0, active: false };
    // unproject a point of the canvas onto the plane y = 0, using the same camera as draw()
    function aim(nx, ny) {
      const e = config.eye, t = config.look, tan = Math.tan(config.fov * Math.PI / 360);
      const f = [t[0] - e[0], t[1] - e[1], t[2] - e[2]]; const fl = Math.hypot(...f); f.forEach((v, i) => f[i] = v / fl);
      const r = [-f[2], 0, f[0]]; const rl = Math.hypot(...r); r.forEach((v, i) => r[i] = v / rl);
      const up = [r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]];
      const aspect = width / height;
      const d = f.map((v, i) => v + nx * aspect * tan * r[i] + ny * tan * up[i]);
      if (d[1] >= -0.02) return null;
      const k = -e[1] / d[1];
      return [e[0] + d[0] * k, e[2] + d[2] * k];
    }
    function point(nx, ny, strength) {
      const hit = aim(nx, ny);
      if (!hit) { pointer.ts = 0; return; }
      pointer.tx = hit[0]; pointer.tz = hit[1]; pointer.ts = strength;
      if (!pointer.active) { pointer.x = pointer.tx; pointer.z = pointer.tz; pointer.active = true; }
    }
    function draw(time) {
      gl.viewport(0, 0, width, height);
      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
      const view = lookAt(config.eye, config.look);
      const vp = multiply(perspective(config.fov, width / height, 0.1, 80), view);
      gl.uniformMatrix4fv(uniforms.vp, false, vp);
      gl.uniform1f(uniforms.t, time);
      gl.uniform4fv(uniforms.shape, config.shape);
      gl.uniform3fv(uniforms.cam, config.eye);
      gl.uniform2f(uniforms.res, width, height);
      gl.uniform4fv(uniforms.clear, config.clear);
      gl.uniform1f(uniforms.freq, config.freq || 1);
      gl.uniform1f(uniforms.bias, config.bias || 0);
      // ease towards the target so the surface answers softly, and let it settle when the pointer leaves
      pointer.x += (pointer.tx - pointer.x) * 0.12; pointer.z += (pointer.tz - pointer.z) * 0.12;
      pointer.strength += (pointer.ts - pointer.strength) * 0.06;
      gl.uniform3f(uniforms.pointer, pointer.x, pointer.z, pointer.strength);
      gl.drawElements(gl.TRIANGLES, indices.length, gl.UNSIGNED_SHORT, 0);
    }

    resize();
    return { resize, draw, point, settled: () => Math.abs(pointer.strength - pointer.ts) < 0.002 };
  }

  function start(container, name, options = {}) {
    let scene = SCENES[name];
    let surface = null;
    try { surface = create(container, scene, options); } catch (error) { console.warn('Sound surface unavailable:', error); }
    if (!surface) { container.classList.add('fallback'); return null; }
    container.classList.add('live');
    const still = options.still || matchMedia('(prefers-reduced-motion: reduce)').matches;
    const forced = /[#&]t=([\d.]+)/.exec(location.hash);
    if (forced) scene = { ...scene, time: Number(forced[1]) };
    surface.draw(scene.time);
    if (still) {
      addEventListener('resize', () => { surface.resize(); surface.draw(scene.time); });
      return surface;
    }
    let running = false, visible = false, frame = 0, last = 0, clock = scene.time;
    function tick(now) {
      frame = 0;
      if (!running) return;
      frame = requestAnimationFrame(tick);
      if (now - last < 32) return;            // about 30 fps is plenty for a slow surface
      clock += Math.min((now - last) / 1000, 0.1) * 0.8;
      last = now;
      surface.draw(clock);
    }
    function update() {
      const should = visible && !document.hidden;
      if (should && !running) { running = true; last = performance.now(); frame = requestAnimationFrame(tick); }
      if (!should) { running = false; if (frame) cancelAnimationFrame(frame); frame = 0; }
    }
    new IntersectionObserver(entries => { visible = entries[0].isIntersecting; update(); }).observe(container);
    // Mouse only: touch scrolling should not pull on the surface.
    const host = container.parentElement;
    host.addEventListener('pointermove', event => {
      if (event.pointerType !== 'mouse') return;
      const box = container.getBoundingClientRect();
      const nx = ((event.clientX - box.left) / box.width) * 2 - 1, ny = 1 - ((event.clientY - box.top) / box.height) * 2;
      surface.point(nx, ny, 1);
    });
    host.addEventListener('pointerleave', () => surface.point(0, 0, 0));
    document.addEventListener('visibilitychange', update);
    addEventListener('resize', () => { surface.resize(); if (!running) surface.draw(clock); });
    return surface;
  }

  // Diagnostics for phones: open the page with #debug to see why the surface is or is not moving.
  if (location.hash.includes('debug')) {
    const panel = document.createElement('pre');
    panel.style.cssText = 'position:fixed;z-index:99;left:8px;bottom:8px;margin:0;padding:8px 10px;background:#000c;color:#ffb347;font:11px/1.4 monospace;border-radius:6px;pointer-events:none;white-space:pre-wrap';
    document.body.append(panel);
    const probe = document.createElement('canvas').getContext('webgl2');
    const report = () => {
      const lines = ['webgl2: ' + (probe ? 'yes' : 'NO (poster fallback)'), 'reduced motion: ' + (matchMedia('(prefers-reduced-motion: reduce)').matches ? 'YES (still frame only)' : 'no'), 'dpr: ' + devicePixelRatio];
      document.querySelectorAll('[data-surface]').forEach(e => lines.push(e.dataset.surface + ': ' + (e.classList.contains('fallback') ? 'fallback poster' : e.classList.contains('live') ? 'live' : 'not started')));
      if (probe) { const info = probe.getExtension('WEBGL_debug_renderer_info'); if (info) lines.push(String(probe.getParameter(info.UNMASKED_RENDERER_WEBGL)).slice(0, 48)); }
      panel.textContent = lines.join(' | ');
    };
    report(); setInterval(report, 1000);
  }

  window.AnarkeySurface = { start };
  document.querySelectorAll('[data-surface]').forEach(container => {
    if (!location.hash.includes('nosurface')) start(container, container.dataset.surface, { still: location.hash.includes('still'), keep: location.hash.includes('still') });
  });
})();
