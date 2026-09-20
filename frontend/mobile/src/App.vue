<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { useSessionStore } from './stores/session'
/** 启动时仅恢复已有真实会话；失败不保留任何模拟身份或个人资料。 */
onLaunch(() => {
  void useSessionStore()
    .restore()
    .catch(() => {})
})
</script>
<style>
@import './styles/main.css';
</style>
