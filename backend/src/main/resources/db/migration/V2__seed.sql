-- =============================================================
-- V2__seed.sql  种子数据：角色/权限/账号/规则/Prompt/模型/指南/患者
-- 演示账号密码：Admin@123456 / Doctor@123456 / Follow@123456 / Patient@123456
-- =============================================================

-- ---------------- 角色 ----------------
INSERT INTO roles (id, code, name, description) VALUES
  (1, 'PATIENT',  '患者模拟用户', '填写预问诊、查看随访'),
  (2, 'DOCTOR',   '医务人员',     '审核预问诊、创建随访计划'),
  (3, 'FOLLOWUP', '随访人员',     '执行随访任务'),
  (4, 'ADMIN',    '系统管理员',   '管理系统');
SELECT setval(pg_get_serial_sequence('roles','id'), 4);

-- ---------------- 权限 ----------------
INSERT INTO permissions (code, name, type) VALUES
  ('visit:create',       '新建预问诊',       'API'),
  ('visit:read-own',     '查看本人问诊',     'API'),
  ('visit:read-all',     '查看全部问诊',     'API'),
  ('patient:manage-own', '管理本人档案',     'API'),
  ('patient:read',       '查看患者档案',     'API'),
  ('review:read',        '查看审核队列',     'API'),
  ('review:write',       '提交审核结果',     'API'),
  ('triage:read',        '查看分诊结果',     'API'),
  ('guideline:read',     '查看指南',         'API'),
  ('followup:read',      '查看随访',         'API'),
  ('followup:write',     '执行随访任务',     'API'),
  ('followup:plan',      '创建随访计划',     'API'),
  ('admin:users',        '用户管理',         'API'),
  ('admin:roles',        '角色管理',         'API'),
  ('admin:guidelines',   '指南管理',         'API'),
  ('admin:rules',        '规则管理',         'API'),
  ('admin:models',       '模型管理',         'API'),
  ('admin:prompts',      'Prompt管理',       'API'),
  ('admin:agent-runs',   'Agent运行记录',    'API'),
  ('admin:alerts',       '安全告警',         'API'),
  ('admin:audit',        '审计日志',         'API'),
  ('admin:configs',      '系统配置',         'API'),
  ('admin:files',        '文件管理',         'API');

-- ---------------- 角色-权限 ----------------
-- PATIENT
INSERT INTO role_permissions (role_id, permission_id)
  SELECT 1, id FROM permissions WHERE code IN ('visit:create','visit:read-own','patient:manage-own','followup:read');
-- DOCTOR
INSERT INTO role_permissions (role_id, permission_id)
  SELECT 2, id FROM permissions WHERE code IN ('visit:read-all','patient:read','review:read','review:write','triage:read','guideline:read','followup:plan','followup:read','admin:agent-runs');
-- FOLLOWUP
INSERT INTO role_permissions (role_id, permission_id)
  SELECT 3, id FROM permissions WHERE code IN ('visit:read-all','patient:read','followup:read','followup:write','admin:agent-runs');
-- ADMIN（全部权限）
INSERT INTO role_permissions (role_id, permission_id)
  SELECT 4, id FROM permissions;

-- ---------------- 用户（BCrypt 哈希） ----------------
INSERT INTO users (id, username, password_hash, real_name, phone, gender, birth_date, enabled) VALUES
  (1, 'admin',    '$2b$10$1IvnraU7NiK8qxu94a.g6uwCxaKSocyIO/hNoX9R0TEqvFmeMgBRe', '系统管理员', '13800000000', '男', '1990-01-01', TRUE),
  (2, 'doctor1',  '$2b$10$JHWoCW3INDMoI3Z3QUAwoO034F4jFg.hUfv62I9fy8fOc0Wn847ta', '李医生',     '13800000001', '女', '1985-06-15', TRUE),
  (3, 'follow1',  '$2b$10$XIsXtjbMcjfTSRGe6FuiUef0Eu1yLO8cQd423gdzmPFZUF74Fq.2W', '王随访',     '13800000002', '男', '1992-03-20', TRUE),
  (4, 'patient1', '$2b$10$1GBNIrpKSd14yl8gae47XOtAurGCYqpHW6PV9fc7ef.dBozgCWbam', '张模拟',     '13800000003', '男', '1990-05-12', TRUE);
