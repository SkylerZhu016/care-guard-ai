import type { Directive } from 'vue'
import { useAuthStore } from '@/stores/auth'
import type { Role } from '@/types'

/** v-permission="'review:write'" 或 v-permission="['DOCTOR','ADMIN']"（角色） */
export const permissionDirective: Directive = {
  mounted(el, binding) {
    const auth = useAuthStore()
    const value = binding.value as string | string[] | undefined
    if (!value) return
    const list = Array.isArray(value) ? value : [value]
    const roleSet: Role[] = ['PATIENT', 'DOCTOR', 'FOLLOWUP', 'ADMIN']
    const ok = list.some((v) =>
      (roleSet as string[]).includes(v) ? auth.hasRole(v as Role) : auth.hasPermission(v)
    )
    if (!ok) el.parentNode?.removeChild(el)
  }
}
