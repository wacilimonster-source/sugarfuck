var store = require('../../utils/store');
var S = require('../../utils/stats');
var C = require('../../utils/constants');
var F = require('../../utils/format');

var KEYS = [
  { k: '1', l: '1' }, { k: '2', l: '2' }, { k: '3', l: '3' },
  { k: '4', l: '4' }, { k: '5', l: '5' }, { k: '6', l: '6' },
  { k: '7', l: '7' }, { k: '8', l: '8' }, { k: '9', l: '9' },
  { k: 'del', l: '⌫ 删除' }, { k: '0', l: '0' }, { k: 'dot', l: '·' }
];

Page({
  data: {
    step: 1,
    stepText: '第 1 步，共 2 步：输入数值',
    keys: KEYS,
    valDisplay: '0.0',
    valPlaceholder: true,
    hint: { cls: 'muted', text: '用下面的键盘输入血糖数值' },
    quickChips: [],
    nextEnabled: false,
    sumVal: '0.0',
    sumCls: 'ok',
    sumText: '',
    periods: [],
    saveEnabled: false,
    showMore: false,
    dateValue: '',
    timeValue: '',
    maxDate: '',
    timeText: '',
    note: '',
    isEdit: false
  },

  onLoad(options) {
    this._editingId = options.id || null;
    this._valStr = '';
    this._measuredAt = Date.now();
    this._selPeriod = null;
    this._recommendId = null;

    var rec = null;
    if (this._editingId) {
      var records = store.getRecords();
      for (var i = 0; i < records.length; i++) {
        if (records[i].id === this._editingId) rec = records[i];
      }
    }

    if (rec) {
      this._valStr = rec.value.toFixed(1);
      this._measuredAt = rec.measuredAt;
      this._selPeriod = rec.period;
      this.setData({ isEdit: true, note: rec.note || '' });
    } else if (this._editingId) {
      // 带着已失效的记录 id 进来（如已被删除）：明确告知并退出，不当成新记录
      wx.showToast({ title: '这条记录不存在或已删除', icon: 'none', duration: 3000 });
      setTimeout(function () { wx.navigateBack(); }, 600);
      return;
    } else {
      this._recommendId = S.recommendPeriod(new Date());
      this._selPeriod = null; // 进入第 2 步时预选推荐项
    }

    // 最近 3 个不同值作为快捷键
    var chips = [];
    store.getRecords().slice(-12).reverse().forEach(function (r) {
      if (chips.indexOf(r.value) < 0 && chips.length < 3) chips.push(r.value);
    });
    this.setData({
      quickChips: chips.map(function (v) { return { v: String(v), t: v.toFixed(1) }; })
    });

    this.refreshTimeInputs();
    this.buildPeriods();
    this.refreshStep1();
  },

  /* ---------- 第 1 步：数值 ---------- */
  refreshStep1() {
    var v = S.validateValue(this._valStr);
    var hint;
    if (this._valStr === '') {
      hint = { cls: 'muted', text: '用下面的键盘输入血糖数值' };
    } else if (!v.ok) {
      hint = { cls: 'bad', text: v.msg + '，请重新输入' };
    } else {
      hint = { cls: 'good', text: '输入好了，点「下一步」继续' };
    }
    this.setData({
      valDisplay: this._valStr === '' ? '0.0' : this._valStr,
      valPlaceholder: this._valStr === '',
      hint: hint,
      nextEnabled: v.ok
    });
  },

  onKey(e) {
    var k = e.currentTarget.dataset.k;
    if (k === 'del') {
      this._valStr = this._valStr.slice(0, -1);
    } else if (k === 'dot') {
      if (this._valStr.indexOf('.') < 0) {
        this._valStr = (this._valStr === '' ? '0' : this._valStr) + '.';
      }
    } else {
      var dot = this._valStr.indexOf('.');
      if (dot >= 0) {
        if (this._valStr.length - dot > 1) return; // 小数最多 1 位
      } else {
        if (this._valStr.length >= 2) return;      // 整数最多 2 位
      }
      this._valStr += k;
    }
    if (this._valStr.length > 1 && this._valStr[0] === '0' && this._valStr[1] !== '.') {
      this._valStr = String(parseInt(this._valStr, 10));
    }
    this.refreshStep1();
  },

  onQuick(e) {
    this._valStr = e.currentTarget.dataset.v;
    this.refreshStep1();
  },

  onNext() {
    var v = S.validateValue(this._valStr);
    if (!v.ok) return;
    if (!this._selPeriod) {
      this._selPeriod = this._recommendId || S.recommendPeriod(new Date());
    }
    this.buildPeriods();
    this.updateSum();
    this.setData({ step: 2, stepText: '第 2 步，共 2 步：选时段后保存' });
  },

  onBack() {
    this.setData({ step: 1, stepText: '第 1 步，共 2 步：输入数值' });
    this.refreshStep1();
  },

  /* ---------- 第 2 步：时段 + 保存 ---------- */
  buildPeriods() {
    var sel = this._selPeriod;
    var recId = this._recommendId;
    var self = this;
    this.setData({
      periods: C.PERIODS.map(function (p) {
        return {
          id: p.id,
          label: p.label,
          sel: p.id === sel,
          rec: !self._editingId && recId != null && p.id === recId
        };
      })
    });
  },

  updateSum() {
    var v = S.validateValue(this._valStr);
    if (!v.ok || !this._selPeriod) {
      this.setData({ saveEnabled: false, sumText: '' });
      return;
    }
    var p = C.periodById(this._selPeriod);
    var targets = store.getTargets();
    var st = S.statusOf(v.value, p.tgt, targets);
    var t = targets[p.tgt];
    this.setData({
      sumVal: v.value.toFixed(1),
      sumCls: st,
      sumText: C.ST_TEXT[st] + '（' + p.label + ' ' + t[0].toFixed(1) + '–' + t[1].toFixed(1) + '）',
      saveEnabled: true
    });
  },

  onPeriod(e) {
    this._selPeriod = e.currentTarget.dataset.pid;
    this.buildPeriods();
    this.updateSum();
  },

  /* ---------- 更多选项（改时间 / 备注） ---------- */
  toggleMore() {
    this.setData({ showMore: !this.data.showMore });
  },

  refreshTimeInputs() {
    var d = new Date(this._measuredAt);
    var now = new Date();
    this.setData({
      dateValue: F.fmtDateInput(d),
      timeValue: F.fmtTimeInput(d),
      maxDate: F.fmtDateInput(now),
      timeText: (F.dayKey(d) === F.dayKey(now) ? '今天' : F.fmtMD(d)) + ' ' + F.fmtHM(d)
    });
  },

  onDate(e) {
    var old = new Date(this._measuredAt);
    var nd = new Date(e.detail.value.replace(/-/g, '/'));
    this._measuredAt = new Date(nd.getFullYear(), nd.getMonth(), nd.getDate(),
      old.getHours(), old.getMinutes()).getTime();
    this.checkFuture();
    this.refreshTimeInputs();
  },

  onTime(e) {
    var parts = e.detail.value.split(':');
    var d = new Date(this._measuredAt);
    this._measuredAt = new Date(d.getFullYear(), d.getMonth(), d.getDate(),
      Number(parts[0]), Number(parts[1])).getTime();
    this.checkFuture();
    this.refreshTimeInputs();
  },

  checkFuture() {
    if (this._measuredAt > Date.now() + 60000) {
      wx.showToast({ title: '测量时间不能是未来的时间', icon: 'none', duration: 3000 });
      this._measuredAt = Date.now();
    }
  },

  onNote(e) {
    this.setData({ note: e.detail.value });
  },

  /* ---------- 保存 / 删除 ---------- */
  onSave() {
    var v = S.validateValue(this._valStr);
    if (!v.ok) {
      wx.showToast({ title: v.msg, icon: 'none', duration: 3000 });
      return;
    }
    if (!this._selPeriod) {
      wx.showToast({ title: '请选一个测量时段', icon: 'none', duration: 3000 });
      return;
    }
    if (this._measuredAt > Date.now() + 60000) {
      wx.showToast({ title: '测量时间不能是未来的时间', icon: 'none', duration: 3000 });
      return;
    }
    var payload = {
      value: v.value,
      period: this._selPeriod,
      note: this.data.note,
      measuredAt: this._measuredAt
    };
    if (this._editingId) store.updateRecord(this._editingId, payload);
    else store.addRecord(payload);
    wx.showToast({
      title: this._editingId ? '改好了 ✓' : '记好了，继续保持 ✓',
      icon: 'none',
      duration: 3000
    });
    setTimeout(function () { wx.navigateBack(); }, 600);
  },

  onDelete() {
    if (!this._editingId) return;
    var that = this;
    wx.showModal({
      title: '删除这条记录？',
      content: '删除后找不回来',
      confirmText: '删除',
      cancelText: '先留着',
      confirmColor: '#c53030',
      success(res) {
        if (!res.confirm) return;
        store.deleteRecord(that._editingId);
        wx.showToast({ title: '已删除', icon: 'none', duration: 3000 });
        setTimeout(function () { wx.navigateBack(); }, 600);
      }
    });
  }
});
