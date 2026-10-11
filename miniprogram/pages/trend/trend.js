var store = require('../../utils/store');
var S = require('../../utils/stats');
var C = require('../../utils/constants');
var F = require('../../utils/format');
var chart = require('../../utils/chart');
var rows = require('../../utils/rows');

Page({
  data: {
    offline: false,
    days: 7,
    chartSub: '',
    detailText: '点图上的圆点，看那一次的详情',
    hasChart: false,
    statCards: [],
    statSub: '',
    paRows: [],
    historyGroups: [],
    hasHistory: false,
    canExpand: false,
    expanded: false,
    expandText: '展开更多记录 ▾'
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 1 });
    }
    this.render();
    this.checkNet();
    var that = this;
    store.sync().then(function (r) { if (r.changed) that.render(); }).catch(function () {});
  },

  /* PRD 4.5 离线态 */
  checkNet() {
    var that = this;
    if (!wx.getNetworkType) return;
    wx.getNetworkType({
      success(res) { that.setData({ offline: res.networkType === 'none' }); }
    });
  },

  render() {
    var records = store.getRecords();
    var targets = store.getTargets();
    var days = this.data.days;
    var list = S.recordsInRange(records, days);
    var s = S.statOf(list, targets);

    var patch = {
      chartSub: '近' + days + '天 · ' + list.length + ' 次',
      hasChart: list.length > 0
    };

    if (!s) {
      patch.statCards = [];
      patch.statSub = '';
      patch.paRows = [];
      patch.historyGroups = [];
      patch.hasHistory = false;
      patch.canExpand = false;
      this.setData(patch);
      return;
    }

    patch.statCards = [
      { v: s.avg.toFixed(1), u: ' mmol/L', v2: '', l: '平均血糖' },
      { v: '' + Math.round(s.tir * 100), u: ' %', v2: '', l: '达标比例' },
      { v: s.max.value.toFixed(1), v2: ' / ' + s.min.value.toFixed(1), u: ' mmol/L', l: '最高 / 最低' }
    ];
    patch.statSub = '共 ' + s.count + ' 次记录 · 连续记录 ' + S.streakDays(records) + ' 天 · 达标比例 = 在目标范围内的次数占比';

    var pas = S.periodAverages(list);
    var maxAvg = 0;
    pas.forEach(function (x) { if (x.avg > maxAvg) maxAvg = x.avg; });
    var cap = Math.max(10, Math.ceil(maxAvg));
    patch.paRows = pas.map(function (x) {
      if (x.avg === null) {
        return { label: x.p.label, empty: true, countText: '还没记过' };
      }
      var st = S.statusOf(x.avg, x.p.tgt, targets);
      return {
        label: x.p.label,
        empty: false,
        pct: Math.min(100, x.avg / cap * 100).toFixed(0),
        color: chart.statusColor(st),
        avgText: x.avg.toFixed(1),
        countText: '(' + x.count + '次)'
      };
    });

    var byDay = {};
    records.slice().reverse().forEach(function (r) {
      var k = F.dayKey(new Date(r.measuredAt));
      (byDay[k] = byDay[k] || []).push(r);
    });
    var keys = Object.keys(byDay).sort().reverse();
    var limit = this.data.expanded ? keys.length : Math.min(7, keys.length);
    var groups = [];
    for (var i = 0; i < limit; i++) {
      var d = new Date(keys[i].replace(/-/g, '/'));
      groups.push({
        label: F.fmtDayLabel(d),
        rows: byDay[keys[i]].map(function (r) { return rows.buildRow(r, targets); })
      });
    }
    patch.historyGroups = groups;
    patch.hasHistory = keys.length > 0;
    patch.canExpand = keys.length > 7;
    patch.expandText = this.data.expanded ? '收起 ▴' : '展开更多记录 ▾';

    this.setData(patch);
    this.drawChart(list, targets);
  },

  drawChart(list, targets) {
    var that = this;
    wx.createSelectorQuery().in(this)
      .select('#trend-canvas')
      .fields({ node: true, size: true, rect: true })
      .exec(function (res) {
        if (!res || !res[0] || !res[0].node) return;
        var node = res[0].node;
        var width = res[0].width;
        var height = res[0].height;
        that._rect = res[0];
        var info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
        var dpr = info.pixelRatio || 2;
        node.width = width * dpr;
        node.height = height * dpr;
        var ctx = node.getContext('2d');
        ctx.scale(dpr, dpr);
        var out = chart.drawTrend(ctx, width, height, {
          list: list, targets: targets, days: that.data.days
        });
        that._points = out.points;
      });
  },

  onChartTap(e) {
    if (!this._points || !this._points.length || !this._rect) return;
    var t = e.changedTouches && e.changedTouches[0];
    if (!t) return;
    var x = t.clientX - this._rect.left;
    var y = t.clientY - this._rect.top;
    var hit = chart.pickPoint(this._points, x, y);
    if (!hit) return;
    var rec = hit.record;
    var p = C.periodById(rec.period);
    var st = S.statusOf(rec.value, p.tgt, store.getTargets());
    var d = new Date(rec.measuredAt);
    this.setData({
      detailText: F.fmtDT(d) + ' · ' + p.label + ' · ' + rec.value.toFixed(1)
        + ' mmol/L ' + C.ST_TEXT[st] + (rec.note ? ' · ' + rec.note : '')
    });
  },

  onSeg(e) {
    var days = Number(e.currentTarget.dataset.days);
    if (days === this.data.days) return;
    this.setData({ days: days });
    this.render();
  },

  onExpand() {
    this.setData({ expanded: !this.data.expanded });
    this.render();
  },

  tapRecord(e) {
    var id = e.currentTarget.dataset.id;
    if (id) wx.navigateTo({ url: '/pages/edit/edit?id=' + id });
  }
});
