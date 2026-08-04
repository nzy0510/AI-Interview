import { ref } from 'vue'
import { getKnowledgeWorkspaceCapabilitiesAPI } from '@/api/knowledgeWorkspace'
import { normalizeKnowledgeWorkspaceCapabilities } from '@/utils/knowledgeWorkspace'

export function useKnowledgeWorkspaceAccess(fetchCapabilities = getKnowledgeWorkspaceCapabilitiesAPI) {
  const workspaceCapabilities = ref(normalizeKnowledgeWorkspaceCapabilities())
  const workspaceCapabilitiesResolved = ref(false)
  const workspaceCapabilitiesFailed = ref(false)
  let workspaceCapabilitiesPromise = null

  const loadWorkspaceCapabilities = () => {
    if (workspaceCapabilitiesResolved.value) return Promise.resolve(workspaceCapabilities.value)
    if (!workspaceCapabilitiesPromise) {
      workspaceCapabilitiesPromise = fetchCapabilities({ silent: true })
        .then((data) => {
          workspaceCapabilities.value = normalizeKnowledgeWorkspaceCapabilities(data)
          workspaceCapabilitiesFailed.value = false
        })
        .catch(() => {
          workspaceCapabilities.value = normalizeKnowledgeWorkspaceCapabilities()
          workspaceCapabilitiesFailed.value = true
        })
        .finally(() => {
          workspaceCapabilitiesResolved.value = true
        })
    }
    return workspaceCapabilitiesPromise
  }

  return {
    workspaceCapabilities,
    workspaceCapabilitiesResolved,
    workspaceCapabilitiesFailed,
    loadWorkspaceCapabilities
  }
}
