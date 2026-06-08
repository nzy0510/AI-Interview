import { describe, expect, it } from 'vitest'
import { renderSafeMarkdown } from '../markdown'

describe('markdown utils', () => {
  it('renders normal markdown', () => {
    expect(renderSafeMarkdown('**重点**')).toContain('<strong>重点</strong>')
  })

  it('removes dangerous HTML from markdown output', () => {
    const html = renderSafeMarkdown('<img src=x onerror=alert(1)>[x](javascript:alert(1))')

    expect(html).not.toContain('onerror')
    expect(html).not.toContain('javascript:')
  })
})
