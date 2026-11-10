import request from '@/utils/request'

// 查询单价矩阵
export function getPriceMatrix() {
  return request({
    url: '/smtchs/price/matrix',
    method: 'get'
  })
}

// 设置单价
export function setPrice(data) {
  return request({
    url: '/smtchs/price',
    method: 'put',
    data: data
  })
}

// 按行复制单价
export function copyPriceRow(data) {
  return request({
    url: '/smtchs/price/copy',
    method: 'post',
    data: data
  })
}
