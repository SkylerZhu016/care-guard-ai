import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { router } from './router'
import { useSessionStore } from './stores/session'

describe('role route guard', () => {
  beforeEach(async () => { setActivePinia(createPinia()); await router.push('/login') })
  it('redirects anonymous users to login', async () => { await router.push('/clinician'); expect(router.currentRoute.value.path).toBe('/login') })
  it('redirects a patient away from clinician workspace', async () => {
    useSessionStore().establish('token',{id:'1',username:'patient',displayName:'模拟患者',role:'SIMULATED_PATIENT'})
    await router.push('/clinician'); expect(router.currentRoute.value.path).toBe('/patient')
  })
})

