import DOMPurify from 'dompurify'
import { marked } from 'marked'

export function renderSafeMarkdown(text) {
  if (!text) return ''
  return DOMPurify.sanitize(marked.parse(text), {
    FORBID_ATTR: ['style']
  })
}