SELECT setval(pg_get_serial_sequence('users','id'), 4);

INSERT INTO user_roles (user_id, role_id) VALUES (1,4),(2,2),(3,3),(4,1);

-- ---------------- 模拟患者（合成数据，owner=patient1） ----------------
INSERT INTO simulated_patients (id, patient_no, owner_user_id, name, gender, birth_date, phone, id_card, blood_type, chronic_tags, address) VALUES
  (1, 'SP20260001', 4, '张明', '男', '1990-05-12', '13800000003', '530100199005120011', 'A', '["高血压"]'::jsonb, '昆明市五华区'),
  (2, 'SP20260002', 4, '李芳', '女', '1968-03-08', '13800000004', '530100196803080028', 'O', '["糖尿病"]'::jsonb, '昆明市盘龙区'),
  (3, 'SP20260003', 4, '王小宝', '男', '2023-11-20', '13800000005', '530100202311200035', 'B', '[]'::jsonb, '昆明市西山区');
SELECT setval(pg_get_serial_sequence('simulated_patients','id'), 3);

-- 既往史
INSERT INTO patient_histories (patient_id, disease_name, diagnosed_at, note) VALUES
  (1, '高血压', '2018-06-01', '长期服用降压药'),
  (2, '2型糖尿病', '2015-09-10', '口服二甲双胍'),
  (2, '高血压', '2019-01-15', '血压控制尚可');
-- 过敏史
INSERT INTO allergy_records (patient_id, allergen, reaction, severity) VALUES
  (1, '青霉素', '皮疹', '中度');
-- 用药记录
INSERT INTO medication_records (patient_id, drug_name, dosage, frequency, start_date) VALUES
  (1, '氨氯地平', '5mg', '每日一次', '2018-06-01'),
  (2, '二甲双胍', '0.5g', '每日两次', '2015-09-10');

