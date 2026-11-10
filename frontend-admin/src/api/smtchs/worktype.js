import request from '@/utils/request'

// 查询工作类型列表
export function listWorkType(query) {
  return request({
    url: '/smtchs/worktype/list',
    method: 'get',
    params: query
  })
}

// 查询启用的工作类型（员工端选类型用）
export function listEnabledWorkType() {
  return request({
    url: '/smtchs/worktype/enabled',
    method: 'get'
  })
}

// 新增工作类型
export function addWorkType(data) {
  return request({
    url: '/smtchs/worktype',
    method: 'post',
    data: data
  })
}

// 修改工作类型
export function updateWorkType(data) {
  return request({
    url: '/smtchs/worktype',
    method: 'put',
    data: data
  })
}

// 删除工作类型
export function delWorkType(id) {
  return request({
    url: '/smtchs/worktype/' + id,
    method: 'delete'
  })
}
