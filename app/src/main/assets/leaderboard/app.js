/**
 * TOCA SOCIAL — TV Leaderboard  |  app.js
 *
 * Rotation logic:
 *   For each game → show HOY (12 s) → then ALL-TIME (8 s) → next game
 *
 * Always renders 10 rows (placeholder rows if SQLite has fewer entries).
 */

// ════════════════════════════════════════
//  CONFIG
// ════════════════════════════════════════

var GAMES = [
  { id: 'STRIKER', label: 'STRIKER', logo: 'logos/striker.png', img: 'images/striker.png' },
  { id: 'ATOMSPLITTER', label: 'ATOM SPLITTER', logo: 'logos/atomsplitter.png', img: 'images/atomsplitter.png' },
  { id: 'ZOMBIES', label: 'ZOMBIES', logo: 'logos/zombies.png', img: 'images/zombies.png' },
  { id: 'TOCACHALLENGE', label: 'TOCA CHALLENGE', logo: 'logos/tocachallenge.png', img: 'images/tocachallenge.png' }
];

var SEG_COUNT = 22;    // segments in the pixelated progress bar
var HOY_MS = 12000; // 12 s showing today's scores
var ALLTIME_MS = 8000; // 8 s showing all-time records
var PLACEHOLDER_N = 10;    // always render this many rows

var currentGameIdx = 0;
var currentMode = 'today';   // 'today' | 'alltime'

var phaseTimer = null;
var rafProgress = null;
var progressStart = 0;
var phaseDuration = HOY_MS;

// ════════════════════════════════════════
//  BOOT
// ════════════════════════════════════════

document.addEventListener('DOMContentLoaded', function () {
  buildTabs();
  startClock();
  enterPhase('today');   // always start with HOY
});

// ════════════════════════════════════════
//  CLOCK
// ════════════════════════════════════════

function startClock() {
  tickClock();
  setInterval(tickClock, 1000);
}

