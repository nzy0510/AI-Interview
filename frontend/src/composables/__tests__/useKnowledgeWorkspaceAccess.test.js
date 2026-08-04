import { describe, expect, it, vi } from 'vitest'
import { useKnowledgeWorkspaceAccess } from '../useKnowledgeWorkspaceAccess'

describe('useKnowledgeWorkspaceAccess', () => {
  it('loads and caches normalized capabilities', async () => {
    const fetchCapabilities = vi.fn().mockResolvedValue({
      userMaintenanceEnabled: false,
      admin: true,
      canAccessWorkspace: true,
      ignored: true
    })
    const access = useKnowledgeWorkspaceAccess(fetchCapabilities)

    await Promise.all([access.loadWorkspaceCapabilities(), access.loadWorkspaceCapabilities()])

    expect(fetchCapabilities).toHaveBeenCalledTimes(1)
    expect(access.workspaceCapabilities.value).toEqual({
      userMaintenanceEnabled: false,
      admin: true,
      canAccessWorkspace: true
    })
    expect(access.workspaceCapabilitiesResolved.value).toBe(true)
    expect(access.workspaceCapabilitiesFailed.value).toBe(false)
  })

  it('fails closed when capabilities cannot be loaded', async () => {
    const access = useKnowledgeWorkspaceAccess(vi.fn().mockRejectedValue(new Error('network')))

    await access.loadWorkspaceCapabilities()

    expect(access.workspaceCapabilities.value.canAccessWorkspace).toBe(false)
    expect(access.workspaceCapabilitiesResolved.value).toBe(true)
    expect(access.workspaceCapabilitiesFailed.value).toBe(true)
  })
})
