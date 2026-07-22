UPDATE visits
SET chief_complaint = regexp_replace(chief_complaint, '(Synthetic case|Fixture)\s*:\s*chest pain with dyspnea', '胸口不适伴呼吸困难', 'gi'),
    free_text = regexp_replace(free_text, '(synthetic|fixture)\s+data', '测试数据', 'gi')
WHERE intake_version = 'INTAKE_V1';

UPDATE visits
SET chief_complaint = '历史测试记录'
WHERE intake_version = 'INTAKE_V1'
  AND chief_complaint LIKE '%?%';

UPDATE symptoms
SET name = CASE code
  WHEN 'CHEST_PAIN' THEN '胸痛'
  WHEN 'DYSPNEA' THEN '呼吸困难'
  WHEN 'SYNCOPE' THEN '晕厥'
  WHEN 'ALTERED_CONSCIOUSNESS' THEN '意识异常'
  WHEN 'HEADACHE' THEN '头痛'
  WHEN 'FATIGUE' THEN '乏力'
  ELSE name
END
WHERE report_source = 'LEGACY';

UPDATE triage_results
SET ai_summary = replace(replace(replace(replace(replace(replace(replace(replace(replace(
  ai_summary,
  'Synthetic case', '测试记录'),
  'Fixture', '测试记录'),
  'JUST_NOW', '刚刚'),
  'TODAY', '今天'),
  'ONE_TO_THREE_DAYS', '1–3 天'),
  'MORE_THAN_THREE_DAYS', '3 天以上'),
  'PRESENT', '仍存在'),
  'NEEDS_REST', '需要停下休息'),
  'UNABLE_NORMAL_ACTIVITY', '无法正常活动')
WHERE ai_summary IS NOT NULL;
