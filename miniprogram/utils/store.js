/* 数据层：本地优先 + 云端同步（微信云开发）+ 离线队列
   - 未配置 cloudEnvId：纯本地模式（wx.storage），数据永不丢
   - 已配置：写入本地缓存 + 待同步队列，联网后冲刷到云数据库；
     sync() 拉取云端为权威源；首次连通时把本地历史一次性迁移上云
   所有方法在无 wx 环境（Node 自测）下可用，只需提供 wx mock */

var config = require('../config');
var C = require('./constants');
var F = require('./format');

var KEY_RECORDS = 'sf_records';
var KEY_TARGETS = 'sf_targets';
var KEY_QUEUE = 'sf_queue';
var KEY_MIGRATED = 'sf_migrated';

var state = {
  records: [],
  targets: null,
  queue: [],
  cloudOk: false,
  flushing: false,
  syncing: false
};

/* ---------- wx 基础封装 ---------- */
function stGet(key, fallback) {
  try {
    var v = wx.getStorageSync(key);
    return (v === '' || v == null) ? fallback : v;
  } catch (e) { return fallback; }
}
function stSet(key, val) {
  try { wx.setStorageSync(key, val); } catch (e) { /* 存储满等异常不阻断 */ }
}
function stRemove(key) {
  try { wx.removeStorageSync(key); } catch (e) { /* 忽略 */ }
}

/* ---------- 初始化 ---------- */
function init() {
  state.records = stGet(KEY_RECORDS, []);
  if (!Array.isArray(state.records)) state.records = [];
  state.records.sort(function (a, b) { return a.measuredAt - b.measuredAt; });
  state.targets = stGet(KEY_TARGETS, null) || JSON.parse(JSON.stringify(C.DEFAULT_TARGETS));
  state.queue = stGet(KEY_QUEUE, []);
  if (!Array.isArray(state.queue)) state.queue = [];

  if (config.cloudEnvId && typeof wx !== 'undefined' && wx.cloud) {
    try {
      wx.cloud.init({ env: config.cloudEnvId, traceUser: true });
      state.cloudOk = true;
    } catch (e) {
      state.cloudOk = false;
    }
  }
  return state.cloudOk;
}

function isCloud() { return state.cloudOk; }

function getStatus() {
  return { cloud: state.cloudOk, pending: state.queue.length };
}

/* ---------- 本地缓存 ---------- */
function persistRecords() { stSet(KEY_RECORDS, state.records); }
function persistTargets() { stSet(KEY_TARGETS, state.targets); }
function persistQueue() { stSet(KEY_QUEUE, state.queue); }

function genId() {
  return 'r' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}

function getRecords() { return state.records; }

function getTargets() {
  return state.targets || JSON.parse(JSON.stringify(C.DEFAULT_TARGETS));
}

/* ---------- 写操作：改缓存 → 落盘 → 入队 → 尽力冲刷 ---------- */
function addRecord(input) {
  var rec = {
    id: genId(),
    value: Math.round(input.value * 10) / 10,
    period: input.period,
    note: (input.note || '').slice(0, 200),
    measuredAt: input.measuredAt != null ? input.measuredAt : Date.now(),
    createdAt: Date.now(),
    updatedAt: Date.now()
  };
  state.records.push(rec);
  state.records.sort(function (a, b) { return a.measuredAt - b.measuredAt; });
  persistRecords();
  enqueue({ type: 'add', rec: rec });
  return rec;
}

function updateRecord(id, patch) {
  var rec = null;
  state.records = state.records.map(function (r) {
    if (r.id !== id) return r;
    rec = {
      id: r.id,
      value: patch.value != null ? Math.round(patch.value * 10) / 10 : r.value,
      period: patch.period || r.period,
      note: patch.note != null ? String(patch.note).slice(0, 200) : r.note,
      measuredAt: patch.measuredAt != null ? patch.measuredAt : r.measuredAt,
      createdAt: r.createdAt,
      updatedAt: Date.now()
    };
    return rec;
  });
  state.records.sort(function (a, b) { return a.measuredAt - b.measuredAt; });
  persistRecords();
  if (rec) enqueue({ type: 'update', rec: rec });
  return rec;
}

function deleteRecord(id) {
  var before = state.records.length;
  state.records = state.records.filter(function (r) { return r.id !== id; });
  persistRecords();
  if (state.records.length !== before) enqueue({ type: 'delete', id: id });
  return before - state.records.length;
}

function saveTargets(targets) {
  state.targets = {
    fasting: [Math.round(targets.fasting[0] * 10) / 10, Math.round(targets.fasting[1] * 10) / 10],
    nonFasting: [Math.round(targets.nonFasting[0] * 10) / 10, Math.round(targets.nonFasting[1] * 10) / 10]
  };
  persistTargets();
  enqueue({ type: 'settings', targets: state.targets });
}

