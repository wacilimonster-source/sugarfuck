#!/usr/bin/env node
/**
 * 糖记 · 一键发布脚本
 * 用法: node tools/release.mjs 2.4 "本次更新说明"
 *
 * 流程: ① 自增 versionCode + 写入 versionName
 *       ② gradlew assembleRelease
 *       ③ 拷贝 APK 到 releases/sugarfather-<版本>.apk
 *       ④ 重写根目录 version.json（App 靠它检查更新）
 *       ⑤ git add/commit/push（推送到 GitHub）
 *
 * 之后手机上的糖记启动时会发现新版本，点一下即可下载安装。
 */
import { readFileSync, writeFileSync, copyFileSync, mkdirSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const REPO = 'wacilimonster-source/sugarfuck';
const BRANCH = 'main';

const [, , version, ...noteParts] = process.argv;
if (!version || !/^\d+\.\d+(\.\d+)?$/.test(version)) {
  console.error('用法: node tools/release.mjs 2.4 "本次更新说明"');
  process.exit(1);
}
const notes = noteParts.join(' ').trim() || `更新到 V${version}`;

// ① 版本号
const gradlePath = join(ROOT, 'android', 'app', 'build.gradle.kts');
let g = readFileSync(gradlePath, 'utf8');
const m = g.match(/versionCode = (\d+)/);
if (!m) { console.error('找不到 versionCode'); process.exit(1); }
const nextCode = Number(m[1]) + 1;
g = g.replace(/versionCode = \d+/, `versionCode = ${nextCode}`)
     .replace(/versionName = "[\d.]+"/, `versionName = "${version}"`);
writeFileSync(gradlePath, g);
console.log(`✓ 版本 → V${version} (versionCode ${nextCode})`);

// ② 构建
const JAVA_HOME = process.env.JAVA_HOME || 'C:\\Program Files\\Android\\Android Studio\\jbr';
console.log('… 正在构建 release APK');
execFileSync('cmd', ['/c', 'gradlew.bat', 'assembleRelease'], {
  cwd: join(ROOT, 'android'),
  stdio: 'inherit',
  env: { ...process.env, JAVA_HOME },
});

// ③ 拷贝 APK
const apk = join(ROOT, 'android', 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
if (!existsSync(apk)) { console.error('构建产物不存在: ' + apk); process.exit(1); }
mkdirSync(join(ROOT, 'releases'), { recursive: true });
const target = join(ROOT, 'releases', `sugarfather-${version}.apk`);
copyFileSync(apk, target);
console.log(`✓ APK → releases/sugarfather-${version}.apk`);

// ④ version.json
// 国内访问 GitHub 大文件受限，默认用加速代理作为主通道，官方地址作镜像。
// 换代理只需改这里（或事后手改 version.json），无需重新打包 App。
const PROXY = process.env.SF_PROXY ?? 'https://ghfast.top/';
const rawUrl = `https://raw.githubusercontent.com/${REPO}/${BRANCH}/releases/sugarfather-${version}.apk`;
const manifest = {
  versionCode: nextCode,
  versionName: version,
  apkUrl: PROXY + rawUrl,
  apkUrlMirror: rawUrl,
  notes,
};
writeFileSync(join(ROOT, 'version.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log('✓ version.json 已更新（主通道: ' + PROXY + '）');

// ⑤ 提交推送
execFileSync('git', ['add', '-A'], { cwd: ROOT, stdio: 'inherit' });
execFileSync('git', ['commit', '-m', `发布 V${version}：${notes}`], { cwd: ROOT, stdio: 'inherit' });
execFileSync('git', ['push'], { cwd: ROOT, stdio: 'inherit' });
console.log(`\n✓ 已发布 V${version}，手机端糖记下次启动会发现更新`);
