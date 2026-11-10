/**
 * 密码摘要 —— 原型环境下的不可逆摘要实现
 *
 * 说明：需求 LOG-05 要求「密码加密存储（不可逆）」。
 * 生产环境应由后端使用 bcrypt / argon2 加盐哈希；本原型为纯前端演示，
 * 使用 Web Crypto 的 SHA-256 + 固定盐做不可逆摘要，确保明文不落库。
 */

const SALT = 'smtchs::v1::'

/** 计算不可逆摘要（十六进制） */
export async function hashPassword(plain: string): Promise<string> {
  const data = new TextEncoder().encode(SALT + plain)
  if (globalThis.crypto?.subtle) {
    const buf = await globalThis.crypto.subtle.digest('SHA-256', data)
    return Array.from(new Uint8Array(buf))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('')
  }
  // 兜底：非安全上下文（如 http 局域网）下的简易摘要，仅用于原型演示
  let h1 = 0x811c9dc5
  let h2 = 0x01000193
  for (let i = 0; i < data.length; i++) {
    h1 ^= data[i]
    h1 = Math.imul(h1, 0x01000193) >>> 0
    h2 = (Math.imul(h2 ^ data[i], 0x85ebca6b) + i) >>> 0
  }
  return `fb${h1.toString(16).padStart(8, '0')}${h2.toString(16).padStart(8, '0')}`
}

/** 校验密码 */
export async function verifyPassword(plain: string, hash: string): Promise<boolean> {
  const computed = await hashPassword(plain)
  return computed === hash
}
