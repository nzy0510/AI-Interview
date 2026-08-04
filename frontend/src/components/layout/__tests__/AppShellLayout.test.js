import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import appShellSource from '../AppShell.vue?raw'

const globalStyleSource = readFileSync(resolve('src/style.css'), 'utf8')

describe('AppShell viewport layout', () => {
  it('keeps the navigation fixed while only the workspace scrolls', () => {
    expect(appShellSource).toMatch(/\.app-shell\s*{[^}]*height:\s*100(?:d)?vh[^}]*overflow:\s*hidden/s)
    expect(appShellSource).toMatch(/\.app-shell__main\s*{[^}]*min-height:\s*0[^}]*overflow:\s*hidden/s)
    expect(appShellSource).toMatch(/\.app-shell__content\s*{[^}]*min-height:\s*0[^}]*overflow-y:\s*auto/s)
    expect(appShellSource).toMatch(/\.app-shell__sidebar\s*{[^}]*min-height:\s*0[^}]*overflow-y:\s*auto/s)
    expect(globalStyleSource).toMatch(/body\s*{[^}]*min-height:\s*100dvh/s)
    expect(globalStyleSource).toMatch(/#app\s*{[^}]*min-height:\s*100dvh/s)
  })
})
