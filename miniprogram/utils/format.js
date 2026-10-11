/* 日期与文本格式化（全部本地时区） */

var WEEK = ['日', '一', '二', '三', '四', '五', '六'];

function pad(n) { return (n < 10 ? '0' : '') + n; }

function dayKey(d) {
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
}

function fmtMD(d) { return (d.getMonth() + 1) + '月' + d.getDate() + '日'; }

function fmtHM(d) { return pad(d.getHours()) + ':' + pad(d.getMinutes()); }

function fmtDT(d) { return fmtMD(d) + ' ' + fmtHM(d); }

function fmtDayLabel(d) { return fmtMD(d) + ' 周' + WEEK[d.getDay()]; }

/* picker 用 YYYY-MM-DD */
function fmtDateInput(d) { return dayKey(d); }

function fmtTimeInput(d) { return pad(d.getHours()) + ':' + pad(d.getMinutes()); }

module.exports = {
  WEEK: WEEK,
  pad: pad,
  dayKey: dayKey,
  fmtMD: fmtMD,
  fmtHM: fmtHM,
  fmtDT: fmtDT,
  fmtDayLabel: fmtDayLabel,
  fmtDateInput: fmtDateInput,
  fmtTimeInput: fmtTimeInput
};