-- ---------------- 规则引擎（13 条） ----------------
-- 字段说明：symptoms=主诉+伴随聚合文本；specialGroup/severity 枚举；durationDays 数值；pastHistory/allergyHistory/medication 文本
INSERT INTO rule_definitions (code, name, category, priority, condition_expr, message, risk_level, enabled, current_version) VALUES
  ('RF001','胸痛伴呼吸困难','RED_FLAG',10,
   '{"all":[{"field":"symptoms","op":"CONTAINS_ANY","value":["胸痛","胸闷","心前区疼痛"]},{"field":"symptoms","op":"CONTAINS_ANY","value":["呼吸困难","气促","喘憋","气短"]}]}'::jsonb,
   '胸痛伴呼吸困难提示可能急性心血管事件，需立即就医','CRITICAL',TRUE,1),
  ('RF002','意识障碍','RED_FLAG',10,
   '{"field":"symptoms","op":"CONTAINS_ANY","value":["意识模糊","昏迷","昏厥","抽搐","晕厥","意识不清"]}'::jsonb,
   '意识障碍属危急症状，需立即急诊','CRITICAL',TRUE,1),
  ('RF003','高热不退','RED_FLAG',20,
   '{"all":[{"field":"symptoms","op":"CONTAINS_ANY","value":["发热","高烧","高热"]},{"field":"severity","op":"EQ","value":"SEVERE"}]}'::jsonb,
   '高热不退需排查严重感染，建议尽快就诊','HIGH',TRUE,1),
  ('RF004','消化道出血','RED_FLAG',20,
   '{"field":"symptoms","op":"CONTAINS_ANY","value":["呕血","便血","黑便","咯血"]}'::jsonb,
   '消化道出血征象，需紧急评估','HIGH',TRUE,1),
  ('RF005','剧烈头痛伴呕吐','RED_FLAG',25,
   '{"all":[{"field":"symptoms","op":"CONTAINS_ANY","value":["剧烈头痛","头痛欲裂"]},{"field":"symptoms","op":"CONTAINS_ANY","value":["呕吐","恶心"]}]}'::jsonb,
   '剧烈头痛伴呕吐需排除颅内病变','HIGH',TRUE,1),
  ('RF006','孕产妇阴道出血','RED_FLAG',10,
   '{"all":[{"field":"specialGroup","op":"EQ","value":"PREGNANT"},{"field":"symptoms","op":"CONTAINS_ANY","value":["阴道出血","出血","腹痛"]}]}'::jsonb,
   '孕期出血属危急情况，需立即急诊','CRITICAL',TRUE,1),
  ('SG001','孕产妇特殊人群','SPECIAL_GROUP',50,
   '{"field":"specialGroup","op":"EQ","value":"PREGNANT"}'::jsonb,
   '孕产妇需优先人工评估','MEDIUM',TRUE,1),
  ('SG002','婴幼儿特殊人群','SPECIAL_GROUP',50,
   '{"field":"specialGroup","op":"EQ","value":"INFANT"}'::jsonb,
   '婴幼儿症状需谨慎评估','MEDIUM',TRUE,1),
  ('SG003','老年人特殊人群','SPECIAL_GROUP',60,
   '{"field":"specialGroup","op":"EQ","value":"ELDERLY"}'::jsonb,
   '老年人症状可能不典型，建议关注','LOW',TRUE,1),
  ('DRUG001','青霉素过敏用药冲突','DRUG',30,
   '{"all":[{"field":"allergyHistory","op":"CONTAINS_ANY","value":["青霉素"]},{"field":"medication","op":"CONTAINS_ANY","value":["阿莫西林","氨苄西林","青霉素"]}]}'::jsonb,
   '青霉素过敏者可能使用含青霉素类药物，需核实用药安全','HIGH',TRUE,1),
  ('COMB001','发热伴皮疹','COMBINATION',70,
   '{"all":[{"field":"symptoms","op":"CONTAINS_ANY","value":["发热","发烧"]},{"field":"symptoms","op":"CONTAINS_ANY","value":["皮疹","红疹","出疹"]}]}'::jsonb,
   '发热伴皮疹需排查感染性疾病','MEDIUM',TRUE,1),
  ('MISS001','起病时间缺失','MISSING_INFO',90,
   '{"field":"onsetTime","op":"IS_EMPTY"}'::jsonb,
   '缺少起病时间，建议补充以便准确评估','LOW',TRUE,1),
  ('ESC001','症状迁延加重','ESCALATION',80,
   '{"all":[{"field":"durationDays","op":"GT","value":7},{"field":"severity","op":"EQ","value":"SEVERE"}]}'::jsonb,
   '症状持续超过一周且严重，建议尽快就诊','MEDIUM',TRUE,1);

-- 规则版本 v1 快照
INSERT INTO rule_versions (rule_id, version, condition_expr, message, risk_level)
  SELECT id, 1, condition_expr, message, risk_level FROM rule_definitions;

-- ---------------- Prompt 模板（5 段） ----------------
INSERT INTO prompt_templates (id, code, name, purpose, current_version) VALUES
  (1,'STRUCTURE','症状结构化 Prompt','从预问诊文本提取结构化症状',1),
  (2,'RETRIEVE','检索词构造 Prompt','由结构化症状构造检索词',1),
  (3,'RISK','风险分析 Prompt','基于证据生成风险关注点',1),
  (4,'SAFETY','安全审查 Prompt','复核输出合规性',1),
  (5,'SUMMARY','汇总摘要 Prompt','生成医生审核用摘要',1);
SELECT setval(pg_get_serial_sequence('prompt_templates','id'), 5);

