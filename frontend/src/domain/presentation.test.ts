import { describe, expect, it } from 'vitest'
import type { Visit } from '../types'
import { isHypertensionTeachingCase, isSupportedSymptom, reasonLabel, SYMPTOM_CATALOG, taskPresentation } from './presentation'

describe('presentation safety boundary', () => {
  it('exposes only the four symptoms with configured teaching rules', () => {
    expect(SYMPTOM_CATALOG.map(item=>item.code)).toEqual(['CHEST_PAIN','DYSPNEA','SYNCOPE','ALTERED_CONSCIOUSNESS'])
    expect(isSupportedSymptom('HEADACHE')).toBe(false)
  })

  it('describes no configured red flag as requiring human review', () => {
    expect(reasonLabel('NO_CONFIGURED_RED_FLAG')).toContain('仍需人工审核')
  })

  it('does not treat a generic chest-pain case as hypertension teaching', () => {
    const visit={chiefComplaint:'合成胸痛',freeText:'',symptoms:[{code:'CHEST_PAIN',name:'胸痛',severity:3}]} as Visit
    expect(isHypertensionTeachingCase(visit)).toBe(false)
    expect(isHypertensionTeachingCase({...visit,freeText:'高血压教学病例'})).toBe(true)
  })

  it('uses human-readable task labels', () => {
    expect(taskPresentation('BP_RECORD').label).toBe('教学血压记录')
  })
})
