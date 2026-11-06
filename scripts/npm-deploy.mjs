#!/usr/bin/env node
/**
 * ItsMyGo 部署入口（npm 只做转发，真正的逻辑在 deploy.sh / deploy.ps1）。
 *
 * 用法：
 *   npm run deploy                   启动全栈（等同 ./deploy.sh）
 *   npm run deploy -- down           停止（保留数据卷）
 *   npm run deploy -- logs           跟踪后端日志
 *   npm run deploy -- status         查看容器状态
 *   npm run deploy -- seed           重新导入 44 个演示商品
 *   npm run deploy -- reset          停止并删除所有数据卷（需输入 yes）
 *   npm run deploy -- up --no-seed   启动但不导入演示数据
 *
 * 也提供快捷脚本：deploy:up / deploy:down / deploy:logs / deploy:status / deploy:seed / deploy:reset / deploy:help
 *
 * 为什么要 node 而不是直接把命令写进 package.json：
 * Windows 下 npm 走 cmd/powershell、Linux/macOS 走 sh，直接写 `./deploy.sh` 两边不通用；
 * 这里按平台选脚本，并把 `--no-seed` 映射成 PowerShell 的 `-NoSeed`。
 */
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const COMMANDS = new Set(['up', 'down', 'logs', 'status', 'seed', 'reset', 'help'])

const USAGE = `
ItsMyGo 部署入口（转发到 deploy.sh / deploy.ps1）

  npm run deploy                   启动全栈（首次自动生成 .env / 管理员 / 演示数据）
  npm run deploy -- down           停止（保留数据卷）
  npm run deploy -- logs           跟踪后端日志
  npm run deploy -- status         查看容器状态
  npm run deploy -- seed           重新导入 44 个演示商品
  npm run deploy -- reset          停止并删除所有数据卷（需输入 yes）
  npm run deploy -- up --no-seed   启动但不导入演示数据

等价快捷脚本：npm run deploy:up / deploy:down / deploy:logs / deploy:status / deploy:seed / deploy:reset / deploy:help
`.trim()

const raw = process.argv.slice(2)
const wantsHelp = raw.includes('-h') || raw.includes('--help')

let command = 'up'
let args = raw
if (!wantsHelp && raw.length > 0 && !raw[0].startsWith('-')) {
  command = raw[0]
  args = raw.slice(1)
}
if (wantsHelp) {
  command = 'help'
}

if (!COMMANDS.has(command)) {
  console.error(`未知命令：${command}\n`)
  console.error(USAGE)
  process.exit(1)
}

const noSeed = args.includes('--no-seed') || args.includes('-NoSeed')
// deploy.ps1 只认识 -NoSeed，其余透传参数仅 bash 版支持
const extra = args.filter((arg) => arg !== '--no-seed' && arg !== '-NoSeed')

const isWindows = process.platform === 'win32'
const script = join(ROOT, isWindows ? 'deploy.ps1' : 'deploy.sh')
if (!existsSync(script)) {
  console.error(`找不到部署脚本：${script}`)
  process.exit(1)
}
if (isWindows && extra.length > 0) {
  console.warn(`[warn] Windows 下的 deploy.ps1 不支持额外参数，已忽略：${extra.join(' ')}`)
}

const child = isWindows
  ? spawn(
      'powershell.exe',
      [
        '-NoProfile',
        '-ExecutionPolicy',
        'Bypass',
        '-File',
        script,
        command,
        ...(noSeed ? ['-NoSeed'] : []),
      ],
      { cwd: ROOT, stdio: 'inherit' },
    )
  : spawn('bash', [script, command, ...(noSeed ? ['--no-seed'] : []), ...extra], {
      cwd: ROOT,
      stdio: 'inherit',
    })

child.on('error', (error) => {
  console.error('无法启动部署脚本：', error.message)
  process.exit(1)
})
child.on('exit', (code, signal) => {
  if (signal) {
    console.error(`部署脚本被信号终止：${signal}`)
    process.exit(1)
  }
  process.exit(code ?? 1)
})
