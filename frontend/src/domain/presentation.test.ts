import { describe, expect, it } from 'vitest'
import { parsePhysiologicalInfo, physiologicalInfoLabel, reasonLabel, serializePhysiologicalInfo, taskPresentation } from './presentation'

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
})
