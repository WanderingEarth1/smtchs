import request from '@/utils/request'

// 查询计费配置
export function getConfig() {
  return request({
    url: '/smtchs/config',
    method: 'get'
  })
}

// 修改计费配置
export function updateConfig(data) {
  return request({
    url: '/smtchs/config',
    method: 'put',
    data: data
  })
}