INSERT INTO prompt_versions (template_id, version, content, enabled) VALUES
  (1,1,'你是医疗信息结构化助手。从患者预问诊文本中提取结构化症状信息，严格输出指定 JSON Schema。不得给出诊断建议。必须标注信息缺失项和置信度。',TRUE),
  (2,1,'根据结构化症状，生成 2-4 个用于检索公开医疗指南的关键词组合。仅输出关键词列表 JSON。',TRUE),
  (3,1,'你是医疗风险分析助手。基于规则命中结果、结构化症状和检索到的指南证据，列出需要关注的风险点。每个风险点必须挂接引用 id；无引用时 severity 不得高于 MEDIUM 并标注"证据不足"。禁止输出确定性诊断。',TRUE),
  (4,1,'你是安全审查助手。检查内容是否包含确定性诊断、处方建议、是否缺少免责声明、是否遗漏红旗症状、引用是否完整。输出审查结论和问题清单。确定性检查规则优先于本 Prompt。',TRUE),
  (5,1,'你是预问诊汇总助手。为医务人员生成结构化审核摘要，包括主诉、症状表、风险点、依据引用和建议关注方向。必须以免责声明结尾。不得替代医生做出最终结论。',TRUE);

-- ---------------- 模型配置 ----------------
INSERT INTO model_configs (name, provider, model_name, api_base, purpose, enabled, is_default, params) VALUES
  ('mock-local',     'mock',    'mock-llm-v1',    NULL,                     'CHAT', TRUE,  TRUE,  '{"temperature":0.3}'::jsonb),
  ('openai-compat',  'openai',  'gpt-4o-mini',    'https://api.openai.com/v1','CHAT', FALSE, FALSE, '{"temperature":0.2}'::jsonb);

-- ---------------- 知识库（7 篇公开来源指南，分块 embedding 由 ai-service 启动时补算） ----------------
INSERT INTO knowledge_documents (id, title, org, publish_date, doc_type, scope, source_note, version, status, uploaded_by) VALUES
  (1,'成人急性发热基层诊疗要点','国家卫健委',   '2022-03-01','GUIDELINE','成人急性发热','教学整理自公开指南',1,'ENABLED',1),
  (2,'胸痛鉴别诊断与红旗症状','中华医学会',     '2021-09-01','GUIDELINE','成人胸痛',    '教学整理自公开指南',1,'ENABLED',1),
  (3,'儿童急性上呼吸道感染基层管理','国家卫健委','2023-01-01','GUIDELINE','儿童',        '教学整理自公开指南',1,'ENABLED',1),
  (4,'高血压基层诊疗规范','国家基本公共卫生服务','2020-06-01','GUIDELINE','成人高血压',  '教学整理自公开指南',1,'ENABLED',1),
  (5,'2型糖尿病基层随访管理要点','中华医学会',   '2022-11-01','GUIDELINE','糖尿病',      '教学整理自公开指南',1,'ENABLED',1),
  (6,'孕期常见症状与警示信号','中华围产医学会',  '2021-05-01','GUIDELINE','孕产妇',      '教学整理自公开指南',1,'ENABLED',1),
  (7,'咳嗽的基层诊断与处理','中华医学会',       '2022-07-01','GUIDELINE','成人咳嗽',    '教学整理自公开指南',1,'ENABLED',1);
SELECT setval(pg_get_serial_sequence('knowledge_documents','id'), 7);

