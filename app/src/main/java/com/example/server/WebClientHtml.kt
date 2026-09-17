package com.example.server

object WebClientHtml {
    fun getHtml(serverPort: Int): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>USB Mirror OBS - Low Latency Stream</title>
  <style>
    :root {
      --bg: #0b0f19;
      --card-bg: rgba(17, 24, 39, 0.85);
      --border: rgba(56, 189, 248, 0.25);
      --primary: #00e5ff;
      --primary-glow: rgba(0, 229, 255, 0.35);
      --accent: #10b981;
      --warning: #f59e0b;
      --danger: #ef4444;
      --text: #f3f4f6;
      --text-muted: #9ca3af;
    }
    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      user-select: none;
    }
    body {
      background-color: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      overflow: hidden;
      width: 100vw;
      height: 100vh;
      display: flex;
      flex-direction: column;
    }
    /* Stream container */
    #stage {
      position: relative;
      flex: 1;
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #05070d;
      overflow: hidden;
    }
    #videoCanvas {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
      box-shadow: 0 10px 40px rgba(0, 0, 0, 0.8);
      transition: transform 0.15s ease-out;
    }
    /* HUD overlay */
    .hud-bar {
      position: absolute;
      top: 16px;
      left: 16px;
      right: 16px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      pointer-events: none;
      z-index: 10;
      transition: opacity 0.25s ease;
    }
    .hud-pill {
      background: var(--card-bg);
      backdrop-filter: blur(12px);
      border: 1px solid var(--border);
      border-radius: 9999px;
      padding: 6px 14px;
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.5px;
      pointer-events: auto;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
    }
    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--accent);
      box-shadow: 0 0 10px var(--accent);
      animation: pulseDot 2s infinite;
    }
    .status-dot.disconnected {
      background: var(--danger);
      box-shadow: 0 0 10px var(--danger);
      animation: none;
    }
    @keyframes pulseDot {
      0%, 100% { opacity: 1; transform: scale(1); }
      50% { opacity: 0.6; transform: scale(0.85); }
    }
    /* Control Toolbar */
    .toolbar {
      position: absolute;
      bottom: 20px;
      left: 50%;
      transform: translateX(-50%);
      background: var(--card-bg);
      backdrop-filter: blur(16px);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 8px 16px;
      display: flex;
      align-items: center;
      gap: 12px;
      z-index: 10;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.6);
      transition: opacity 0.25s ease, transform 0.25s ease;
    }
    .btn {
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.1);
      color: var(--text);
      padding: 8px 14px;
      border-radius: 10px;
      cursor: pointer;
      font-size: 13px;
      font-weight: 500;
      display: flex;
      align-items: center;
      gap: 6px;
      transition: all 0.15s ease;
    }
    .btn:hover {
      background: rgba(0, 229, 255, 0.15);
      border-color: var(--primary);
      color: #fff;
    }
    .btn.active {
      background: var(--primary);
      color: #000;
      font-weight: 600;
      box-shadow: 0 0 16px var(--primary-glow);
    }
    .vol-slider {
      width: 80px;
      accent-color: var(--primary);
      cursor: pointer;
    }
    /* Audio visualizer canvas */
    #vuMeter {
      width: 48px;
      height: 14px;
      border-radius: 4px;
      background: rgba(0, 0, 0, 0.4);
      display: inline-block;
      vertical-align: middle;
    }
    /* OBS Clean mode */
    body.obs-mode #stage {
      background: transparent;
    }
    body.obs-mode .hud-bar,
    body.obs-mode .toolbar {
      display: none !important;
    }
    body.obs-mode #videoCanvas {
      box-shadow: none;
    }
    /* Overlay message */
    #overlayMsg {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      text-align: center;
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 28px 36px;
      z-index: 20;
      max-width: 440px;
      backdrop-filter: blur(20px);
    }
    #overlayMsg h2 {
      font-size: 20px;
      margin-bottom: 8px;
      color: var(--primary);
    }
    #overlayMsg p {
      font-size: 14px;
      color: var(--text-muted);
      line-height: 1.5;
      margin-bottom: 18px;
    }
    .code-box {
      background: #020617;
      border: 1px solid rgba(255, 255, 255, 0.15);
      padding: 8px 12px;
      border-radius: 8px;
      font-family: monospace;
      font-size: 12px;
      color: #38bdf8;
      margin-bottom: 14px;
      user-select: text;
    }
  </style>
