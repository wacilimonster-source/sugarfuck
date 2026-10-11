var store = require('../../utils/store');
var C = require('../../utils/constants');
var config = require('../../config');

Page({
  data: {
    cloudText: '',
    pending: 0,
    editorOpen: false,
    fLo: '4.4',
    fHi: '7.0',
    nLo: '4.4',
    nHi: '10.0',
    version: config.version
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 2 });
    }
    var st = store.getStatus();
    var cloudText = st.cloud ? '● 云端同步已开启' : '● 本地模式（数据存本机）';
    if (st.cloud && st.pending > 0) {
      cloudText = '● 有 ' + st.pending + ' 条待同步，联网后自动上传';
    }
    this.setData({ cloudText: cloudText, pending: st.pending });
    this.loadTargets();
  },

  loadTargets() {
    var t = store.getTargets();
    this.setData({
      fLo: t.fasting[0].toFixed(1),
      fHi: t.fasting[1].toFixed(1),
      nLo: t.nonFasting[0].toFixed(1),
      nHi: t.nonFasting[1].toFixed(1)
    });
  },

  toggleEditor() {
    this.setData({ editorOpen: !this.data.editorOpen });
  },

  onInput(e) {
    var patch = {};
    patch[e.currentTarget.dataset.k] = e.detail.value;
    this.setData(patch);
  },

  saveTargets() {
    var d = this.data;
    var t = {
      fasting: [parseFloat(d.fLo), parseFloat(d.fHi)],
      nonFasting: [parseFloat(d.nLo), parseFloat(d.nHi)]
    };
    var all = [t.fasting[0], t.fasting[1], t.nonFasting[0], t.nonFasting[1]];
    for (var i = 0; i < 4; i++) {
      if (isNaN(all[i]) || all[i] < 1 || all[i] > 25) {
        wx.showToast({ title: '目标值要在 1.0 到 25.0 之间', icon: 'none', duration: 3000 });
        return;
      }
    }
    if (t.fasting[0] >= t.fasting[1] || t.nonFasting[0] >= t.nonFasting[1]) {
      wx.showToast({ title: '下限要比上限小', icon: 'none', duration: 3000 });
      return;
    }
    store.saveTargets(t);
    this.loadTargets();
    this.setData({ editorOpen: false });
    wx.showToast({ title: '目标范围改好了，颜色和统计已更新', icon: 'none', duration: 3000 });
  },

  resetTargets() {
    store.saveTargets(JSON.parse(JSON.stringify(C.DEFAULT_TARGETS)));
    this.loadTargets();
    wx.showToast({ title: '已恢复默认目标', icon: 'none', duration: 3000 });
  },

  exportData() {
    var text = store.exportText();
    var n = store.getRecords().length;
    wx.setClipboardData({
      data: text,
      success() {
        wx.showToast({ title: '已复制 ' + n + ' 条记录', icon: 'none', duration: 3000 });
      }
    });
  }
});
