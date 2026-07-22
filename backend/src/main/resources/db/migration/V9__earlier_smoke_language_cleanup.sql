UPDATE triage_results
SET ai_summary = regexp_replace(
      regexp_replace(ai_summary,
        '(测试记录|Fixture)\s*:\s*chest discomfort with dyspnea',
        '胸口不适并伴呼吸困难', 'gi'),
      '(测试记录|Fixture)\s*:\s*headache today',
      '今天头痛', 'gi')
WHERE ai_summary ~* '(chest discomfort with dyspnea|headache today)';

UPDATE visits
SET chief_complaint = CASE chief_complaint
      WHEN 'Fixture: chest discomfort with dyspnea' THEN '胸口不适并伴呼吸困难'
      WHEN 'Fixture: headache today' THEN '今天头痛'
      ELSE chief_complaint
    END,
    free_text = CASE free_text
      WHEN 'Privacy-safe automated fixture data' THEN '不含真实身份信息的自动化测试数据'
      ELSE free_text
    END
WHERE chief_complaint IN ('Fixture: chest discomfort with dyspnea', 'Fixture: headache today')
   OR free_text = 'Privacy-safe automated fixture data';
