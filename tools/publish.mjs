/* 糖记 · 发布调试工具（微信官方 miniprogram-ci 命令行封装）
 *
 * 用法:
 *   node tools/publish.mjs set-appid <wx AppID>                 一次性写入 project.config.json
 *   node tools/publish.mjs preview  [--appid X --key 路径]       生成真机预览二维码 preview-qr.png
 *   node tools/publish.mjs upload   [--version 1.1.0]           上传开发版（后台可设体验版）
 *
 * 密钥获取: mp.weixin.qq.com → 开发管理 → 开发设置 → 小程序代码上传 → 生成代码上传密钥
 * 密钥放置: 保存为项目根目录 private.<appid>.key 或 private.key
 */
import { createRequire } from 'node:module';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const ci = require('miniprogram-ci');
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const MP = join(ROOT, 'miniprogram');

function parseArgs(argv) {
  const args = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith('--')) args[a.slice(2)] = argv[++i];
    else args._.push(a);
  }
  return args;
}

function readAppid(args) {
  let appid = args.appid;
  if (!appid) {
    const cfg = JSON.parse(readFileSync(join(MP, 'project.config.json'), 'utf8'));
    appid = cfg.appid;
  }
  if (!appid || appid === 'touristappid') {
    console.error('✗ 还没有真实 AppID。先执行: node tools/publish.mjs set-appid wx你的appid');
    process.exit(1);
  }
  return appid;
}

function keyPathFor(appid, args) {
  if (args.key) return resolve(args.key);
  const candidates = [join(ROOT, `private.${appid}.key`), join(ROOT, 'private.key')];
  for (const c of candidates) {
    if (existsSync(c)) return c;
  }
  console.error([
    '✗ 找不到代码上传密钥。请按以下步骤获取并放置：',
    '  1. 登录 mp.weixin.qq.com → 开发管理 → 开发设置 → 小程序代码上传',
    '  2. 点「生成代码上传密钥」，把下载的文件保存为: ' + join(ROOT, `private.${appid}.key`),
    '  3. 同一页面把「IP 白名单」设为关闭（家庭宽带 IP 会变，关闭最省事；',
    '     也可把出口 IP 加入白名单: 2408:8214:c1c:31a0:580a:d90b:d33b:40a5）',
    '  ⚠ 密钥等同上传权限，不要发给任何人/传到公开仓库；用完可在后台「重置」作废',
  ].join('\n'));
  process.exit(1);
}

function makeProject(appid, keyPath) {
  return new ci.Project({
    appid,
    type: 'miniProgram',
    projectPath: MP,
    privateKeyPath: keyPath,
    ignores: ['node_modules/**/*']
  });
}

const [cmd, ...rest] = process.argv.slice(2);
const args = parseArgs(rest);

try {
  if (cmd === 'set-appid') {
    const appid = args._[0];
    if (!appid || !/^wx[0-9a-f]{16}$/i.test(appid)) {
      console.error('✗ 用法: node tools/publish.mjs set-appid wx1234567890abcdef');
      process.exit(1);
    }
    const p = join(MP, 'project.config.json');
    const cfg = JSON.parse(readFileSync(p, 'utf8'));
    cfg.appid = appid;
    writeFileSync(p, JSON.stringify(cfg, null, 2) + '\n');
    console.log('✓ project.config.json appid →', appid);
    console.log('  下一步: 把上传密钥存为 ' + join(ROOT, `private.${appid}.key`) + '，然后跑 preview');
  } else if (cmd === 'preview') {
    const appid = readAppid(args);
    const keyPath = keyPathFor(appid, args);
    const project = makeProject(appid, keyPath);
    const dest = join(ROOT, 'preview-qr.png');
    const result = await ci.preview({
      project,
      desc: '真机调试 ' + new Date().toLocaleString('zh-CN'),
      setting: { es6: true, minified: true },
      qrcodeFormat: 'image',
      qrcodeOutputDest: dest,
      onProgressUpdate() {}
    });
    console.log('✓ 预览二维码已生成: ' + dest);
    console.log('  用你的微信扫码即可在手机上打开调试（仅你自己可见，二维码约 25 分钟有效，过期重跑本命令）');
    const info = (result && result.subPackageInfo) || [];
    if (info.length) console.log('  包大小: ' + JSON.stringify(info));
  } else if (cmd === 'upload') {
    const appid = readAppid(args);
    const keyPath = keyPathFor(appid, args);
    const project = makeProject(appid, keyPath);
    const version = args.version || '1.1.0';
    await ci.upload({
      project,
      version,
      desc: args.desc || '适老化血糖记录 V1.1',
      setting: { es6: true, minified: true },
      onProgressUpdate() {}
    });
    console.log('✓ 已上传开发版 ' + version);
    console.log('  到 mp.weixin.qq.com → 版本管理 → 开发版本 可设为「体验版」（需管理员在后台操作）');
  } else {
    console.log([
      '糖记 · 发布调试工具（miniprogram-ci ' + require('miniprogram-ci/package.json').version + '）',
      '',
      '用法:',
      '  node tools/publish.mjs set-appid <wx AppID>      一次性写入 project.config.json',
      '  node tools/publish.mjs preview [--appid X --key 路径]   生成真机预览二维码 preview-qr.png',
      '  node tools/publish.mjs upload  [--version 1.1.0]       上传开发版（后台可设体验版）',
      '',
      '首次使用三步: set-appid → 放置密钥文件 → preview'
    ].join('\n'));
  }
} catch (e) {
  console.error('✗ 失败: ' + (e.message || e));
  const msg = String((e && e.message) || e);
  if (msg.indexOf('IP') >= 0 || msg.indexOf('61010') >= 0 || msg.indexOf('whitelist') >= 0) {
    console.error('  提示: 到 mp 后台「开发设置 → 小程序代码上传 → IP 白名单」暂时关闭后重试');
  }
  if (msg.indexOf('invalid appid') >= 0 || msg.indexOf('40013') >= 0) {
    console.error('  提示: AppID 不对，检查 set-appid 的值与密钥是否配套');
  }
  process.exit(1);
}
