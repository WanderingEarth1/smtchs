import request from '@/utils/request'

// 查询全部记录（管理端）
export function listRecord(query) {
  return request({
    url: '/smtchs/record/list',
    method: 'get',
    params: query
  })
}

// 查询记录详情
export function getRecord(id) {
  return request({
    url: '/smtchs/record/' + id,
    method: 'get'
  })
}
