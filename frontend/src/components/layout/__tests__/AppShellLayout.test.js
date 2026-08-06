import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import appShellSource from '../AppShell.vue?raw'
import historySource from '../../../views/History.vue?raw'
import interviewSetupSource from '../../../views/InterviewSetup.vue?raw'
import llmProviderSettingsSource from '../../../views/LlmProviderSettings.vue?raw'
import mentorSource from '../../../views/Mentor.vue?raw'
import resumeSource from '../../../views/Resume.vue?raw'
import settingsSource from '../../../views/Settings.vue?raw'

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

  it.each([
    ['resume', resumeSource, 'page-header'],
    ['history', historySource, 'page-header'],
    ['interview setup', interviewSetupSource, 'setup-header'],
    ['mentor', mentorSource, 'mentor-header'],
    ['settings', settingsSource, 'settings-header'],
    ['llm provider settings', llmProviderSettingsSource, 'llm-header'],
  ])('lets the %s page header scroll with the workspace', (_name, source, className) => {
    const escapedClassName = className.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const headerRule = source.match(new RegExp(`\\.${escapedClassName}\\s*\\{([^}]*)\\}`))

    expect(headerRule).not.toBeNull()
    expect(headerRule[1]).not.toMatch(/position:\s*sticky/)
  })
})
