// MIT License
// Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio

/**
 * Laser triangles — a replica of osu!lazer `TrianglesV2`
 * (osu.Game/Graphics/Backgrounds/TrianglesV2.cs) + the depth & edge-fade
 * effects from the app's Kotlin port (TrianglesBackground.kt).
 *
 * A SINGLE source for every WebView page (licenses, contributor, etc.).
 * A page just needs a <canvas id="bg-triangles"> and then:
 *   <script src="triangles.js"></script>
 *
 * Original lazer formula: size = 100 x ScaleAdjust PIXELS (absolute),
 * AimCount = width x 0.02 x SpawnRatio, drift = velocity x 50 / H
 * rel/s, speed Box-Muller N(0.5, 0.16²) min 0.1, seed 64140.
 * Depth (from the Kotlin port): size 0.45-1.15x, brightness 0.35-1x,
 * color faded x0.82 for far ones; smooth fade at the bottom edge (spawn)
 * & the top (exit). All triangles are white outlines.
 */
(function () {
  var canvas = document.getElementById('bg-triangles');
  if (!canvas) return; // page without a canvas → skip silently
  var ctx = canvas.getContext('2d');

  var ALPHA = 0.45, SCALE_ADJUST = 1, VELOCITY = 1.5, SPAWN_RATIO = 2.5, STROKE = 1;
  var MAX_PARTICLES = 64, SEED = 64140;

  // Seeded RNG (mulberry32) — particle order IDENTICAL to native.
  function mulberry32(a) {
    return function () {
      a |= 0; a = a + 0x6D2B79F5 | 0;
      var t = Math.imul(a ^ a >>> 15, 1 | a);
      t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
      return ((t ^ t >>> 14) >>> 0) / 4294967296;
    };
  }
  function lerp(a, b, t) { return a + (b - a) * t; }

  // Speed multiplier exactly like CreateTriangle — Box-Muller N(0.5, 0.16²), min 0.1.
  function normalSpeed(rng) {
    var u1 = 1 - rng();
    var u2 = rng();
    var z = Math.sqrt(-2 * Math.log(u1)) * Math.sin(2 * Math.PI * u2);
    return Math.max(0.1, 0.5 + 0.16 * z);
  }

  var W = 0, H = 0, baseTriW = 0, baseTriH = 0, count = 0;
  var particles = [];

  function initParticles() {
    var rng = mulberry32(SEED);
    particles = [];
    for (var i = 0; i < MAX_PARTICLES; i++) {
      var d = rng();
      var far = d * d;                       // biased toward "far" (many small, dim ones)
      var sizeF = lerp(1, lerp(0.45, 1.15, far), 1);   // perspective size
      var bright = lerp(1, lerp(0.35, 1, far), 1);     // brightness
      var rgbF = lerp(1, 0.82, far);                   // color fade (far)
      particles.push({
        x: rng(),
        speed: normalSpeed(rng),
        phase: rng(),
        sizeF: sizeF,
        bright: bright,
        rgbF: rgbF
      });
    }
  }

  function resize() {
    var dpr = window.devicePixelRatio || 1;
    W = window.innerWidth;
    H = window.innerHeight;
    canvas.width = Math.round(W * dpr);
    canvas.height = Math.round(H * dpr);
    canvas.style.width = W + 'px';
    canvas.style.height = H + 'px';
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    // Absolute TRIANGLE size (lazer): 100 x ScaleAdjust px, height x0.866.
    baseTriW = 100 * SCALE_ADJUST;
    baseTriH = baseTriW * 0.866;
    // AimCount exactly like TrianglesV2: DrawWidth × 0.02 × SpawnRatio.
    count = Math.max(1, Math.min(MAX_PARTICLES, Math.floor(W * 0.02 * SPAWN_RATIO)));
    if (particles.length === 0) initParticles();
  }

  function frame(nowSec) {
    ctx.clearRect(0, 0, W, H);
    // Laser upward drift: movedDistance = -t x Velocity x 50 / DrawHeight.
    var rate = VELOCITY * 50 / H;                  // rel/detik
    ctx.lineWidth = STROKE;
    ctx.lineCap = 'butt';
    for (var i = 0; i < count; i++) {
      var p = particles[i];
      var travel = nowSec * rate * Math.max(0.5, p.speed) + p.phase;
      // Drift UP + wrap only AFTER the triangle has fully exited the top
      // (wrap = 1 + relative triangle height — same as the Kotlin port).
      // With wrap=1 the y never goes negative → the top fade-out term is
      // always >= 1 → triangles vanish instantly at the top edge.
      var wrap = 1 + baseTriH / H;
      var y = (1 - (travel % wrap)) * H;
      var x = p.x * W;
      var triW = baseTriW * p.sizeF;
      var triH = baseTriH * p.sizeF;
      // Smooth edge fade (spawn: fade-in at the bottom, exit: fade-out at
      // the top). y goes down to -triH → the (y + triH)/triH term ramps
      // 0→1 exactly over the exit zone.
      var fade = Math.min((H - y) / triH, (y + triH) / triH);
      var a = ALPHA * Math.max(0, Math.min(1, fade)) * p.bright;
      if (a < 0.004) continue;
      var v = p.rgbF * 255;
      ctx.strokeStyle = 'rgba(' + v.toFixed(1) + ',' + v.toFixed(1) + ',' + v.toFixed(1) + ',' + a.toFixed(4) + ')';
      ctx.beginPath();
      ctx.moveTo(x, y);                            // top tip
      ctx.lineTo(x + triW / 2, y + triH);          // bottom-right
      ctx.lineTo(x - triW / 2, y + triH);          // bottom-left
      ctx.closePath();
      ctx.stroke();
    }
    requestAnimationFrame(function () { frame(performance.now() / 1000); });
  }

  resize();
  window.addEventListener('resize', resize);
  requestAnimationFrame(function () { frame(performance.now() / 1000); });
})();
