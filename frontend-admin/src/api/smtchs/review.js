import request from '@/utils/request'

// 确认单条记录
export function approveRecord(id) {
  return request({
    url: '/smtchs/review/approve/' + id,
    method: 'put'
  })
}

// 批量确认
export function approveBatch(ids) {
  return request({
    url: '/smtchs/review/approve-batch',
    method: 'put',
    data: ids
  })
}

// 驳回记录
export function rejectRecord(id, reason) {
  return request({
    url: '/smtchs/review/reject/' + id,
    method: 'put',
    data: { reason: reason }
  })
}

// 修正已确认记录
export function reviseRecord(id, data) {
  return request({
    url: '/smtchs/review/revise/' + id,
    method: 'put',
    data: data
  })
}

// 查询修正痕迹
export function listRevisions(id) {
  return request({
    url: '/smtchs/review/revisions/' + id,
    method: 'get'
  })
}
