var store = require('../../utils/store');
var S = require('../../utils/stats');
var C = require('../../utils/constants');
var F = require('../../utils/format');
var chart = require('../../utils/chart');
var rows = require('../../utils/rows');

Page({
  data: {
    offline: false,
    dateText: '',
    streakText: '',
    remind: null,
    hero: null,
    todayList: [],
    hasToday: false,
    todayCount: '',
    hasSpark: false
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 0 });
    }
    this.render();
    this.checkNet();
    var that = this;
    store.sync().then(function (r) { if (r.changed) that.render(); }).catch(function () {});
  },

  /* PRD 4.5 离线态：顶部细条提示，数据走本地缓存 + 联网补传 */
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
    var now = new Date();

    var miss = S.missingPeriods(records, now);
    var streak = S.streakDays(records, now);
    var remind;
    if (records.length && !miss.length) {
      remind = { cls: 'ok', icon: '✓', title: '今天的时段都记完啦', desc: '已经连续记录 ' + streak + ' 天，真棒' };
    } else if (records.length) {
      var labels = miss.map(function (p) { return p.label; }).join('、');
      remind = { cls: '', icon: '⏰', title: '还有 ' + miss.length + ' 个时段没记', desc: labels + ' —— 测完点下面的 ＋ 记一笔' };
    } else {
      remind = { cls: '', icon: '👋', title: '欢迎使用糖记', desc: '测完血糖，点下面的 ＋ 记第一笔' };
    }

    var hero = null;
    if (records.length) {
      var latest = records[records.length - 1];
      var p = C.periodById(latest.period);
      var st = S.statusOf(latest.value, p.tgt, targets);
      var t = targets[p.tgt];
      hero = {
        id: latest.id,
        timeText: '最近一次 · ' + F.fmtDT(new Date(latest.measuredAt)),
        value: latest.value.toFixed(1),
        periodLabel: p.label,
        st: st,
        stText: C.ST_TEXT[st],
        targetText: '（' + p.label + '目标 ' + t[0].toFixed(1) + '–' + t[1].toFixed(1) + '）',
        note: latest.note || ''
      };
    }

    var todayKey = F.dayKey(now);
    var todays = records
      .filter(function (r) { return F.dayKey(new Date(r.measuredAt)) === todayKey; })
      .reverse()
      .map(function (r) { return rows.buildRow(r, targets); });

    this.setData({
      dateText: F.fmtDayLabel(now),
      streakText: '🔥 连续 ' + streak + ' 天',
      remind: remind,
      hero: hero,
      todayList: todays,
      hasToday: todays.length > 0,
      todayCount: todays.length ? '共 ' + todays.length + ' 条' : ''
    });

    this.drawSpark(records, targets);
  },

  drawSpark(records, targets) {
    var list = S.recordsInRange(records, 7);
    var that = this;
    if (!list.length) {
      this.setData({ hasSpark: false });
      return;
    }
    wx.createSelectorQuery().in(this)
      .select('#spark-canvas')
      .fields({ node: true, size: true })
      .exec(function (res) {
        if (!res || !res[0] || !res[0].node) return;
        var node = res[0].node;
        var width = res[0].width;
        var height = res[0].height;
        var info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
        var dpr = info.pixelRatio || 2;
        node.width = width * dpr;
        node.height = height * dpr;
        var ctx = node.getContext('2d');
        ctx.scale(dpr, dpr);
        chart.drawTrend(ctx, width, height, { list: list, targets: targets, mini: true });
        that.setData({ hasSpark: S.dailyMeans(list).length >= 2 });
      });
  },

  goTrend() { wx.switchTab({ url: '/pages/trend/trend' }); },

  tapRecord(e) {
    var id = e.currentTarget.dataset.id;
    if (id) wx.navigateTo({ url: '/pages/edit/edit?id=' + id });
  }
});