-- 分块（embedding 留 NULL，ai-service 启动时用 HashEmbedder 补算）
INSERT INTO knowledge_chunks (document_id, chunk_no, content, section, page_no, version, enabled) VALUES
  (1,1,'急性发热定义：体温≥38.0℃。评估应包括体温曲线、伴随症状、病程和全身状况。发热伴意识改变、呼吸困难、持续高热不退属危急情况，需立即转诊。','第一章 定义与评估',1,1,TRUE),
  (1,2,'低中度发热可观察和对症处理；高热（≥39.5℃）或持续超过3天建议就诊。特殊人群（孕妇、婴幼儿、老年人）发热阈值降低，需更积极评估。','第二章 处理原则',2,1,TRUE),
  (2,1,'胸痛伴呼吸困难、出汗、放射痛提示急性冠脉综合征可能，属红旗症状，需立即呼叫急救。常见鉴别包括心绞痛、心肌梗死、肺栓塞、主动脉夹层。','第一章 红旗症状',1,1,TRUE),
  (2,2,'胸痛评估应记录部位、性质、持续时间、诱因、缓解因素和伴随症状。心电图和心肌酶是初步评估手段。任何持续超过15分钟的胸痛均需急诊评估。','第二章 评估流程',2,1,TRUE),
  (2,3,'非典型胸痛（短暂、体位相关、按压加重）风险较低，但仍建议面诊排除器质性病因。不可仅凭预问诊信息排除心血管事件。','第三章 低危胸痛',3,1,TRUE),
  (3,1,'儿童急性上呼吸道感染以发热、鼻塞、流涕、咳嗽为主要表现。多数为病毒感染，病程自限。婴幼儿（<3月龄）发热≥38.0℃需立即就诊。','第一章 临床表现',1,1,TRUE),
  (3,2,'儿童出现呼吸急促、三凹征、精神萎靡、拒食、持续高热不退属红旗症状。需警惕肺炎、脓毒症等严重感染。','第二章 红旗症状',2,1,TRUE),
  (4,1,'高血压分级：1级140-159/90-99；2级160-179/100-109；3级≥180/110。血压≥180/110需紧急处理。降压目标一般<140/90，合并糖尿病者<130/80。','第一章 分级与目标',1,1,TRUE),
  (4,2,'高血压患者随访频率：血压稳定者每3个月一次；血压未达标者每2-4周一次。每次随访记录血压、用药、不良反应和靶器官损害评估。','第二章 随访管理',2,1,TRUE),
  (5,1,'2型糖尿病随访内容包括：血糖监测（空腹/餐后/HbA1c）、并发症筛查（眼底/足部/肾功能）、用药调整和生活方式指导。HbA1c目标一般<7.0%。','第一章 随访内容',1,1,TRUE),
  (5,2,'低血糖（血糖<3.9mmol/L）是常见急性并发症。反复低血糖提示需调整治疗方案。严重低血糖伴意识障碍需立即处理并急诊。','第二章 急性并发症',2,1,TRUE),
  (6,1,'孕期阴道出血在任何阶段均属警示信号。早孕期出血可能提示先兆流产或异位妊娠；中晚孕期出血可能提示胎盘问题。需立即就诊评估。','第一章 警示症状',1,1,TRUE),
  (6,2,'孕期常见不适包括恶心呕吐（早孕反应）、腰背痛、下肢水肿。若呕吐严重无法进食、剧烈腹痛、胎动减少需紧急就医。','第二章 常见症状',2,1,TRUE),
  (7,1,'咳嗽按病程分急性（<3周）、亚急性（3-8周）、慢性（>8周）。急性咳嗽最常见病因为上呼吸道感染。伴咯血、呼吸困难、持续高热属红旗症状。','第一章 分类与病因',1,1,TRUE),
  (7,2,'咳嗽处理原则：病因治疗为主，镇咳药谨慎使用。儿童不宜常规使用中枢镇咳药。咳嗽超过2周建议就诊排查原因。','第二章 处理原则',2,1,TRUE);

-- ---------------- 系统配置 ----------------
INSERT INTO system_configs (config_key, config_value, description) VALUES
  ('disclaimer',         '本系统为教学用辅助系统，所有 AI 生成内容仅供教学参考，不能替代医生诊断。', '全站免责声明'),
  ('knowledge_version',  'KV-1', '知识库版本号，每次摄取/启停/重建递增'),
  ('rule_version',       'RV-1', '规则版本号，每次规则变更递增'),
  ('login_max_attempts', '5',    '登录失败最大次数'),
  ('lock_minutes',       '15',   '锁定时长（分钟）'),
  ('max_upload_mb',      '10',   '最大上传文件大小（MB）');
