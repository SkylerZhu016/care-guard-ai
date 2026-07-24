-- Older PowerShell smoke requests could persist literal question marks when
-- their UTF-8 request body was decoded with the wrong console encoding.
-- The lost source text cannot be reconstructed, so keep the records while
-- replacing only visibly corrupted values with an explicit neutral notice.
UPDATE visits
SET chief_complaint = '历史测试记录（原文编码异常）'
WHERE position('???' IN chief_complaint) > 0;

UPDATE visits
SET free_text = '历史补充说明编码异常，原文无法恢复'
WHERE position('???' IN free_text) > 0;

UPDATE triage_results
SET ai_summary = '历史自动摘要编码异常，请依据患者事实人工复核'
WHERE position('???' IN coalesce(ai_summary, '')) > 0;

UPDATE triage_results
SET review_reason = '历史审核说明编码异常，请重新核对'
WHERE position('???' IN coalesce(review_reason, '')) > 0;

UPDATE visit_supplements
SET content = '历史补充信息编码异常，原文无法恢复'
WHERE position('???' IN content) > 0;
