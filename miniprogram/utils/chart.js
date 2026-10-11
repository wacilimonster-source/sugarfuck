/* Canvas 折线图（零依赖，替代 ECharts 的最小实现）
   - 目标区间背景带 + 空腹上限虚线 + 日均值折线 + 逐条着色数据点
   - 返回 points 供页面做“点圆点看明细”的命中判定
   - mini 模式用于今天页迷你趋势（无轴标签、无虚线） */

var C = require('./constants');
var S = require('./stats');
var F = require('./format');

function statusColor(st) {
  return st === 'ok' ? C.COLOR.ok : (st === 'low' ? C.COLOR.low : C.COLOR.high);
}

function drawTrend(ctx, w, h, opts) {
  var list = opts.list || [];
  var targets = opts.targets;
  var mini = !!opts.mini;
  if (!list.length) return { points: [] };

  var padL = mini ? 8 : 40;
  var padR = mini ? 8 : 14;
  var padT = mini ? 8 : 20;
  var padB = mini ? 8 : 32;

  var vals = list.map(function (r) { return r.value; });
  var lo = Math.min(3, Math.min.apply(null, vals) - 0.5);
  var hi = Math.max(12, Math.max.apply(null, vals) + 0.5);
  var x0 = list[0].measuredAt;
  var x1 = list[list.length - 1].measuredAt;

  function X(t) { return padL + (t - x0) / ((x1 - x0) || 1) * (w - padL - padR); }
  function Y(v) { return h - padB - (v - lo) / (hi - lo) * (h - padT - padB); }

  ctx.clearRect(0, 0, w, h);

  // 非空腹目标区间背景带
  var bandTop = Y(targets.nonFasting[1]);
  var bandBot = Y(targets.nonFasting[0]);
  ctx.fillStyle = 'rgba(14,156,115,0.11)';
  ctx.fillRect(padL, bandTop, w - padL - padR, bandBot - bandTop);

  var points = [];

  if (!mini) {
    // y 轴网格与刻度
    var step = (hi - lo) > 8 ? 4 : 2;
    ctx.strokeStyle = C.COLOR.grid;
    ctx.lineWidth = 1;
    ctx.font = '13px sans-serif';
    ctx.fillStyle = C.COLOR.label;
    ctx.textAlign = 'end';
    for (var v = Math.ceil(lo); v <= hi; v += step) {
      var gy = Y(v);
      ctx.beginPath();
      ctx.moveTo(padL, gy);
      ctx.lineTo(w - padR, gy);
      ctx.stroke();
      ctx.fillText(String(v), padL - 7, gy + 5);
    }
    // 空腹上限虚线
    var fHi = Y(targets.fasting[1]);
    ctx.strokeStyle = 'rgba(11,125,94,0.65)';
    ctx.setLineDash([5, 5]);
    ctx.beginPath();
    ctx.moveTo(padL, fHi);
    ctx.lineTo(w - padR, fHi);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.fillStyle = C.COLOR.primary;
    ctx.textAlign = 'end';
    ctx.fillText('空腹上限 ' + targets.fasting[1].toFixed(1), w - padR, fHi - 6);
  }

  // 日均值折线
  var means = S.dailyMeans(list);
  if (means.length > 1) {
    ctx.strokeStyle = C.COLOR.primary;
    ctx.lineWidth = 2.5;
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.beginPath();
    means.forEach(function (m, i) {
      var px = X(m.date.getTime());
      var py = Y(m.avg);
      if (i === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py);
    });
    ctx.stroke();
  }

  // 逐条记录着色数据点
  var r = mini ? 4 : (opts.days > 30 ? 3.5 : 5);
  list.forEach(function (rec) {
    var st = S.statusOf(rec.value, C.periodById(rec.period).tgt, targets);
    var px = X(rec.measuredAt);
    var py = Y(rec.value);
    ctx.beginPath();
    ctx.arc(px, py, r, 0, Math.PI * 2);
    ctx.fillStyle = statusColor(st);
    ctx.fill();
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 1.5;
    ctx.stroke();
    points.push({ x: px, y: py, r: r, record: rec });
  });

  // x 轴日期标签
  if (!mini && list.length) {
    var ticks = Math.min(opts.days > 60 ? 6 : 4, list.length);
    ctx.font = '13px sans-serif';
    ctx.fillStyle = C.COLOR.label;
    ctx.textAlign = 'center';
    for (var i = 0; i < ticks; i++) {
      var idx = Math.floor(i * (list.length - 1) / Math.max(1, ticks - 1));
      var d = new Date(list[idx].measuredAt);
      ctx.fillText((d.getMonth() + 1) + '/' + d.getDate(), X(list[idx].measuredAt), h - 8);
    }
  }

  return { points: points };
}

/* 命中判定：找点击位置附近的数据点（容差 26px，适老易点中） */
function pickPoint(points, x, y) {
  var best = null;
  var bestD = 26 * 26;
  points.forEach(function (p) {
    var dx = p.x - x;
    var dy = p.y - y;
    var d = dx * dx + dy * dy;
    if (d <= bestD) { bestD = d; best = p; }
  });
  return best;
}

module.exports = {
  drawTrend: drawTrend,
  pickPoint: pickPoint,
  statusColor: statusColor
};
