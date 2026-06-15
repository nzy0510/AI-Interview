import { describe, expect, it } from 'vitest'
import {
  isPositionEditable,
  canMaintainQuestionBank,
  canPublishQuestionBankAtoms,
  canArchiveQuestionBankAtoms,
  parseImportPackageText
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
})
