import request from '@/utils/request'

// 查询月度报表
export function getMonthlyReport(query) {
  return request({
    url: '/smtchs/report/monthly',
    method: 'get',
    params: query
  })
}

// 导出 Excel（双 Sheet）
export function exportReport(month) {
  return request({
    url: '/smtchs/report/export',
    method: 'post',
    params: { month: month },
    responseType: 'blob'
  })
}
