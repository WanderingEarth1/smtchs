import request from '@/utils/request'

// 查询员工列表
export function listEmployee(query) {
  return request({
    url: '/smtchs/employee/list',
    method: 'get',
    params: query
  })
}

// 查询员工详细
export function getEmployee(id) {
  return request({
    url: '/smtchs/employee/' + id,
    method: 'get'
  })
}

// 新增员工
export function addEmployee(data) {
  return request({
    url: '/smtchs/employee',
    method: 'post',
    data: data
  })
}

// 修改员工
export function updateEmployee(data) {
  return request({
    url: '/smtchs/employee',
    method: 'put',
    data: data
  })
}

// 删除员工
export function delEmployee(id) {
  return request({
    url: '/smtchs/employee/' + id,
    method: 'delete'
  })
}

// 重置密码
export function resetEmployeePwd(id, password) {
  return request({
    url: '/smtchs/employee/' + id + '/password',
    method: 'put',
    data: { password: password }
  })
}
