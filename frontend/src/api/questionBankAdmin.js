import request from '@/utils/request'

const adminHeaders = (adminToken) => ({ 'X-Admin-Token': adminToken })

export const getQuestionBankCategoriesAPI = (adminToken) => {
  return request({
    url: '/admin/question-bank/categories',
    method: 'get',
    headers: adminHeaders(adminToken)
  })
}

export const validateQuestionBankImportAPI = (data, adminToken) => {
  return request({
    url: '/admin/question-bank/import/validate',
    method: 'post',
    data,
    headers: adminHeaders(adminToken)
  })
}

export const dryRunQuestionBankImportAPI = (data, adminToken) => {
  return request({
    url: '/admin/question-bank/import/dry-run',
    method: 'post',
    data,
    headers: adminHeaders(adminToken)
  })
}

export const publishQuestionBankImportAPI = (data, adminToken) => {
  return request({
    url: '/admin/question-bank/import/publish',
    method: 'post',
    data,
    headers: adminHeaders(adminToken)
  })
}

export const searchQuestionBankAtomsAPI = (data, adminToken) => {
  return request({
    url: '/admin/question-bank/atoms/search',
    method: 'post',
    data,
    headers: adminHeaders(adminToken)
  })
}

export const archiveQuestionBankAtomsAPI = (atomIds, adminToken) => {
  return request({
    url: '/admin/question-bank/atoms/archive',
    method: 'post',
    data: { atomIds },
    headers: adminHeaders(adminToken)
  })
}

export const publishQuestionBankAtomsAPI = (atomIds, adminToken) => {
  return request({
    url: '/admin/question-bank/atoms/publish',
    method: 'post',
    data: { atomIds },
    headers: adminHeaders(adminToken)
  })
}

export const reindexQuestionBankAtomsAPI = (atomIds, adminToken) => {
  return request({
    url: '/admin/question-bank/atoms/reindex',
    method: 'post',
    data: { atomIds },
    headers: adminHeaders(adminToken)
  })
}

export const listQuestionBankBatchesAPI = (params, adminToken) => {
  return request({
    url: '/admin/question-bank/batches',
    method: 'get',
    params,
    headers: adminHeaders(adminToken)
  })
}

export const getQuestionBankBatchAPI = (batchId, adminToken) => {
  return request({
    url: `/admin/question-bank/batches/${encodeURIComponent(batchId)}`,
    method: 'get',
    headers: adminHeaders(adminToken)
  })
}

export const archiveQuestionBankBatchAPI = (batchId, adminToken) => {
  return request({
    url: `/admin/question-bank/batches/${encodeURIComponent(batchId)}/archive`,
    method: 'post',
    headers: adminHeaders(adminToken)
  })
}

export const reindexUnsyncedQuestionBankAPI = (adminToken) => {
  return request({
    url: '/admin/question-bank/reindex/unsynced',
    method: 'post',
    headers: adminHeaders(adminToken)
  })
}

export const reindexAllQuestionBankAPI = (adminToken) => {
  return request({
    url: '/admin/question-bank/reindex/all',
    method: 'post',
    headers: adminHeaders(adminToken)
  })
}

export const previewQuestionBankSearchAPI = (data, adminToken) => {
  return request({
    url: '/admin/question-bank/search-preview',
    method: 'post',
    data,
    headers: adminHeaders(adminToken)
  })
}