function tickClock() {
  var clockEl = document.getElementById('clock');
  var dateEl = document.getElementById('date-display');
  if (window.Android && typeof window.Android.getCurrentTimeString === 'function') {
    if (clockEl) clockEl.textContent = window.Android.getCurrentTimeString();
    if (dateEl) dateEl.textContent = window.Android.getCurrentDateString();
  } else {
    var now = new Date();
    if (clockEl) clockEl.textContent = now.toLocaleTimeString('es-GT', { hour12: false });
    if (dateEl) {
      var dStr = now.toLocaleDateString('es-GT', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
      dateEl.textContent = dStr.charAt(0).toUpperCase() + dStr.slice(1);
    }
  }
}

// ════════════════════════════════════════
//  TABS
// ════════════════════════════════════════

function buildTabs() {
  var nav = document.getElementById('game-tabs');
  if (!nav) return;
  nav.innerHTML = '';
  GAMES.forEach(function (game, idx) {
    var btn = document.createElement('button');
    btn.className = 'game-tab' + (idx === currentGameIdx ? ' active' : '');
    btn.id = 'tab-' + idx;
    btn.innerHTML =
      '<img src="' + game.logo + '" class="tab-game-logo" alt="' + game.label + '">' +
      '<span class="tab-game-label">' + game.label + '</span>';
    btn.addEventListener('click', function () {
      currentGameIdx = idx;
      refreshTabsUI();
      enterPhase('today');   // clicking a tab always restarts from HOY
    });
    nav.appendChild(btn);
  });
}

function refreshTabsUI() {
  GAMES.forEach(function (_, idx) {
    var el = document.getElementById('tab-' + idx);
    if (el) el.className = 'game-tab' + (idx === currentGameIdx ? ' active' : '');
  });
}

// ════════════════════════════════════════
//  PHASE ROTATION: HOY → ALL-TIME → next game
// ════════════════════════════════════════

function enterPhase(phase) {
  if (phaseTimer) clearTimeout(phaseTimer);
  if (rafProgress) cancelAnimationFrame(rafProgress);

  currentMode = phase;
  phaseDuration = phase === 'today' ? HOY_MS : ALLTIME_MS;
  progressStart = Date.now();

  // Update mode label UI
  var lblToday = document.getElementById('mode-label-today');
  var lblAlltime = document.getElementById('mode-label-alltime');
  if (lblToday) lblToday.className = 'mode-label' + (phase === 'today' ? ' active-mode' : '');
  if (lblAlltime) lblAlltime.className = 'mode-label' + (phase === 'alltime' ? ' active-mode' : '');

  loadAndRender();
  animateProgressBar();

  phaseTimer = setTimeout(function () {
    if (currentMode === 'today') {
      // Switch to all-time for same game
      enterPhase('alltime');
    } else {
      // Move to next game, back to HOY
      currentGameIdx = (currentGameIdx + 1) % GAMES.length;
      refreshTabsUI();
      enterPhase('today');
    }
  }, phaseDuration);
}

function animateProgressBar() {
  var fill = document.getElementById('mode-progress-fill');
  if (!fill) return;
  function tick() {
    var pct = Math.min(((Date.now() - progressStart) / phaseDuration) * 100, 100);
    fill.style.width = pct + '%';
    rafProgress = requestAnimationFrame(tick);
  }
  tick();
}

// ════════════════════════════════════════
//  DATA — Android Bridge
// ════════════════════════════════════════

function loadScores() {
  if (!window.Android) return mockScores();
  var gameId = GAMES[currentGameIdx].id;
  try {
    var json = currentMode === 'today'
      ? window.Android.getTodayScores(gameId)
      : window.Android.getAllTimeRecords(gameId);
    var data = JSON.parse(json || '[]');
    return Array.isArray(data) ? data : [];
  } catch (e) {
    return [];
  }
}

function loadHallOfFame() {
  if (!window.Android) {
    return [
      { game: GAMES[0], record: { playerName: 'Melissa', score: 2650 } },
      { game: GAMES[1], record: { playerName: 'Carlos', score: 1980 } },
      { game: GAMES[2], record: { playerName: 'Alex', score: 3420 } },
      { game: GAMES[3], record: { playerName: 'Sofia', score: 4100 } }
    ];
  }
  try {
    var records = JSON.parse(window.Android.getHallOfFame() || '[]');
    var bestByGame = {};
    records.forEach(function (r) {
      if (!bestByGame[r.gameType] || r.score > bestByGame[r.gameType].score)
        bestByGame[r.gameType] = r;
    });
    return GAMES.map(function (g) {
      return { game: g, record: bestByGame[g.id] || null };
    });
  } catch (e) { return []; }
}

function mockScores() {
  return [
    { playerName: 'VALERIA P.', score: 9800, playedDate: '2026-09-07' },
    { playerName: 'DIEGO S.', score: 8900, playedDate: '2026-09-07' },
    { playerName: 'MATEO G.', score: 8500, playedDate: '2026-09-07' },
    { playerName: 'CARLOS R.', score: 7200, playedDate: '2026-09-07' },
    { playerName: 'LUCAS M.', score: 6800, playedDate: '2026-09-07' },
  ];
}

// ════════════════════════════════════════
//  RENDER ORCHESTRATOR
// ════════════════════════════════════════

function loadAndRender() {
  var game = GAMES[currentGameIdx];

  // Sub-header with game background image
  var gh = document.getElementById('game-header');
  if (gh) {
    if (game.img) {
      gh.style.backgroundImage =
        'linear-gradient(90deg, rgba(10, 0, 55, 0.88) 0%, rgba(10, 0, 55, 0.55) 45%, rgba(10, 0, 55, 0.85) 100%), url("' + game.img + '")';
    } else {
      gh.style.backgroundImage = 'none';
    }
    gh.innerHTML =
      '<div class="game-title-row">' +
      '<img src="' + game.logo + '" class="game-logo-large" alt="' + game.label + '">' +
      '<span class="game-name-large">' + game.label + '</span>' +
      '</div>' +
      '<span class="game-mode-badge">' +
      (currentMode === 'today' ? '▶ CAMPEONES DE HOY' : '★ RÉCORDS HISTÓRICOS') +
      '</span>';
  }

  renderScoreList(loadScores());
  renderFooter();
}

// ════════════════════════════════════════
//  RENDER — Score list (always 10 rows)
// ════════════════════════════════════════

function renderScoreList(scores) {
  var list = document.getElementById('score-list');
  if (!list) return;

  var top = scores.slice(0, PLACEHOLDER_N);
  var maxScore = top.length > 0 ? (top[0].score || 1) : 1;

  // Pad to PLACEHOLDER_N with empty entries
  while (top.length < PLACEHOLDER_N) top.push(null);

  list.innerHTML = top.map(function (entry, idx) {
    var rank = idx + 1;

    if (!entry) {
      // Empty placeholder row
      return (
        '<div class="score-row empty" style="--row-idx:' + idx + '">' +
        '<div class="rank-badge">' + rank + '</div>' +
        '<div class="player-info"><div class="player-name">- - -</div></div>' +
        '<div class="seg-bar">' + buildSegBar(0, rank) + '</div>' +
        '<div class="score-value">' +
        '<span class="score-number">???</span>' +
        '<span class="score-pts">PTS</span>' +
        '</div>' +
        '</div>'
      );
    }

    var pct = Math.round((entry.score / maxScore) * 100);
    var rankCls = rank === 1 ? 'rank-1' : rank === 2 ? 'rank-2' : rank === 3 ? 'rank-3' : '';
    var badgeCls = rank === 1 ? 'gold' : rank === 2 ? 'silver' : rank === 3 ? 'bronze' : '';
    var dateStr = fmtDate(entry.playedDate || entry.achievedDate || '');

    return (
      '<div class="score-row ' + rankCls + '" style="--row-idx:' + idx + '">' +
      '<div class="rank-badge ' + badgeCls + '">' + rank + '</div>' +
      '<div class="player-info">' +
      '<div class="player-name">' + escHtml(entry.playerName) + '</div>' +
      (dateStr ? '<div class="player-date">' + dateStr + '</div>' : '') +
      '</div>' +
      '<div class="seg-bar" data-pct="' + pct + '">' + buildSegBar(0, rank) + '</div>' +
      '<div class="score-value">' +
      '<span class="score-number">' + fmtScore(entry.score) + '</span>' +
      '<span class="score-pts">PTS</span>' +
      '</div>' +
      '</div>'
    );
  }).join('');

  // Animate bars after two rAF frames (let browser paint first)
  requestAnimationFrame(function () {
    requestAnimationFrame(function () {
      list.querySelectorAll('.seg-bar[data-pct]').forEach(function (bar) {
        var pct = parseInt(bar.dataset.pct, 10) || 0;
        var filled = Math.round(pct / 100 * SEG_COUNT);
        bar.innerHTML = buildSegBar(filled, 0);
      });
    });
  });
}

/** Build SEG_COUNT square blocks, `filledCount` of them filled */
function buildSegBar(filledCount, rank) {
  var html = '';
  for (var i = 0; i < SEG_COUNT; i++) {
    html += '<div class="seg-block' + (i < filledCount ? ' filled' : '') + '"></div>';
  }
  return html;
}

// ════════════════════════════════════════
//  RENDER — Footer Hall of Fame
// ════════════════════════════════════════

function renderFooter() {
  var track = document.getElementById('footer-ticker-track');
  if (!track) return;

  var hof = loadHallOfFame();
  var records = hof.filter(function (d) { return d.record; });

  var items = [];

  // Add records
  records.forEach(function (d) {
    items.push(
      '<div class="ticker-item">' +
        '<span class="ticker-star">★</span>' +
        '<img src="' + d.game.logo + '" class="ticker-game-logo" alt="' + d.game.label + '">' +
        '<span class="ticker-game-name">' + d.game.label + '</span>' +
        '<span class="ticker-sep">▸</span>' +
        '<span class="ticker-pname">' + escHtml(d.record.playerName) + '</span>' +
        '<span class="ticker-pscore">' + fmtScore(d.record.score) + ' PTS</span>' +
      '</div>'
    );
  });

  // Add social and arcade hype items
  items.push(
    '<div class="ticker-item">' +
      '<span class="ticker-star">★</span>' +
      '<span class="ticker-cta highlight">🎮 ¡SUPERA EL RÉCORD EN TU BOX Y APARECE EN PANTALLA!</span>' +
    '</div>'
  );

  items.push(
    '<div class="ticker-item">' +
      '<span class="ticker-star">★</span>' +
      '<span class="ticker-cta">📱 COMPARTE TU VICTORIA CON #TocaSocialGT @TocaSocial</span>' +
    '</div>'
  );

  items.push(
    '<div class="ticker-item">' +
      '<span class="ticker-star">★</span>' +
      '<span class="ticker-cta highlight">⚡ ACTUALIZACIÓN DE PUNTAJES EN VIVO</span>' +
    '</div>'
  );

  if (records.length === 0) {
    items.push(
      '<div class="ticker-item">' +
        '<span class="ticker-star">★</span>' +
        '<span class="ticker-cta">🏆 SÉ EL PRIMERO EN ESTABLECER UN RÉCORD HOY</span>' +
      '</div>'
    );
  }

  var oneSet = items.join('');
  // Duplicamos para que la animación CSS con translateX(-50%) sea continua y sin cortes
  track.innerHTML = oneSet + oneSet;
}

// ════════════════════════════════════════
//  RECORD BREAK CELEBRATION
// ════════════════════════════════════════

window.showRecordBreakCelebration = function (event) {
  var overlay = document.getElementById('record-overlay');
  if (!overlay) return;

  var gameLabel = (event.gameType || 'TOCA')
    .replace('ATOMSPLITTER', 'ATOM SPLITTER')
    .replace('TOCACHALLENGE', 'TOCA CHALLENGE');

  setText('rec-game', '— ' + gameLabel + ' —');
  setText('rec-player', event.playerName || '');
  setText('rec-score', fmtScore(event.newScore));

  var prevEl = document.getElementById('rec-prev');
  if (prevEl) {
    prevEl.textContent = event.previousRecord
      ? 'RÉCORD ANTERIOR: ' + fmtScore(event.previousRecord) +
      (event.previousHolder ? ' · ' + event.previousHolder : '')
      : '¡PRIMER RÉCORD EN ESTA CATEGORÍA!';
  }

  overlay.classList.remove('hidden');
  setTimeout(function () { overlay.classList.add('hidden'); }, 10000);
};

// ════════════════════════════════════════
//  HELPERS
// ════════════════════════════════════════

function fmtScore(n) {
  return Number(n).toLocaleString('es-GT');
}

function fmtDate(dateStr) {
  if (!dateStr) return '';
  try {
    var p = String(dateStr).split('-');
    if (p.length === 3) {
      var d = new Date(+p[0], +p[1] - 1, +p[2]);
      return d.toLocaleDateString('es-GT', { day: 'numeric', month: 'short', year: 'numeric' });
    }
  } catch (e) { /* ignore */ }
  return dateStr;
}

function escHtml(s) {
  if (!s) return '';
  return String(s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function setText(id, text) {
  var el = document.getElementById(id);
  if (el) el.textContent = text;
}
