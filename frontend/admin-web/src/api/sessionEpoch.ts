let epoch = 0

/** 读取当前身份操作代次，供请求判断响应是否仍属于当前登录状态。 */
export function currentSessionEpoch(): number {
  return epoch
}

/** 登录、注销或清除身份时递增代次，使旧请求的权限错误不影响新会话。 */
export function advanceSessionEpoch(): void {
  epoch++
}
