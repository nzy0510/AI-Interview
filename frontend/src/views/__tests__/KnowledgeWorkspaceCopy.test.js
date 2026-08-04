import { describe, expect, it } from 'vitest'
import workspaceSource from '../KnowledgeWorkspace.vue?raw'

describe('KnowledgeWorkspace public copy', () => {
  it('does not expose competition-specific wording in the web interface', () => {
    expect(workspaceSource).not.toMatch(/比赛|Demo/i)
  })
})
