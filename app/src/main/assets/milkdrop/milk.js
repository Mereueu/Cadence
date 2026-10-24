// MilkDrop visuals for Cadence, rendered by Butterchurn (MIT) with the community preset packs.
// Audio comes from the app (the PCM it is playing), not from a microphone.
(function () {
  'use strict';
  var bridge = window.CadenceMilk;
  var canvas = document.getElementById('c');
  var viz = null;
  var presets = {};
  var quality = 0.75;
  var mono = new Uint8Array(1024);
  var useLevels = true;

  function loadScript(name) {
    return new Promise(function (resolve) {
      var s = document.createElement('script');
      s.src = 'lib/' + name;
      s.onload = resolve;
      s.onerror = resolve;
      document.body.appendChild(s);
    });
  }

  function size() {
    var dpr = (window.devicePixelRatio || 1) * quality;
    return { w: Math.max(64, Math.round(window.innerWidth * dpr)), h: Math.max(64, Math.round(window.innerHeight * dpr)) };
  }

  function resize() {
    if (!viz) return;
    var s = size();
    canvas.width = s.w;
    canvas.height = s.h;
    viz.setRendererSize(s.w, s.h);
  }

  function readAudio() {
    var data = bridge ? bridge.frame() : '';
    if (data) {
      var bin = atob(data);
      var n = Math.min(1024, bin.length);
      for (var i = 0; i < n; i++) mono[i] = bin.charCodeAt(i);
    } else {
      mono.fill(128);
    }
  }

  function loop() {
    readAudio();
    try {
      if (useLevels) {
        viz.render({ audioLevels: { timeByteArray: mono, timeByteArrayL: mono, timeByteArrayR: mono } });
      } else {
        viz.render();
      }
    } catch (e) {
      if (useLevels) useLevels = false;
    }
    requestAnimationFrame(loop);
  }

  function addPack(global) {
    var m = window[global];
    if (!m) return;
    var mod = m.default || m;
    var p = typeof mod.getPresets === 'function' ? mod.getPresets() : mod;
    for (var k in p) if (Object.prototype.hasOwnProperty.call(p, k)) presets[k] = p[k];
  }

  async function init() {
    if (bridge) quality = parseFloat(bridge.quality()) || 0.75;
    var files = (window.MILK_FILES || ['butterchurn.min.js', 'butterchurnPresets.min.js']).slice();
    files.sort(function (a, b) { return (a === 'butterchurn.min.js' ? -1 : 0) - (b === 'butterchurn.min.js' ? -1 : 0); });
    for (var i = 0; i < files.length; i++) await loadScript(files[i]);

    var bc = window.butterchurn && (window.butterchurn.default || window.butterchurn);
    if (!bc || !bc.createVisualizer) {
      if (bridge) bridge.onError('MilkDrop engine missing from this build');
      return;
    }
    ['butterchurnPresets', 'butterchurnPresetsExtra', 'butterchurnPresetsExtra2', 'butterchurnPresetsMD1', 'butterchurnPresetsNonMinimal']
      .forEach(addPack);

    var AC = window.AudioContext || window.webkitAudioContext;
    var ctx = new AC();
    var s = size();
    canvas.width = s.w;
    canvas.height = s.h;
    viz = bc.createVisualizer(ctx, canvas, { width: s.w, height: s.h, pixelRatio: 1, textureRatio: 1 });
    window.addEventListener('resize', resize);

    var names = Object.keys(presets).sort(function (a, b) { return a.toLowerCase().localeCompare(b.toLowerCase()); });
    if (bridge) bridge.onReady(JSON.stringify(names));
    requestAnimationFrame(loop);
  }

  // Called from the app.
  window.cadence = {
    load: function (name, blend) {
      if (!viz || !presets[name]) return;
      viz.loadPreset(presets[name], blend);
      if (bridge) bridge.onPreset(name);
    },
    setQuality: function (q) {
      quality = q;
      resize();
    }
  };

  init();
})();
