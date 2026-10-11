/* 列表行视图模型（今天页 / 趋势历史共用） */

var C = require('./constants');
var S = require('./stats');
var F = require('./format');

function buildRow(rec, targets) {
  var p = C.periodById(rec.period);
  var st = S.statusOf(rec.value, p.tgt, targets);
  return {
    id: rec.id,
    periodLabel: p.label,
    note: rec.note || '',
    timeText: F.fmtHM(new Date(rec.measuredAt)) + ' · ' + C.ST_TEXT[st],
    value: rec.value.toFixed(1),
    cls: st,
    color: st === 'ok' ? C.COLOR.ink : (st === 'low' ? C.COLOR.low : C.COLOR.high)
  };
}

module.exports = { buildRow: buildRow };
