/* 统计计算：纯函数，显式传入 targets / now，便于 Node 自测 */

var C = require('./constants');
var F = require('./format');

function statusOf(value, tgtKey, targets) {
  var t = (targets && targets[tgtKey]) || C.DEFAULT_TARGETS.nonFasting;
  if (value < t[0]) return 'low';
  if (value > t[1]) return 'high';
  return 'ok';
}

function recordsInRange(records, days, now) {
  now = now || new Date();
  var start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - (days - 1));
  return records.filter(function (r) { return r.measuredAt >= start.getTime(); });
}

function statOf(list, targets) {
  if (!list.length) return null;
  var sum = 0, ok = 0, max = list[0], min = list[0];
  list.forEach(function (r) {
    sum += r.value;
    if (statusOf(r.value, C.periodById(r.period).tgt, targets) === 'ok') ok++;
    if (r.value > max.value) max = r;
    if (r.value < min.value) min = r;
  });
  return { avg: sum / list.length, tir: ok / list.length, count: list.length, max: max, min: min };
}

function periodAverages(list) {
  return C.PERIODS.map(function (p) {
    var arr = list.filter(function (r) { return r.period === p.id; });
    if (!arr.length) return { p: p, avg: null, count: 0 };
    var s = 0;
    arr.forEach(function (r) { s += r.value; });
    return { p: p, avg: s / arr.length, count: arr.length };
  });
}

function dailyMeans(list) {
  var byDay = {};
  list.forEach(function (r) {
    var k = F.dayKey(new Date(r.measuredAt));
    (byDay[k] = byDay[k] || []).push(r.value);
  });
  return Object.keys(byDay).sort().map(function (k) {
    var arr = byDay[k];
    var s = 0;
    arr.forEach(function (v) { s += v; });
    var d = new Date(k.replace(/-/g, '/'));
    d.setHours(12, 0, 0, 0);
    return { key: k, date: d, avg: s / arr.length };
  });
}

/* 今天没记时从昨天起算，不因“今天还没测”清零 */
function streakDays(records, now) {
  now = now || new Date();
  var set = {};
  records.forEach(function (r) { set[F.dayKey(new Date(r.measuredAt))] = 1; });
  var d = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  var n = 0;
  if (!set[F.dayKey(d)]) d.setDate(d.getDate() - 1);
  while (set[F.dayKey(d)]) { n++; d.setDate(d.getDate() - 1); }
  return n;
}

/* 已过截止点且今日无记录的时段 */
function missingPeriods(records, now) {
  now = now || new Date();
  var h = now.getHours() + now.getMinutes() / 60;
  var today = F.dayKey(now);
  var have = {};
  records.forEach(function (r) {
    if (F.dayKey(new Date(r.measuredAt)) === today) have[r.period] = 1;
  });
  var miss = [];
  C.PERIODS.forEach(function (p) {
    if (p.id !== 'random' && h >= p.due && !have[p.id]) miss.push(p);
  });
  return miss;
}

/* 按录入时刻所在时间窗推荐 */
function recommendPeriod(now) {
  now = now || new Date();
  var h = now.getHours() + now.getMinutes() / 60;
  for (var i = 0; i < C.PERIODS.length; i++) {
    var p = C.PERIODS[i];
    if (h >= p.win[0] && h < p.win[1]) return p.id;
  }
  return 'random';
}

/* 两步录入第 1 步的数值校验（PRD FR-1：1.0–33.3，最多 1 位小数） */
function validateValue(str) {
  var s = String(str == null ? '' : str).trim();
  if (!s) return { ok: false, value: NaN, msg: '请先输入血糖数值' };
  if (!/^\d{1,2}(\.\d)?$/.test(s)) {
    return { ok: false, value: NaN, msg: '格式不对，最多 1 位小数' };
  }
  var v = parseFloat(s);
  if (isNaN(v) || v < C.VALUE_MIN || v > C.VALUE_MAX) {
    return { ok: false, value: v, msg: '数值要在 ' + C.VALUE_MIN.toFixed(1) + ' 到 ' + C.VALUE_MAX.toFixed(1) + ' 之间' };
  }
  return { ok: true, value: Math.round(v * 10) / 10, msg: '' };
}

module.exports = {
  statusOf: statusOf,
  recordsInRange: recordsInRange,
  statOf: statOf,
  periodAverages: periodAverages,
  dailyMeans: dailyMeans,
  streakDays: streakDays,
  missingPeriods: missingPeriods,
  recommendPeriod: recommendPeriod,
  validateValue: validateValue
};