</head>
<body>
  <div id="stage">
    <canvas id="videoCanvas" width="1080" height="1920"></canvas>

    <!-- Top HUD -->
    <div class="hud-bar" id="hudBar">
      <div class="hud-pill">
        <div class="status-dot" id="statusDot"></div>
        <span id="statusText">Connecting USB...</span>
        <span style="color: var(--primary); margin-left: 6px;" id="modeText">H.264 Low-Latency</span>
      </div>

      <div class="hud-pill">
        <span>FPS: <strong id="fpsVal" style="color: var(--primary);">0</strong></span>
        <span style="color: rgba(255,255,255,0.2);">|</span>
        <span>LATENCY: <strong id="pingVal" style="color: var(--accent);">-- ms</strong></span>
        <span style="color: rgba(255,255,255,0.2);">|</span>
        <span>DROP: <strong id="dropVal" style="color: var(--text-muted);">0</strong></span>
        <span style="color: rgba(255,255,255,0.2);">|</span>
        <canvas id="vuMeter" width="48" height="14"></canvas>
      </div>
    </div>

    <!-- Bottom Toolbar -->
    <div class="toolbar" id="toolbar">
      <button class="btn" id="unmuteBtn" title="Enable real-time audio playback">
        <span id="unmuteIcon">🔊</span> <span id="unmuteText">Unmute</span>
      </button>
      <input type="range" class="vol-slider" id="volSlider" min="0" max="1" step="0.05" value="1.0" title="Audio volume">

      <button class="btn" id="obsModeBtn" title="Hide all UI for clean OBS recording">
        🎥 OBS Mode
      </button>

      <button class="btn" id="fitModeBtn" title="Toggle aspect fit/fill">
        ⛶ Fit Screen
      </button>

      <button class="btn" id="fsBtn" title="Fullscreen">
        Full Screen
      </button>
    </div>

    <!-- Standby Overlay -->
    <div id="overlayMsg" style="display: none;">
      <h2 id="msgTitle">Waiting for Android Mirroring</h2>
      <p id="msgDesc">Ensure USB Debugging is ON and Android service is active. If opening in PC browser or OBS:</p>
      <div class="code-box">adb forward tcp:$serverPort tcp:$serverPort</div>
      <button class="btn active" id="retryBtn" style="margin: 0 auto;">Connect Stream</button>
    </div>
  </div>

  <script>
    // URL param check for OBS Mode (?obs=true)
    const urlParams = new URLSearchParams(window.location.search);
    const isObsParam = urlParams.get('obs') === 'true' || urlParams.get('obs') === '1';
    if (isObsParam) {
      document.body.classList.add('obs-mode');
    }

    const canvas = document.getElementById('videoCanvas');
    const ctx = canvas.getContext('2d', { alpha: false, desynchronized: true });
    const vuCanvas = document.getElementById('vuMeter');
    const vuCtx = vuCanvas.getContext('2d');

    const statusDot = document.getElementById('statusDot');
    const statusText = document.getElementById('statusText');
    const fpsVal = document.getElementById('fpsVal');
    const pingVal = document.getElementById('pingVal');
    const dropVal = document.getElementById('dropVal');
    const overlayMsg = document.getElementById('overlayMsg');
    const retryBtn = document.getElementById('retryBtn');
    const unmuteBtn = document.getElementById('unmuteBtn');
    const unmuteText = document.getElementById('unmuteText');
    const volSlider = document.getElementById('volSlider');
    const obsModeBtn = document.getElementById('obsModeBtn');
    const fsBtn = document.getElementById('fsBtn');

    let ws = null;
    let audioCtx = null;
    let audioGainNode = null;
    let nextAudioTime = 0;
    let isAudioMuted = true;
    let videoDecoder = null;
    let hasWebCodecs = typeof VideoDecoder !== 'undefined';
    let lastPingSend = 0;
    let pingInterval = null;

    // FPS counter
    let frameCount = 0;
    let lastFpsUpdate = performance.now();
    let droppedFrames = 0;

    // Audio level meter
    let currentAudioPeak = 0;

    function initAudio() {
      if (audioCtx) return;
      try {
        const AudioContextClass = window.AudioContext || window.webkitAudioContext;
        audioCtx = new AudioContextClass({ sampleRate: 48000, latencyHint: 'interactive' });
        audioGainNode = audioCtx.createGain();
        audioGainNode.gain.value = parseFloat(volSlider.value);
        audioGainNode.connect(audioCtx.destination);
        if (audioCtx.state === 'suspended') {
          audioCtx.resume();
        }
      } catch (e) {
        console.error('AudioContext init error:', e);
      }
    }

    function toggleMute() {
      initAudio();
      if (audioCtx && audioCtx.state === 'suspended') {
        audioCtx.resume();
      }
      isAudioMuted = !isAudioMuted;
      if (isAudioMuted) {
        unmuteBtn.classList.remove('active');
        unmuteText.innerText = 'Unmute';
        if (audioGainNode) audioGainNode.gain.value = 0;
      } else {
        unmuteBtn.classList.add('active');
        unmuteText.innerText = 'Mute';
        if (audioGainNode) audioGainNode.gain.value = parseFloat(volSlider.value);
      }
    }

    volSlider.addEventListener('input', (e) => {
      const vol = parseFloat(e.target.value);
      if (audioGainNode && !isAudioMuted) {
        audioGainNode.gain.value = vol;
      }
    });

    unmuteBtn.addEventListener('click', toggleMute);

    // OBS Mode toggle
    obsModeBtn.addEventListener('click', () => {
      document.body.classList.toggle('obs-mode');
    });

    // Keyboard shortcut 'H' to toggle HUD in OBS
    window.addEventListener('keydown', (e) => {
      if (e.key === 'h' || e.key === 'H') {
        document.body.classList.toggle('obs-mode');
      }
    });

    // Fullscreen toggle
    fsBtn.addEventListener('click', () => {
      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen().catch(() => {});
      } else {
        document.exitFullscreen().catch(() => {});
      }
    });

    // WebCodecs initialization
    function initVideoDecoder() {
      if (!hasWebCodecs) return;
      try {
        if (videoDecoder && videoDecoder.state !== 'closed') {
          videoDecoder.close();
        }
        videoDecoder = new VideoDecoder({
          output: (frame) => {
            if (canvas.width !== frame.displayWidth || canvas.height !== frame.displayHeight) {
              canvas.width = frame.displayWidth;
              canvas.height = frame.displayHeight;
            }
            ctx.drawImage(frame, 0, 0, canvas.width, canvas.height);
            frame.close();
            onFrameRendered();
          },
          error: (e) => {
            console.warn('VideoDecoder error, resetting:', e);
          }
        });
        videoDecoder.configure({
          codec: 'avc1.42E01F', // H.264 Baseline Profile Level 3.1
          optimizeForLatency: true
        });
      } catch (err) {
        console.error('Failed to configure VideoDecoder:', err);
        hasWebCodecs = false;
      }
    }

    function onFrameRendered() {
      frameCount++;
      const now = performance.now();
      if (now - lastFpsUpdate >= 1000) {
        fpsVal.innerText = Math.round((frameCount * 1000) / (now - lastFpsUpdate));
        frameCount = 0;
        lastFpsUpdate = now;
      }
    }

    // Play raw PCM audio chunks with anti-drift buffer scheduling
    function playPcmAudio(pcmDataView, sampleRate, channels) {
      if (!audioCtx || isAudioMuted) return;
      if (audioCtx.state === 'suspended') {
        audioCtx.resume();
      }

      const sampleCount = (pcmDataView.byteLength / 2) / channels;
      if (sampleCount <= 0) return;

      const audioBuffer = audioCtx.createBuffer(channels, sampleCount, sampleRate);
      let maxPeak = 0;

      for (let ch = 0; ch < channels; ch++) {
        const channelData = audioBuffer.getChannelData(ch);
        let byteOffset = ch * 2;
        for (let i = 0; i < sampleCount; i++) {
          const sampleInt16 = pcmDataView.getInt16(byteOffset, true);
          const sampleFloat = sampleInt16 / 32768.0;
          channelData[i] = sampleFloat;
          const abs = Math.abs(sampleFloat);
          if (abs > maxPeak) maxPeak = abs;
          byteOffset += channels * 2;
        }
      }

      currentAudioPeak = Math.max(currentAudioPeak * 0.8, maxPeak);

      const source = audioCtx.createBufferSource();
      source.buffer = audioBuffer;
      source.connect(audioGainNode);

      const currentTime = audioCtx.currentTime;
      // Anti-lag jitter buffer: Keep playback within 25-40ms of current time
      if (nextAudioTime < currentTime || nextAudioTime > currentTime + 0.1) {
        nextAudioTime = currentTime + 0.025; // 25ms lead
      }

      source.start(nextAudioTime);
      nextAudioTime += audioBuffer.duration;
    }

    // Connect WebSocket stream
    function connectStream() {
      const loc = window.location;
      const wsProto = (loc.protocol === 'https:') ? 'wss:' : 'ws:';
      const wsUrl = wsProto + '//' + loc.host + '/ws';

      statusText.innerText = 'Connecting...';
      statusDot.className = 'status-dot';
      overlayMsg.style.display = 'none';

      initVideoDecoder();

      try {
        ws = new WebSocket(wsUrl);
        ws.binaryType = 'arraybuffer';
      } catch (err) {
        showError('WebSocket connection failed: ' + err.message);
        return;
      }

      ws.onopen = () => {
        statusText.innerText = 'Live Mirror';
        statusDot.className = 'status-dot';
        overlayMsg.style.display = 'none';
        ws.send(JSON.stringify({ cmd: 'clientHello', webCodecs: hasWebCodecs }));

        if (pingInterval) clearInterval(pingInterval);
        pingInterval = setInterval(() => {
          if (ws && ws.readyState === WebSocket.OPEN) {
            lastPingSend = performance.now();
            ws.send(JSON.stringify({ cmd: 'ping', ts: lastPingSend }));
          }
        }, 1500);
      };

      ws.onmessage = (event) => {
        if (typeof event.data === 'string') {
          try {
            const msg = JSON.parse(event.data);
            if (msg.cmd === 'pong') {
              const rtt = Math.round(performance.now() - msg.ts);
              pingVal.innerText = rtt + ' ms';
            } else if (msg.cmd === 'stats') {
              if (msg.dropped !== undefined) {
                dropVal.innerText = msg.dropped;
              }
            }
          } catch (e) {}
          return;
        }

        // Binary frame handling
        const data = event.data;
        const view = new DataView(data);
        if (view.byteLength < 2) return;

        const tag = view.getUint8(0);

        if (tag === 0x01) {
          // Video frame
          // [0x01, type(1=h264, 2=jpeg), isKeyframe, timestamp(8 bytes), payload...]
          const codecType = view.getUint8(1);
          const isKeyframe = view.getUint8(2) === 1;
          const payload = new Uint8Array(data, 11);

          if (codecType === 1 && hasWebCodecs && videoDecoder && videoDecoder.state === 'configured') {
            try {
              const chunk = new EncodedVideoChunk({
                type: isKeyframe ? 'key' : 'delta',
                timestamp: performance.now() * 1000,
                data: payload
              });
              videoDecoder.decode(chunk);
            } catch (decErr) {
              console.warn('Decoder chunk error, re-initializing:', decErr);
              initVideoDecoder();
            }
          } else {
            // Turbo JPEG fallback
            const blob = new Blob([payload], { type: 'image/jpeg' });
            createImageBitmap(blob).then((bitmap) => {
              if (canvas.width !== bitmap.width || canvas.height !== bitmap.height) {
                canvas.width = bitmap.width;
                canvas.height = bitmap.height;
              }
              ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
              bitmap.close();
              onFrameRendered();
            }).catch(() => {});
          }
        } else if (tag === 0x02) {
          // Audio frame
          // [0x02, sampleRate(4 bytes), channels(1 byte), timestamp(8 bytes), pcm samples...]
          const sampleRate = view.getUint32(1);
          const channels = view.getUint8(5);
          const pcmView = new DataView(data, 14);
          playPcmAudio(pcmView, sampleRate, channels);
        }
      };

      ws.onclose = () => {
        statusText.innerText = 'Disconnected';
        statusDot.className = 'status-dot disconnected';
        if (pingInterval) clearInterval(pingInterval);
        showError('Disconnected from USB Mirror server.');
      };

      ws.onerror = (e) => {
        console.error('WS Error:', e);
      };
    }

    function showError(desc) {
      overlayMsg.style.display = 'block';
      document.getElementById('msgDesc').innerText = desc;
    }

    retryBtn.addEventListener('click', () => {
      connectStream();
    });

    // Draw VU-meter animation loop
    function drawVuMeter() {
      vuCtx.clearRect(0, 0, vuCanvas.width, vuCanvas.height);
      const level = Math.min(1, currentAudioPeak * 1.5);
      const fillW = Math.round(level * vuCanvas.width);

      const grad = vuCtx.createLinearGradient(0, 0, vuCanvas.width, 0);
      grad.addColorStop(0, '#10b981');
      grad.addColorStop(0.7, '#00e5ff');
      grad.addColorStop(1, '#ef4444');

      vuCtx.fillStyle = grad;
      vuCtx.fillRect(0, 0, fillW, vuCanvas.height);

      currentAudioPeak *= 0.92;
      requestAnimationFrame(drawVuMeter);
    }
    requestAnimationFrame(drawVuMeter);

    // Initial connection
    window.addEventListener('DOMContentLoaded', () => {
      connectStream();
    });
  </script>
</body>
</html>
        """.trimIndent()
    }
}
