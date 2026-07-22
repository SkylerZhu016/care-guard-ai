UPDATE users
SET display_name = CASE role
  WHEN 'PATIENT' THEN '患者用户'
  WHEN 'CLINICIAN' THEN '医务人员'
  WHEN 'FOLLOWUP_STAFF' THEN '随访人员'
  WHEN 'ADMIN' THEN '系统管理员'
  ELSE display_name
END
WHERE username IN ('patient','clinician','followup','admin');

UPDATE followup_tasks
SET title = CASE task_code
  WHEN 'BP_RECORD' THEN '记录血压数据'
  WHEN 'SYMPTOM_CHECK' THEN '完成结构化症状复核'
  WHEN 'ADHERENCE_CHECK' THEN '记录随访计划执行情况'
  WHEN 'CLINICIAN_REVIEW' THEN '第 4 周医务人员复核'
  ELSE title
END;

UPDATE visits
SET chief_complaint = replace(replace(replace(chief_complaint, '合成病例', '测试记录'), '教学病例', '示例记录'), '合成', '测试'),
    free_text = replace(replace(replace(replace(free_text, '教学模拟', '功能测试'), '合成数据', '测试数据'), '教学病例', '示例记录'), '合成病例', '测试记录')
WHERE intake_version = 'INTAKE_V1';
