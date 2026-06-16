#!/usr/bin/env node
// Cross-platform Maven Wrapper launcher for npm scripts: cmd.exe needs `mvnw.cmd`, while
// POSIX shells need `./mvnw`. Forwards its arguments and the exit code, so it is a drop-in
// stand-in for the wrapper inside `npm run` commands (e.g. `node scripts/mvnw.mjs spring-boot:run`).
import { spawn } from 'node:child_process'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const projectRoot = join(dirname(fileURLToPath(import.meta.url)), '..')
const onWindows = process.platform === 'win32'
const args = process.argv.slice(2)

// Use the wrapper's absolute path so resolution never depends on the current directory, and run
// it from the project root so Maven finds the POM. On Windows a .cmd must go through cmd.exe.
const child = onWindows
  ? spawn('cmd.exe', ['/d', '/s', '/c', join(projectRoot, 'mvnw.cmd'), ...args], {
      cwd: projectRoot,
      stdio: 'inherit',
    })
  : spawn(join(projectRoot, 'mvnw'), args, { cwd: projectRoot, stdio: 'inherit' })

const forward = (signal) => () => child.kill(signal)
process.on('SIGINT', forward('SIGINT'))
process.on('SIGTERM', forward('SIGTERM'))

child.on('exit', (code, signal) => process.exit(signal ? 1 : (code ?? 0)))
