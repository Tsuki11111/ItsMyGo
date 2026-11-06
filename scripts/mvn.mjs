#!/usr/bin/env node
/**
 * 跨平台 Maven Wrapper 转发：仓库根的 npm 脚本用它跑 Maven。
 *
 * 为什么需要：Windows 下 `./mvnw` 不可执行、要 `mvnw.cmd`；Linux/macOS 反之。
 * npm 的脚本 shell 又随平台变化，直接在 package.json 里写死哪一个都不通用。
 * 这里按平台选 wrapper，参数原样透传（例如 `-Dmaven.test.skip=true`）。
 *
 * 用法：node scripts/mvn.mjs <maven 参数...>
 */

import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const isWindows = process.platform === 'win32'
const wrapper = join(ROOT, isWindows ? 'mvnw.cmd' : 'mvnw')

if (!existsSync(wrapper)) {
  console.error(`找不到 Maven Wrapper：${wrapper}`)
  process.exit(1)
}

const args = process.argv.slice(2)
const child = isWindows
  ? spawn(process.env.ComSpec || 'cmd.exe', ['/d', '/c', wrapper, ...args], {
      cwd: ROOT,
      stdio: 'inherit',
    })
  : spawn(wrapper, args, { cwd: ROOT, stdio: 'inherit' })

child.on('error', (error) => {
  console.error('无法启动 Maven：', error.message)
  process.exit(1)
})
child.on('exit', (code, signal) => {
  if (signal) {
    console.error(`Maven 被信号终止：${signal}`)
    process.exit(1)
  }
  process.exit(code ?? 1)
})
