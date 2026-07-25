import { describe, expect, it } from 'vitest'
import { normalizeReproductiveStatus, parsePhysiologicalInfo, physiologicalInfoLabel, reasonLabel, REPRODUCTIVE_STATUS_OPTIONS, reproductiveOptionsFor, serializePhysiologicalInfo, taskPresentation } from './presentation'

describe('presentation safety boundary', () => {
  it('describes unsupported symptoms as requiring human review', () => {
    expect(reasonLabel('UNSUPPORTED_SYMPTOMS_REQUIRE_REVIEW')).toContain('人工复核')
  })

  it('distinguishes supported no-match from no corresponding rule', () => {
    expect(reasonLabel('SUPPORTED_NO_RULE_MATCH_REQUIRES_REVIEW')).toContain('未覆盖')
    expect(reasonLabel('UNSUPPORTED_SYMPTOMS_REQUIRE_REVIEW')).not.toBe(reasonLabel('SUPPORTED_NO_RULE_MATCH_REQUIRES_REVIEW'))
  })

  it('uses human-readable task labels', () => {
    expect(taskPresentation('BP_RECORD').label).toBe('血压记录')
  })

  it('serializes physiological choices without free-text input', () => {
    const encoded=serializePhysiologicalInfo({birthSex:'FEMALE',reproductiveStatus:'PREGNANT'})
    expect(parsePhysiologicalInfo(encoded)).toEqual({birthSex:'FEMALE',reproductiveStatus:'PREGNANT'})
    expect(physiologicalInfoLabel(encoded)).toContain('已怀孕')
  })

  it('filters reproductive choices without removing privacy-preserving answers', () => {
    expect(reproductiveOptionsFor('MALE').map(item=>item.value)).toEqual(['NOT_APPLICABLE','UNKNOWN','PREFER_NOT_TO_SAY'])
    expect(reproductiveOptionsFor('FEMALE').map(item=>item.value)).toEqual([
      'NOT_PREGNANT','POSSIBLY_PREGNANT','PREGNANT','POSTPARTUM_SIX_WEEKS','BREASTFEEDING','UNKNOWN','PREFER_NOT_TO_SAY',
    ])
    expect(reproductiveOptionsFor('INTERSEX_OR_OTHER')).toEqual(REPRODUCTIVE_STATUS_OPTIONS)
  })

  it('normalizes a stale reproductive choice after birth sex changes', () => {
    expect(normalizeReproductiveStatus('MALE','PREGNANT')).toBe('NOT_APPLICABLE')
    expect(normalizeReproductiveStatus('FEMALE','NOT_APPLICABLE')).toBe('UNKNOWN')
    expect(normalizeReproductiveStatus('FEMALE','PREFER_NOT_TO_SAY')).toBe('PREFER_NOT_TO_SAY')
  })
})
