/* 全局常量：时段体系、默认目标、取值范围、状态色（适老化加深版） */

var PERIODS = [
  { id: 'fasting',        label: '空腹',   tgt: 'fasting',    win: [4, 9],   due: 9.5 },
  { id: 'afterBreakfast', label: '早餐后', tgt: 'nonFasting', win: [9, 11],  due: 11 },
  { id: 'beforeLunch',    label: '午餐前', tgt: 'nonFasting', win: [11, 13], due: 13 },
  { id: 'afterLunch',     label: '午餐后', tgt: 'nonFasting', win: [13, 16], due: 16 },
  { id: 'beforeDinner',   label: '晚餐前', tgt: 'nonFasting', win: [16, 19], due: 19 },
  { id: 'afterDinner',    label: '晚餐后', tgt: 'nonFasting', win: [19, 22], due: 22 },
  { id: 'bedtime',        label: '睡前',   tgt: 'nonFasting', win: [22, 24], due: 24 },
  { id: 'random',         label: '随机',   tgt: 'nonFasting', win: [0, 4],   due: 25 }
];

var DEFAULT_TARGETS = { fasting: [4.4, 7.0], nonFasting: [4.4, 10.0] };

var VALUE_MIN = 1.0;
var VALUE_MAX = 33.3;

var ST_TEXT = { ok: '达标', low: '偏低', high: '偏高' };

/* 颜色+文字双编码用的加深色，正文对比度 ≥7:1 */
var COLOR = {
  ink: '#191d23',
  sub: '#49505e',
  primary: '#0b7d5e',
  ok: '#0e9c73',
  okInk: '#0a5c44',
  low: '#b45309',
  lowInk: '#8a4106',
  high: '#c53030',
  highInk: '#a02020',
  grid: '#eef1f5',
  label: '#6a7280'
};

function periodById(id) {
  for (var i = 0; i < PERIODS.length; i++) {
    if (PERIODS[i].id === id) return PERIODS[i];
  }
  return PERIODS[7]; // 兜底：随机
}

module.exports = {
  PERIODS: PERIODS,
  DEFAULT_TARGETS: DEFAULT_TARGETS,
  VALUE_MIN: VALUE_MIN,
  VALUE_MAX: VALUE_MAX,
  ST_TEXT: ST_TEXT,
  COLOR: COLOR,
  periodById: periodById
};