/* ---------- 队列与云冲刷 ---------- */
function enqueue(op) {
  state.queue.push(op);
  persistQueue();
  flush();
}

function db() {
  return wx.cloud.database();
}

/* 安全规则限制仅本人可读写；设置集合每人一条，用查询后 upsert */
function applyOp(op) {
  var col = db().collection('records');
  if (op.type === 'add') {
    var data = {
      _id: op.rec.id,
      value: op.rec.value,
      period: op.rec.period,
      note: op.rec.note,
      measuredAt: op.rec.measuredAt,
      createdAt: op.rec.createdAt,
      updatedAt: op.rec.updatedAt
    };
    return col.add({ data: data }).catch(function (err) {
      // 幂等：迁移重试时 _id 已存在视为成功
      var msg = (err && err.errMsg) || String(err);
      if (msg.indexOf('exists') >= 0 || msg.indexOf('already') >= 0) return;
      throw err;
    });
  }
  if (op.type === 'update') {
    return col.doc(op.rec.id).update({
      data: {
        value: op.rec.value,
        period: op.rec.period,
        note: op.rec.note,
        measuredAt: op.rec.measuredAt,
        updatedAt: op.rec.updatedAt
      }
    }).catch(function (err) {
      var msg = (err && err.errMsg) || String(err);
      if (msg.indexOf('not exist') >= 0 || msg.indexOf('document.get:fail') >= 0) {
        // 云端缺失（如迁移前更新的记录）→ 转为整条新建
        return col.add({ data: {
          _id: op.rec.id,
          value: op.rec.value,
          period: op.rec.period,
          note: op.rec.note,
          measuredAt: op.rec.measuredAt,
          createdAt: op.rec.createdAt || Date.now(),
          updatedAt: op.rec.updatedAt
        } });
      }
      throw err;
    });
  }
  if (op.type === 'delete') {
    return col.doc(op.id).remove().catch(function (err) {
      var msg = (err && err.errMsg) || String(err);
      if (msg.indexOf('not exist') >= 0) return; // 已不存在 = 目标状态
      throw err;
    });
  }
  if (op.type === 'settings') {
    var sc = db().collection('settings');
    return sc.limit(1).get().then(function (res) {
      if (res.data && res.data.length) {
        return sc.doc(res.data[0]._id).update({ data: { targets: op.targets } });
      }
      return sc.add({ data: { targets: op.targets } });
    });
  }
  return Promise.resolve();
}

function netOnline() {
  return new Promise(function (resolve) {
    if (typeof wx === 'undefined' || !wx.getNetworkType) return resolve(true);
    wx.getNetworkType({
      success: function (res) { resolve(res.networkType !== 'none'); },
      fail: function () { resolve(true); }
    });
  });
}

function flush() {
  if (state.flushing) return state._inflight || Promise.resolve({ done: 0 });
  if (!state.cloudOk || !state.queue.length) return Promise.resolve({ done: 0 });
  state.flushing = true;
  var p = netOnline().then(function (online) {
    if (!online) return { done: 0 };
    var done = 0;
    function next() {
      if (!state.queue.length) return Promise.resolve();
      var op = state.queue[0];
      return applyOp(op).then(function () {
        state.queue.shift();
        persistQueue();
        done++;
        return next();
      }).catch(function () {
        // 失败保留队首，等下次联网重试；不阻断后续操作
      });
    }
    return next().then(function () { return { done: done }; });
  }).then(function (r) {
    state.flushing = false;
    state._inflight = null;
    return r;
  }, function (e) {
    state.flushing = false;
    state._inflight = null;
    throw e;
  });
  state._inflight = p;
  return p;
}

/* ---------- 云端拉取 + 首次迁移 ---------- */
function pullAll() {
  var col = db().collection('records');
  var all = [];
  function page(skip) {
    return col.orderBy('measuredAt', 'asc').skip(skip).limit(100).get()
      .then(function (res) {
        var data = (res && res.data) || [];
        all = all.concat(data);
        if (data.length < 100) return all;
        return page(skip + 100);
      });
  }
  return page(0).then(function (docs) {
    return docs.map(function (d) {
      return {
        id: d._id,
        value: d.value,
        period: d.period,
        note: d.note || '',
        measuredAt: d.measuredAt,
        createdAt: d.createdAt || d.measuredAt,
        updatedAt: d.updatedAt || d.measuredAt
      };
    });
  });
}

