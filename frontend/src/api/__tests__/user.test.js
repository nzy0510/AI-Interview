import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: vi.fn()
}))

import request from '@/utils/request'
import { getKnowledgeCoverageAPI, getMentorInsightAPI, refreshMentorInsightAPI } from '../user'

describe('AI Mentor API adapters', () => {
  beforeEach(() => {
    request.mockReset()
    request.mockResolvedValue({})
  })

  it('scopes Mentor reads and refreshes to the selected position', async () => {
    await getMentorInsightAPI(20)
    await refreshMentorInsightAPI(21)
    await getKnowledgeCoverageAPI(20)

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/user/mentor-insight',
      method: 'get',
      params: { positionId: 20 },
      timeout: 70000
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/user/mentor-insight/refresh',
      method: 'post',
      params: { positionId: 21 },
      timeout: 70000
    })
    expect(request).toHaveBeenNthCalledWith(3, {
      url: '/user/knowledge-coverage',
      method: 'get',
      params: { positionId: 20 }
    })
  })
})
