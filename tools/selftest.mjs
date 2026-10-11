/* 糖记 · 自测套件（Node 运行，不需要微信开发者工具）
   覆盖：JS 语法 / JSON 合法性 / 静态一致性 / 统计纯函数 / 数据层（本地 + 云队列） */
import { createRequire } from 'node:module';
import { readdirSync, statSync, existsSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { execFileSync } from 'node:child_process';

const require = createRequire(import.meta.url);
const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const MP = join(ROOT, 'miniprogram');

let pass = 0, fail = 0;
const failures = [];
function ok(cond, name) {
  if (cond) { pass++; }
  else { fail++; failures.push(name); console.error('  ✗ ' + name); }
}
function eq(a, b, name) {
  ok(JSON.stringify(a) === JSON.stringify(b), name + ' (got ' + JSON.stringify(a) + ', want ' + JSON.stringify(b) + ')');
}
function section(t) { console.log('\n== ' + t + ' =='); }

/* ============ 1. JS 语法检查 ============ */
section('JS 语法（node --check）');
function walk(dir, out) {
  for (const f of readdirSync(dir)) {
    const p = join(dir, f);
    if (statSync(p).isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
}
const allFiles = walk(MP, []);
const jsFiles = allFiles.filter(f => f.endsWith('.js'));
for (const f of jsFiles) {
  try {
    execFileSync(process.execPath, ['--check', f], { stdio: 'pipe' });
    ok(true, '语法 ' + basename(f));
  } catch (e) {
    ok(false, '语法 ' + f + ' → ' + e.stderr.toString().slice(0, 200));
  }
}

/* ============ 2. JSON 合法性 ============ */
section('JSON 合法性');
const jsonFiles = allFiles.filter(f => f.endsWith('.json'));
const jsons = {};
for (const f of jsonFiles) {
  try {
    jsons[f] = JSON.parse(require('node:fs').readFileSync(f, 'utf8'));
    ok(true, 'JSON ' + basename(f));
  } catch (e) {
    ok(false, 'JSON ' + f + ' → ' + e.message);
  }
}

/* ============ 3. 静态一致性 ============ */
section('静态一致性（pages / tabBar / 组件 / 花括号 / WXML 标签）');
const appJson = jsons[join(MP, 'app.json')];
ok(!!appJson, 'app.json 可解析');
if (appJson) {
  for (const p of appJson.pages) {
    for (const ext of ['.js', '.wxml', '.json', '.wxss']) {
      ok(existsSync(join(MP, p + ext)), '存在 ' + p + ext);
    }
  }
  ok(appJson.tabBar && appJson.tabBar.custom === true, 'tabBar.custom = true');
  for (const t of appJson.tabBar.list) {
    ok(appJson.pages.includes(t.pagePath), 'tabBar 项已注册 ' + t.pagePath);
  }
}
for (const ext of ['.js', '.wxml', '.json', '.wxss']) {
  ok(existsSync(join(MP, 'custom-tab-bar', 'index' + ext)), '存在 custom-tab-bar/index' + ext);
}
for (const f of allFiles.filter(f => f.endsWith('.wxss'))) {
  const src = require('node:fs').readFileSync(f, 'utf8');
  const open = (src.match(/{/g) || []).length, close = (src.match(/}/g) || []).length;
  ok(open === close, '花括号平衡 ' + basename(f) + ' (' + open + '/' + close + ')');
}
for (const f of allFiles.filter(f => f.endsWith('.wxml'))) {
  const src = require('node:fs').readFileSync(f, 'utf8');
  let balanced = true, detail = '';
  for (const tag of ['view', 'block', 'button', 'text', 'picker', 'canvas', 'input']) {
    const open = (src.match(new RegExp('<' + tag + '(\\s|>|/)', 'g')) || []).length;
    const close = (src.match(new RegExp('</' + tag + '>', 'g')) || []).length;
    const selfClose = (src.match(new RegExp('<' + tag + '[^>]*/>', 'g')) || []).length;
    if (open - selfClose !== close) { balanced = false; detail += ` ${tag}:${open - selfClose}/${close}`; }
  }
  ok(balanced, 'WXML 标签平衡 ' + basename(f) + detail);
}
/* require 相对路径可解析 */
for (const f of jsFiles) {
  const src = require('node:fs').readFileSync(f, 'utf8');
  const re = /require\('(\.[^']+)'\)/g;
  let m;
  while ((m = re.exec(src))) {
    const target = join(dirname(f), m[1]);
    const hit = existsSync(target) || existsSync(target + '.js');
    ok(hit, 'require 可解析 ' + basename(f) + ' → ' + m[1]);
  }
}

/* ============ 4. wx mock + 工具层 ============ */
function makeWx(opts = {}) {
  const mem = new Map();
  const cloudDocs = new Map();
  let settingsDoc = null;
  const api = {
    __mem: mem, __cloudDocs: cloudDocs,
    set online(v) { api.__online = v; },
    get online() { return api.__online; },
    setStorageSync(k, v) { mem.set(k, JSON.parse(JSON.stringify(v))); },
    getStorageSync(k) { return mem.has(k) ? JSON.parse(JSON.stringify(mem.get(k))) : ''; },
    removeStorageSync(k) { mem.delete(k); },
    getNetworkType(o) { setTimeout(() => o.success({ networkType: api.__online ? 'wifi' : 'none' }), 0); },
    onNetworkStatusChange() {},
    cloud: {
      init() { if (opts.cloudInitThrows) throw new Error('cloud init fail'); },
      database() {
        return {
          collection(name) {
            if (name === 'settings') {
              return {
                limit() {
                  return { get: () => Promise.resolve({ data: settingsDoc ? [JSON.parse(JSON.stringify(settingsDoc))] : [] }) };
                },
                doc(id) {
                  return {
                    update(o) {
                      if (opts.failCloud) return Promise.reject(new Error('cloud fail'));
                      settingsDoc = Object.assign({ _id: id }, settingsDoc, JSON.parse(JSON.stringify(o.data)));
                      return Promise.resolve({});
                    }
                  };
                },
                add(o) {
                  if (opts.failCloud) return Promise.reject(new Error('cloud fail'));
                  settingsDoc = Object.assign({ _id: 'settings-1' }, JSON.parse(JSON.stringify(o.data)));
                  return Promise.resolve({ _id: 'settings-1' });
                }
              };
            }
            return {
              add(o) {
                if (opts.failCloud || api.__failAdd) return Promise.reject(new Error('cloud fail'));
                const d = JSON.parse(JSON.stringify(o.data));
                if (cloudDocs.has(d._id)) return Promise.reject(new Error('document.exists'));
                cloudDocs.set(d._id, d);
                return Promise.resolve({ _id: d._id });
              },
              doc(id) {
                return {
                  update(o) {
                    if (opts.failCloud) return Promise.reject(new Error('cloud fail'));
                    if (!cloudDocs.has(id)) return Promise.reject(new Error('document.get:fail data not exist'));
                    Object.assign(cloudDocs.get(id), JSON.parse(JSON.stringify(o.data)));
                    return Promise.resolve({});
                  },
                  remove() {
                    if (opts.failCloud) return Promise.reject(new Error('cloud fail'));
                    if (!cloudDocs.has(id)) return Promise.reject(new Error('document not exist'));
                    cloudDocs.delete(id);
                    return Promise.resolve({});
                  }
                };
              },
              orderBy() {
                return {
                  skip(n) {
                    return {
                      limit(n2) {
                        return {
                          get() {
                            if (opts.failCloud) return Promise.reject(new Error('cloud fail'));
                            const arr = [...cloudDocs.values()]
                              .sort((a, b) => a.measuredAt - b.measuredAt)
                              .slice(n, n + n2)
                              .map(d => JSON.parse(JSON.stringify(d)));
                            return Promise.resolve({ data: arr });
                          }
                        };
                      }
                    };
                  }
                };
              }
            };
          }
        };
      }
    }
  };
  api.__online = opts.online !== false;
  api.__failAdd = !!opts.failAdd;
  return api;
}
function settle(ms = 40) { return new Promise(r => setTimeout(r, ms)); }

const settleP = settle;
globalThis.wx = makeWx();
const C = require(join(MP, 'utils', 'constants.js'));
const S = require(join(MP, 'utils', 'stats.js'));
const F = require(join(MP, 'utils', 'format.js'));
const chart = require(join(MP, 'utils', 'chart.js'));
const store = require(join(MP, 'utils', 'store.js'));

/* ---- 4.1 常量 ---- */
section('常量');
eq(C.PERIODS.length, 8, '8 个时段');
ok(new Set(C.PERIODS.map(p => p.id)).size === 8, '时段 id 唯一');
eq(C.periodById('nope').id, 'random', 'periodById 兜底为随机');
eq(C.periodById('fasting').tgt, 'fasting', '空腹用空腹目标');

/* ---- 4.2 状态判定边界 ---- */
section('状态判定边界（PRD FR-3）');
const T = C.DEFAULT_TARGETS;
eq(S.statusOf(4.3, 'fasting', T), 'low', '空腹 4.3 → 偏低');
eq(S.statusOf(4.4, 'fasting', T), 'ok', '空腹 4.4 → 达标');
eq(S.statusOf(7.0, 'fasting', T), 'ok', '空腹 7.0 → 达标');
eq(S.statusOf(7.1, 'fasting', T), 'high', '空腹 7.1 → 偏高');
eq(S.statusOf(10.0, 'nonFasting', T), 'ok', '非空腹 10.0 → 达标');
eq(S.statusOf(10.1, 'nonFasting', T), 'high', '非空腹 10.1 → 偏高');
eq(S.statusOf(4.39, 'nonFasting', T), 'low', '非空腹 4.39 → 偏低');

/* ---- 4.3 数值校验（两步录入第 1 步） ---- */
section('数值校验 validateValue');
ok(S.validateValue('6.5').ok && S.validateValue('6.5').value === 6.5, '6.5 合法');
ok(S.validateValue('6').ok && S.validateValue('6').value === 6, '6 合法');
ok(!S.validateValue('').ok, '空串拒绝');
ok(!S.validateValue('6.55').ok, '两位小数拒绝');
ok(!S.validateValue('0.5').ok, '低于 1.0 拒绝');
ok(!S.validateValue('34').ok, '高于 33.3 拒绝');
ok(!S.validateValue('abc').ok, '非数字拒绝');
ok(S.validateValue('33.3').ok, '上边界 33.3 合法');
ok(S.validateValue('1.0').ok, '下边界 1.0 合法');

/* ---- 4.4 时段推荐 ---- */
section('时段推荐 recommendPeriod');
function at(h, m) { const d = new Date(2026, 8, 27); d.setHours(h, m, 0, 0); return d; }
eq(S.recommendPeriod(at(2, 0)), 'random', '02:00 → 随机');
eq(S.recommendPeriod(at(6, 30)), 'fasting', '06:30 → 空腹');
eq(S.recommendPeriod(at(9, 30)), 'afterBreakfast', '09:30 → 早餐后');
eq(S.recommendPeriod(at(12, 0)), 'beforeLunch', '12:00 → 午餐前');
eq(S.recommendPeriod(at(16, 29)), 'beforeDinner', '16:29 → 晚餐前');
eq(S.recommendPeriod(at(22, 30)), 'bedtime', '22:30 → 睡前');

/* ---- 4.5 软提醒 missingPeriods ---- */
section('软提醒 missingPeriods（PRD FR-5）');
const day0 = F.dayKey(at(8, 0));
function recToday(period, h) {
  const d = at(h, 0);
  return { id: 'x' + period, value: 6.0, period, note: '', measuredAt: d.getTime(), createdAt: d.getTime(), updatedAt: d.getTime() };
}
const rFasting7 = recToday('fasting', 7);
eq(S.missingPeriods([rFasting7], at(10, 0)).map(p => p.id), [], '10:00 仅记空腹 → 无提醒（早餐后未到截止点）');
eq(S.missingPeriods([rFasting7], at(11, 30)).map(p => p.id), ['afterBreakfast'], '11:30 → 提醒早餐后');
eq(S.missingPeriods([rFasting7], at(16, 30)).map(p => p.id), ['afterBreakfast', 'beforeLunch', 'afterLunch'], '16:30 → 提醒 3 个时段');
eq(S.missingPeriods([rFasting7, recToday('afterBreakfast', 9), recToday('beforeLunch', 12), recToday('afterLunch', 14), recToday('beforeDinner', 17), recToday('afterDinner', 20), recToday('bedtime', 22)], at(23, 0)).length, 0, '全记完 → 无提醒');
eq(S.missingPeriods([Object.assign({}, rFasting7, { measuredAt: at(7, 0).getTime() - 86400000 })], at(10, 0)).map(p => p.id), ['fasting'], '昨天的空腹不算今天');

/* ---- 4.6 连续天数 ---- */
section('连续天数 streakDays');
const now = at(20, 0);
function recDaysAgo(days, h = 8) {
  const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - days, h, 0);
  return { id: 'd' + days + '-' + h, value: 6, period: 'random', note: '', measuredAt: d.getTime(), createdAt: d.getTime(), updatedAt: d.getTime() };
}
eq(S.streakDays([recDaysAgo(1), recDaysAgo(2)], now), 2, '今天没记 → 从昨天起算 2 天');
eq(S.streakDays([recDaysAgo(0), recDaysAgo(1), recDaysAgo(2)], now), 3, '今天记了 → 3 天');
eq(S.streakDays([recDaysAgo(0), recDaysAgo(2)], now), 1, '昨天断档 → 1 天');
eq(S.streakDays([], now), 0, '空数据 → 0 天');

/* ---- 4.7 统计 ---- */
section('统计 statOf / periodAverages / dailyMeans / recordsInRange');
const list3 = [
  { value: 5.0, period: 'random', measuredAt: at(8, 0).getTime() },
  { value: 15.0, period: 'afterDinner', measuredAt: at(20, 0).getTime() },
  { value: 3.0, period: 'fasting', measuredAt: at(7, 0).getTime() }
];
const st = S.statOf(list3, T);
ok(Math.abs(st.avg - 23 / 3) < 1e-9, '平均 = 23/3');
eq(Math.round(st.tir * 100), 33, '达标比例 33%（仅 3.0 空腹达标）');
eq(st.max.value, 15.0, '最高 15.0');
eq(st.min.value, 3.0, '最低 3.0');
eq(S.statOf([], T), null, '空列表 → null');
const pa = S.periodAverages(list3);
eq(pa.find(x => x.p.id === 'fasting').count, 1, ' fasting 计数 1');
eq(pa.find(x => x.p.id === 'beforeLunch').avg, null, '无数据时段 avg=null');
eq(S.dailyMeans(list3).length, 1, '同一天 → 1 个日均值');
const twoDays = list3.concat([{ value: 8, period: 'random', measuredAt: at(8, 0).getTime() - 8 * 86400000 }]);
eq(S.dailyMeans(twoDays).length, 2, '两天 → 2 个日均值');
const in7 = S.recordsInRange(twoDays, 7, now);
eq(in7.length, 3, '近7天不含 8 天前的记录');
eq(S.recordsInRange(twoDays, 90, now).length, 4, '近90天含全部 4 条');

/* ---- 4.8 图表几何 ---- */
section('图表 chart.drawTrend / pickPoint');
{
  const calls = { fillRect: 0, arc: 0 };
  const ctx = new Proxy({}, {
    get(t, k) {
      if (k === 'fillRect') return () => { calls.fillRect++; };
      if (k === 'arc') return () => { calls.arc++; };
      if (k === 'measureText') return () => ({ width: 10 });
      return () => {};
    },
    set() { return true; }
  });
  const out = chart.drawTrend(ctx, 320, 250, { list: twoDays, targets: T, days: 7 });
  eq(out.points.length, 4, '绘制 4 个数据点');
  eq(calls.arc, 4, 'arc 调用 4 次');
  const hit = chart.pickPoint(out.points, out.points[0].x, out.points[0].y);
  ok(hit && hit.record.value === 5.0, '命中最近点');
  ok(chart.pickPoint(out.points, -100, -100) === null, '远离处不命中');
  const mini = chart.drawTrend(ctx, 320, 84, { list: twoDays, targets: T, mini: true });
  eq(mini.points.length, 4, 'mini 模式同样 4 点');
}

/* ---- 4.9 数据层 · 本地模式 ---- */
section('数据层 · 本地模式（增删改查 + 持久化）');
globalThis.wx = makeWx();
store._testReset({ cloudOk: false });
ok(store.init() === false, '未配置云环境 → 本地模式');
const r1 = store.addRecord({ value: 6.5, period: 'fasting', note: '测试', measuredAt: at(7, 0).getTime() });
const r2 = store.addRecord({ value: 9.1, period: 'afterBreakfast', note: '', measuredAt: at(9, 30).getTime() });
eq(store.getRecords().length, 2, '两条记录');
eq(store.getRecords()[0].id, r1.id, '按时间升序');
ok(store.getStatus().cloud === false, '状态 = 本地');
store.updateRecord(r1.id, { value: 5.8, note: '改过' });
eq(store.getRecords().find(r => r.id === r1.id).value, 5.8, '更新生效');
eq(store.getRecords().find(r => r.id === r1.id).note, '改过', '备注更新');
eq(store.deleteRecord(r2.id), 1, '删除 1 条');
eq(store.getRecords().length, 1, '剩 1 条');
/* 持久化：重新 init 从 storage 恢复 */
const reloaded = store.init();
eq(store.getRecords().length, 1, '重启后记录仍在');
eq(store.getRecords()[0].value, 5.8, '重启后数值正确');
/* 目标范围 */
store.saveTargets({ fasting: [4.0, 8.0], nonFasting: [4.0, 11.0] });
eq(store.getTargets().fasting, [4.0, 8.0], '目标保存');
eq(store.getTargets().fasting, [4, 8], '保存后取回一致');

/* ---- 4.10 数据层 · 云模式（队列冲刷 / 离线暂存 / 幂等迁移） ---- */
section('数据层 · 云模式（离线队列与同步）');
{
  const mock = makeWx({ online: true });
  globalThis.wx = mock;
  store._testReset({ cloudOk: true });
  const a = store.addRecord({ value: 7.2, period: 'fasting', note: '', measuredAt: at(7, 0).getTime() });
  await store.flush();
  ok(mock.__cloudDocs.has(a.id), 'add 冲刷到云端');
  eq(store.getStatus().pending, 0, '队列清空');
  /* 云端 _id = 本地 id（幂等迁移的前提） */
  eq([...mock.__cloudDocs.values()][0]._id, a.id, '云端 _id 复用本地 id');
  /* 离线暂存 */
  mock.online = false;
  const b = store.addRecord({ value: 12.0, period: 'afterDinner', note: '', measuredAt: at(19, 30).getTime() });
  await store.flush();
  eq(store.getStatus().pending, 1, '离线 → 队列保留 1 条');
  ok(!mock.__cloudDocs.has(b.id), '离线写入未上云');
  eq(store.getRecords().length, 2, '本地缓存仍有 2 条（不丢数据）');
  /* 恢复联网 → 自动补传 */
  mock.online = true;
  const fr = await store.flush();
  eq(fr.done, 1, '恢复联网补传 1 条');
  eq(store.getStatus().pending, 0, '队列再次清空');
  ok(mock.__cloudDocs.has(b.id), '暂存记录已上云');
  /* update / delete 同步 */
  store.updateRecord(a.id, { value: 7.5 });
  await store.flush();
  eq(mock.__cloudDocs.get(a.id).value, 7.5, '云端 update 生效');
  store.deleteRecord(a.id);
  await store.flush();
  ok(!mock.__cloudDocs.has(a.id), '云端 delete 生效');
  /* 云端缺失时的 update 转新建 */
  const ghost = store.addRecord({ value: 5.0, period: 'random', note: '', measuredAt: at(6, 0).getTime() });
  mock.__cloudDocs.delete(ghost.id);
  store.updateRecord(ghost.id, { value: 5.2 });
  await store.flush();
  ok(mock.__cloudDocs.has(ghost.id) && mock.__cloudDocs.get(ghost.id).value === 5.2, '云端缺失 → update 转新建');
  /* settings upsert */
  store.saveTargets({ fasting: [4.0, 8.0], nonFasting: [4.0, 11.0] });
  await store.flush();
  ok(JSON.stringify(store.getTargets().fasting) === JSON.stringify([4, 8]), 'settings 保存后取回');
  /* 云端故障：队列不丢 */
  const failMock = makeWx({ online: true, failCloud: true });
  globalThis.wx = failMock;
  store._testReset({ cloudOk: true });
  store.addRecord({ value: 6.6, period: 'random', note: '', measuredAt: at(8, 0).getTime() });
  await store.flush();
  eq(store.getStatus().pending, 1, '云端故障 → 记录留在队列（不丢）');
}

/* ---- 4.11 迁移与拉取（sync） ---- */section('数据层 · 迁移与拉取 sync()');
{
  const mock = makeWx({ online: true });
  globalThis.wx = mock;
  /* 本地已有 2 条历史 → 首次连通自动迁移 */
  store._testReset({
    cloudOk: true,
    records: [
      { id: 'm1', value: 5.5, period: 'fasting', note: '', measuredAt: at(7, 0).getTime(), createdAt: 1, updatedAt: 1 },
      { id: 'm2', value: 8.8, period: 'afterDinner', note: '', measuredAt: at(20, 0).getTime(), createdAt: 2, updatedAt: 2 }
    ]
  });
  const res1 = await store.sync();
  ok(res1.changed === true || res1.changed === false, 'sync 返回 changed 标志');
  eq(mock.__cloudDocs.size, 2, '本地 2 条已迁移上云');
  eq(store.getRecords().length, 2, '迁移后本地仍 2 条');
  /* 重复 sync 不产生重复数据（_id 幂等） */
  await store.sync();
  await store.sync();
  eq(mock.__cloudDocs.size, 2, '重复 sync 不产生重复');
  /* 云端新增记录 → 拉取为权威 */
  mock.__cloudDocs.set('remote-1', { _id: 'remote-1', value: 4.9, period: 'fasting', note: '', measuredAt: at(6, 0).getTime(), createdAt: 3, updatedAt: 3 });
  await store.sync();
  eq(store.getRecords().length, 3, '云端新记录被拉取');
  eq(store.getRecords()[0].id, 'remote-1', '按时间升序排列');
  /* 断网 sync 不抛错（离线兜底） */
  mock.online = false;
  const off = await store.sync();
  ok('offline' in off || off.changed === false, '断网 sync 优雅返回');
}

/* ---- 4.11b 同步安全：队列未清空时不得整表覆盖缓存（优化回归） ---- */
section('数据层 · 同步安全（合并而非覆盖）');
{
  const mock = makeWx({ online: true, failAdd: true });
  globalThis.wx = mock;
  mock.__cloudDocs.set('remote-9', { _id: 'remote-9', value: 4.9, period: 'fasting', note: '', measuredAt: at(6, 0).getTime(), createdAt: 1, updatedAt: 1 });
  store._testReset({ cloudOk: true });
  const r = store.addRecord({ value: 7.2, period: 'fasting', note: '', measuredAt: at(7, 0).getTime() });
  await store.flush();
  eq(store.getStatus().pending, 1, 'add 失败 → 队列保留 1 条');
  await store.sync(); // flush 仍失败 → pull 成功 → 走合并路径
  eq(store.getRecords().length, 2, '缓存 = 本地未同步 + 云端记录');
  ok(store.getRecords().some(x => x.id === r.id), '本地未同步记录不丢');
  ok(store.getRecords().some(x => x.id === 'remote-9'), '云端记录并入缓存');
  eq(store.getStatus().pending, 1, '队列仍保留');
  mock.__failAdd = false;
  const fr = await store.flush();
  eq(fr.done, 1, '恢复后补传 1 条');
  ok(mock.__cloudDocs.has(r.id), '未同步记录已上云');
  await store.sync();
  eq(store.getRecords().length, 2, '整表同步后一致');
  eq(store.getStatus().pending, 0, '队列清空');
}

/* ---- 4.11c 缺省 measuredAt 兜底 ---- */
section('数据层 · addRecord 缺省时间戳');
{
  globalThis.wx = makeWx();
  store._testReset({ cloudOk: false });
  const r = store.addRecord({ value: 5.5, period: 'random', note: '' });
  ok(typeof r.measuredAt === 'number' && r.measuredAt > 0, '缺省 measuredAt 自动补当前时间');
}

/* ---- 4.12 导出文本 ---- */
section('导出文本 exportText（PRD FR-7）');
{
  const mock = makeWx();
  globalThis.wx = mock;
  store._testReset({ cloudOk: false });
  store.addRecord({ value: 6.5, period: 'fasting', note: '走了一万步', measuredAt: at(7, 0).getTime() });
  store.addRecord({ value: 9.1, period: 'afterBreakfast', note: '', measuredAt: at(9, 30).getTime() });
  const text = store.exportText(at(12, 0));
  ok(text.indexOf('【糖记】血糖记录导出') === 0, '标题行');
  ok(text.indexOf('共 2 条') >= 0, '条数行');
  ok(text.indexOf('空腹\t6.5\t达标\t走了一万步') >= 0, '记录行含时段/数值/判定/备注');
  ok(text.indexOf('平均 7.8') >= 0, '汇总平均 7.8');
  ok(text.indexOf('达标比例 100%') >= 0, '汇总达标比例');
  ok(text.indexOf('目标：空腹 4.4–7.0') >= 0, '目标行');
  ok(text.indexOf('仅供参考') >= 0, '免责行');
  const emptyText = (function () { store._testReset({ cloudOk: false }); return store.exportText(at(12, 0)); })();
  ok(emptyText.indexOf('暂无记录') >= 0, '空数据导出不崩溃');
}

/* ---- 4.13 适老化静态抽查（PRD 第五章） ---- */
section('适老化静态抽查');
{
  const fs = require('node:fs');
  const editWxss = fs.readFileSync(join(MP, 'pages', 'edit', 'edit.wxss'), 'utf8');
  ok(/height:\s*128rpx/.test(editWxss), '键盘键高 128rpx (64px)');
  ok(/padding:\s*38rpx 0/.test(editWxss) && /font-size:\s*42rpx/.test(editWxss), '主按钮 88px 级别');
  const appWxss = fs.readFileSync(join(MP, 'app.wxss'), 'utf8');
  ok(/font-size:\s*36rpx/.test(appWxss), '全局基准字号 36rpx (18px)');
  const tabWxss = fs.readFileSync(join(MP, 'custom-tab-bar', 'index.wxss'), 'utf8');
  ok(/136rpx/.test(tabWxss), '中央＋按钮 68px');
}

/* ============ 汇总 ============ */
console.log('\n========================================');
console.log('通过 ' + pass + ' 项' + (fail ? '，失败 ' + fail + ' 项' : '，全部通过 ✓'));
if (fail) {
  console.log('失败清单：');
  failures.forEach(f => console.log('  - ' + f));
  process.exit(1);
}
