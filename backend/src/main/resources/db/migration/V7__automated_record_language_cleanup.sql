UPDATE visits
SET chief_complaint = CASE chief_complaint
      WHEN 'Chest discomfort with dyspnea' THEN '胸口不适并伴呼吸困难'
      WHEN 'Headache today' THEN '今天头痛'
      ELSE chief_complaint
    END,
    free_text = CASE free_text
      WHEN 'Privacy-safe automated test data' THEN '不含真实身份信息的自动化测试数据'
      WHEN 'Requires manual review' THEN '需要人工复核'
      ELSE free_text
    END
WHERE chief_complaint IN ('Chest discomfort with dyspnea', 'Headache today')
   OR free_text IN ('Privacy-safe automated test data', 'Requires manual review');

UPDATE visit_supplements
SET content = '补充测试信息：今天下午有恶心'
WHERE content = 'Additional fixture detail: nausea this afternoon';

UPDATE triage_results
SET review_reason = '自动化测试：事实、规则和引用已核对'
WHERE review_reason = 'Automated fixture: facts, rules, and citations checked';

UPDATE followup_tasks
SET result_summary = CASE result_summary
  WHEN 'Automated fixture in progress' THEN '自动化测试处理中'
  WHEN 'Automated fixture completed' THEN '自动化测试已完成'
  ELSE result_summary
END
WHERE result_summary IN ('Automated fixture in progress', 'Automated fixture completed');