function sync() {
  if (state.syncing) return Promise.resolve({ changed: false });
  if (!state.cloudOk) return Promise.resolve({ changed: false, offline: true });
  state.syncing = true;
  return flush().then(function () {
    // 首次连通：把本地历史一次性迁移上云
    var migrate = Promise.resolve();
    if (!stGet(KEY_MIGRATED, false) && state.records.length) {
      migrate = Promise.all(state.records.map(function (r) {
        return applyOp({ type: 'add', rec: r }).catch(function () { /* 幂等，忽略 */ });
      })).then(function () {
        stSet(KEY_MIGRATED, true);
      });
    }
    return migrate;
  }).then(function () {
    return pullAll();
  }).then(function (remote) {
    remote.sort(function (a, b) { return a.measuredAt - b.measuredAt; });
    var changed = false;
    if (state.queue.length) {
      // 仍有待同步操作：不能整表覆盖缓存（会闪掉未同步的本地变更），
      // 只并入本地没有、且不在待删除队列里的云端记录
      var delIds = {};
      state.queue.forEach(function (op) { if (op.type === 'delete') delIds[op.id] = 1; });
      var known = {};
      state.records.forEach(function (r) { known[r.id] = 1; });
      remote.forEach(function (r) {
        if (!known[r.id] && !delIds[r.id]) { state.records.push(r); changed = true; }
      });
      if (changed) {
        state.records.sort(function (a, b) { return a.measuredAt - b.measuredAt; });
        persistRecords();
      }
    } else {
      changed = JSON.stringify(remote) !== JSON.stringify(state.records);
      if (changed) {
        state.records = remote;
        persistRecords();
      }
    }
    state.syncing = false;
    return { changed: changed };
  }).catch(function (e) {
    state.syncing = false;
    throw e;
  });
}

/* ---------- 导出文本（PRD FR-7） ---------- */
function exportText(now) {
  now = now || new Date();
  var stats = require('./stats');
  var targets = getTargets();
  var recs = state.records;
  var lines = ['【糖记】血糖记录导出', '导出时间 ' + F.fmtDT(now)];
  if (!recs.length) {
    lines.push('暂无记录');
    return lines.join('\n');
  }
  var first = F.dayKey(new Date(recs[0].measuredAt)).replace(/-/g, '/');
  var last = F.dayKey(new Date(recs[recs.length - 1].measuredAt)).replace(/-/g, '/');
  lines.push('共 ' + recs.length + ' 条 · ' + first + ' ~ ' + last, '---');
  recs.slice().reverse().forEach(function (r) {
    var p = C.periodById(r.period);
    var st = stats.statusOf(r.value, p.tgt, targets);
    var d = new Date(r.measuredAt);
    lines.push(F.dayKey(d).replace(/-/g, '/') + ' ' + F.fmtHM(d) + '\t' + p.label + '\t'
      + r.value.toFixed(1) + '\t' + C.ST_TEXT[st] + (r.note ? '\t' + r.note : ''));
  });
  var s = stats.statOf(recs, targets);
  lines.push('---');
  lines.push('平均 ' + s.avg.toFixed(1) + ' mmol/L ｜ 达标比例 ' + Math.round(s.tir * 100) + '% ｜ 最高 '
    + s.max.value.toFixed(1) + '（' + F.fmtMD(new Date(s.max.measuredAt)) + ' ' + C.periodById(s.max.period).label + '）｜ 最低 '
    + s.min.value.toFixed(1) + '（' + F.fmtMD(new Date(s.min.measuredAt)) + ' ' + C.periodById(s.min.period).label + '）');
  lines.push('目标：空腹 ' + targets.fasting[0].toFixed(1) + '–' + targets.fasting[1].toFixed(1)
    + ' · 非空腹 ' + targets.nonFasting[0].toFixed(1) + '–' + targets.nonFasting[1].toFixed(1) + ' mmol/L');
  lines.push('仅供参考，不能代替医生诊断');
  return lines.join('\n');
}

/* 测试钩子：注入 mock 状态（仅自测使用） */
function _testReset(options) {
  options = options || {};
  state.records = options.records || [];
  state.targets = options.targets || JSON.parse(JSON.stringify(C.DEFAULT_TARGETS));
  state.queue = [];
  state.cloudOk = !!options.cloudOk;
  state.flushing = false;
  state.syncing = false;
}

module.exports = {
  init: init,
  isCloud: isCloud,
  getStatus: getStatus,
  getRecords: getRecords,
  getTargets: getTargets,
  addRecord: addRecord,
  updateRecord: updateRecord,
  deleteRecord: deleteRecord,
  saveTargets: saveTargets,
  flush: flush,
  sync: sync,
  exportText: exportText,
  _testReset: _testReset
};
