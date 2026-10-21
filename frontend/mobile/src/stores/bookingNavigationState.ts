/** 创建仅驻留内存的一次性预约导航意图，身份不一致时直接丢弃。 */
export function createBookingNavigation() {
  let pending: { accountId: string; bookingId: string } | null = null

  /** 保存当前已认证账号的目标编号，不保存预约资料或授权结论。 */
  function queue(accountId: string, bookingId: string) {
    pending = { accountId, bookingId }
  }

  /** 消费一次目标；接收页仍须请求详情并由服务端重新检查对象权限。 */
  function consume(accountId: string): string | null {
    const target = pending
    pending = null
    return target?.accountId === accountId ? target.bookingId : null
  }

  /** 会话结束或导航失败时删除尚未消费的意图。 */
  function clear() {
    pending = null
  }

  return { queue, consume, clear }
}
