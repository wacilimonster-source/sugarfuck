const store = require('./utils/store');

App({
  onLaunch() {
    store.init();
    // 断网期间录入的记录进待同步队列，联网后自动补传
    wx.onNetworkStatusChange((res) => {
      if (res.isConnected) store.flush();
    });
    store.sync().catch(() => {});
  }
});
