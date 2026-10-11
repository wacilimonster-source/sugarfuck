/* 自定义 TabBar：今天 / 趋势 / 中央＋ / 我的
   中央凸起＋不在 tabBar.list 内，点击 navigateTo 录入页 */
Component({
  data: {
    selected: 0
  },
  methods: {
    tapTab(e) {
      var idx = Number(e.currentTarget.dataset.i);
      var path = e.currentTarget.dataset.path;
      if (idx === this.data.selected) return;
      wx.switchTab({ url: '/' + path });
    },
    tapPlus() {
      wx.navigateTo({ url: '/pages/edit/edit' });
    }
  }
});
