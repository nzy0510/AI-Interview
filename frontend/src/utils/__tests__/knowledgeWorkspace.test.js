import { describe, expect, it } from 'vitest'
import {
  isPositionEditable,
  canMaintainQuestionBank,
  canPublishQuestionBankAtoms,
  canArchiveQuestionBankAtoms,
  canCreatePrivatePosition,
  getKnowledgeWorkspaceNavLabel,
  isPublicOnlyMaintenanceMode,
  normalizeKnowledgeWorkspaceCapabilities,
  shouldShowKnowledgeWorkspace,
  parseImportPackageText
} from '../knowledgeWorkspace'
import {
  canBuildQuestionBank,
  getQuestionBankBuildStatusLabel,
  isQuestionBankCandidateAccepted,
  isQuestionBankAtomPublishEligible,
  isQuestionBankBuildInProgress
} from '../knowledgeWorkspace'

describe('knowledge workspace utils', () => {
  it('keeps public positions read-only', () => {
    expect(isPositionEditable({ scope: 'PUBLIC', editable: false, status: 'ACTIVE' })).toBe(false)
  })

  it('uses backend capability flags for package maintenance', () => {
    expect(canMaintainQuestionBank({ scope: 'PRIVATE', editable: true, status: 'ACTIVE', knowledgeBase: { id: 9 } })).toBe(true)
    expect(canMaintainQuestionBank({
      scope: 'PUBLIC',
      editable: false,
      status: 'ACTIVE',
      knowledgeBase: { id: 9 },
      canImportPackage: true,
      canManageAtoms: true
    })).toBe(true)
    expect(canPublishQuestionBankAtoms({
      scope: 'PUBLIC',
      status: 'ACTIVE',
      knowledgeBase: { id: 9 },
      canPublishAtoms: true
    })).toBe(true)
    expect(canArchiveQuestionBankAtoms({
      scope: 'PRIVATE',
      status: 'ACTIVE',
      knowledgeBase: { id: 9 },
      canArchiveAtoms: true
    })).toBe(true)
    expect(canMaintainQuestionBank({ scope: 'PUBLIC', editable: false, status: 'ACTIVE', knowledgeBase: { id: 9 }, canImportPackage: false })).toBe(false)
    expect(canMaintainQuestionBank({ scope: 'PRIVATE', editable: true, status: 'ACTIVE', knowledgeBase: null })).toBe(false)
  })

  it('parses JSON import package text and rejects non-object payloads', () => {
    expect(parseImportPackageText('{"batchId":"qb-1","atoms":[]}')).toEqual({ batchId: 'qb-1', atoms: [] })
    expect(() => parseImportPackageText('[{"id":"atom"}]')).toThrow('导入包必须是 JSON 对象')
    expect(() => parseImportPackageText('{bad json')).toThrow('无法解析 JSON 导入包')
  })

  it('normalizes workspace capabilities and treats access as backend-owned', () => {
    expect(normalizeKnowledgeWorkspaceCapabilities({
      userMaintenanceEnabled: true,
      admin: false,
      canAccessWorkspace: true,
      ignored: 'value'
    })).toEqual({
      userMaintenanceEnabled: true,
      admin: false,
      canAccessWorkspace: true
    })
    expect(normalizeKnowledgeWorkspaceCapabilities({
      userMaintenanceEnabled: 1,
      admin: 'true',
      canAccessWorkspace: true
    })).toEqual({
      userMaintenanceEnabled: false,
      admin: false,
      canAccessWorkspace: true
    })
    expect(normalizeKnowledgeWorkspaceCapabilities()).toEqual({
      userMaintenanceEnabled: false,
      admin: false,
      canAccessWorkspace: false
    })
  })

  it('builds workspace navigation from capabilities', () => {
    const publicOnlyAdminCapabilities = {
      admin: true,
      userMaintenanceEnabled: false,
      canAccessWorkspace: true
    }
    const localAdminCapabilities = {
      admin: true,
      userMaintenanceEnabled: true,
      canAccessWorkspace: true
    }
    const userCapabilities = { admin: false, userMaintenanceEnabled: true, canAccessWorkspace: true }
    const closedCapabilities = { admin: false, userMaintenanceEnabled: false, canAccessWorkspace: false }

    expect(shouldShowKnowledgeWorkspace(publicOnlyAdminCapabilities)).toBe(true)
    expect(getKnowledgeWorkspaceNavLabel(publicOnlyAdminCapabilities)).toBe('公共题库维护')
    expect(isPublicOnlyMaintenanceMode(publicOnlyAdminCapabilities)).toBe(true)
    expect(canCreatePrivatePosition(publicOnlyAdminCapabilities)).toBe(false)

    expect(getKnowledgeWorkspaceNavLabel(localAdminCapabilities)).toBe('岗位 / 题库维护')
    expect(isPublicOnlyMaintenanceMode(localAdminCapabilities)).toBe(false)
    expect(canCreatePrivatePosition(localAdminCapabilities)).toBe(true)

    expect(shouldShowKnowledgeWorkspace(userCapabilities)).toBe(true)
    expect(getKnowledgeWorkspaceNavLabel(userCapabilities)).toBe('岗位 / 题库维护')
    expect(canCreatePrivatePosition(userCapabilities)).toBe(true)
    expect(shouldShowKnowledgeWorkspace(closedCapabilities)).toBe(false)
    expect(canCreatePrivatePosition(closedCapabilities)).toBe(false)
  })

  it('keeps build access private-owner scoped and requires accepted review for import/publish', () => {
    expect(canBuildQuestionBank({ scope: 'PRIVATE', editable: true, status: 'ACTIVE', knowledgeBase: { id: 1 } })).toBe(true)
    expect(canBuildQuestionBank({ scope: 'PUBLIC', editable: true, status: 'ACTIVE', knowledgeBase: { id: 1 } })).toBe(false)
    expect(isQuestionBankCandidateAccepted({ reviewStatus: 'ACCEPTED' })).toBe(true)
    expect(isQuestionBankCandidateAccepted({ reviewStatus: 'PENDING' })).toBe(false)
    expect(isQuestionBankAtomPublishEligible({ status: 'DRAFT', reviewStatus: 'PASS' })).toBe(true)
    expect(isQuestionBankAtomPublishEligible({ status: 'DRAFT', reviewStatus: 'ACCEPTED' })).toBe(false)
    expect(isQuestionBankAtomPublishEligible({ status: 'DRAFT', reviewStatus: 'NEEDS_REVIEW' })).toBe(false)
    expect(isQuestionBankBuildInProgress({ status: 'PENDING' })).toBe(true)
    expect(isQuestionBankBuildInProgress({ status: 'RUNNING' })).toBe(true)
    expect(isQuestionBankBuildInProgress({ status: 'COMPLETED' })).toBe(false)
    expect(getQuestionBankBuildStatusLabel('FAILED')).toBe('失败')
  })
})
