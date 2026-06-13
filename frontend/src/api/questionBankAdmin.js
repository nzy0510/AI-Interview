import request from '@/utils/request'

export const getQuestionBankCategoriesAPI = () => {
  return request({
    url: '/admin/question-bank/categories',
    method: 'get'
  })
}

export const validateQuestionBankImportAPI = (data) => {
  return request({
    url: '/admin/question-bank/import/validate',
    method: 'post',
    data
  })
}

export const dryRunQuestionBankImportAPI = (data) => {
  return request({
    url: '/admin/question-bank/import/dry-run',
    method: 'post',
    data
  })
}

export const publishQuestionBankImportAPI = (data) => {
  return request({
    url: '/admin/question-bank/import/publish',
    method: 'post',
    data
  })
}

export const searchQuestionBankAtomsAPI = (data) => {
  return request({
    url: '/admin/question-bank/atoms/search',
    method: 'post',
    data
  })
}

export const archiveQuestionBankAtomsAPI = (atomIds) => {
  return request({
    url: '/admin/question-bank/atoms/archive',
    method: 'post',
    data: { atomIds }
  })
}

export const publishQuestionBankAtomsAPI = (atomIds) => {
  return request({
    url: '/admin/question-bank/atoms/publish',
    method: 'post',
    data: { atomIds }
  })
}

export const reindexQuestionBankAtomsAPI = (atomIds) => {
  return request({
    url: '/admin/question-bank/atoms/reindex',
    method: 'post',
    data: { atomIds }
  })
}

export const listQuestionBankBatchesAPI = (params) => {
  return request({
    url: '/admin/question-bank/batches',
    method: 'get',
    params
  })
}

export const getQuestionBankBatchAPI = (batchId) => {
  return request({
    url: `/admin/question-bank/batches/${encodeURIComponent(batchId)}`,
    method: 'get'
  })
}

export const archiveQuestionBankBatchAPI = (batchId) => {
  return request({
    url: `/admin/question-bank/batches/${encodeURIComponent(batchId)}/archive`,
    method: 'post'
  })
}

export const reindexUnsyncedQuestionBankAPI = () => {
  return request({
    url: '/admin/question-bank/reindex/unsynced',
    method: 'post'
  })
}

export const reindexAllQuestionBankAPI = () => {
  return request({
    url: '/admin/question-bank/reindex/all',
    method: 'post'
  })
}

export const previewQuestionBankSearchAPI = (data) => {
  return request({
    url: '/admin/question-bank/search-preview',
    method: 'post',
    data
  })
}
