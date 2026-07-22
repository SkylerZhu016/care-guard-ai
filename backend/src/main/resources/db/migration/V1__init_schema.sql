-- V1__init_schema.sql
-- 基层医疗安全型预问诊与随访平台 - 数据库初始化脚本

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(100),
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_role (role),
    INDEX idx_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS simulated_patients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    age INT NOT NULL,
    gender VARCHAR(10) NOT NULL,
    medical_history TEXT,
    allergies TEXT,
    blood_type VARCHAR(20),
    synthetic_profile TEXT COMMENT 'JSON - 模拟患者完整档案',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_patients_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS visits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    chief_complaint TEXT,
    structured_symptoms TEXT COMMENT 'JSON - 结构化症状数据',
    risk_level INT COMMENT '1-5 风险等级',
    triage_result TEXT,
    doctor_notes TEXT,
    agent_run_id VARCHAR(50),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (patient_id) REFERENCES simulated_patients(id),
    FOREIGN KEY (doctor_id) REFERENCES users(id),
    INDEX idx_visits_status (status),
    INDEX idx_visits_patient (patient_id),
    INDEX idx_visits_doctor (doctor_id),
    INDEX idx_visits_risk (risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS symptoms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_id BIGINT NOT NULL,
    symptom_name VARCHAR(100) NOT NULL,
    body_part VARCHAR(20),
    severity INT COMMENT '1-10',
    duration VARCHAR(50),
    description TEXT,
    onset_time DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE CASCADE,
    INDEX idx_symptoms_visit (visit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS triage_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_id BIGINT NOT NULL UNIQUE,
    risk_score INT NOT NULL,
    risk_level VARCHAR(20) COMMENT 'LOW, MEDIUM, HIGH, CRITICAL',
    risk_factors TEXT COMMENT 'JSON - 风险因素列表',
    red_flags TEXT COMMENT 'JSON - 红旗症状',
    recommendations TEXT,
    safety_checked BOOLEAN DEFAULT FALSE,
    safety_report TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS medical_guidelines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    content TEXT,
    embedding_vector TEXT COMMENT 'JSON - 向量嵌入',
    source VARCHAR(50),
    version VARCHAR(20),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_guidelines_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS followup_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_id BIGINT NOT NULL,
    plan_name VARCHAR(200) NOT NULL,
    description TEXT,
    interval_days INT NOT NULL,
    total_times INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE CASCADE,
    INDEX idx_followup_visit (visit_id),
    INDEX idx_followup_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS followup_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    assignee_id BIGINT,
    due_date DATE NOT NULL,
    questionnaire TEXT,
    patient_response TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (plan_id) REFERENCES followup_plans(id) ON DELETE CASCADE,
    FOREIGN KEY (assignee_id) REFERENCES users(id),
    INDEX idx_tasks_plan (plan_id),
    INDEX idx_tasks_assignee (assignee_id),
    INDEX idx_tasks_due (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS safety_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_id BIGINT,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(200),
    description TEXT,
    evidence TEXT,
    reviewed BOOLEAN DEFAULT FALSE,
    reviewed_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE SET NULL,
    FOREIGN KEY (reviewed_by) REFERENCES users(id),
    INDEX idx_alerts_reviewed (reviewed),
    INDEX idx_alerts_severity (severity),
    INDEX idx_alerts_type (alert_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(50) NOT NULL UNIQUE,
    agent_name VARCHAR(100),
    model_version VARCHAR(50),
    prompt_version VARCHAR(50),
    kb_version VARCHAR(50),
    input_data TEXT,
    output_data TEXT,
    status VARCHAR(20) NOT NULL,
    error_log TEXT,
    latency_ms BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_agent_run_id (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS citations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_run_id BIGINT,
    source_content TEXT,
    source_name VARCHAR(200),
    source_url VARCHAR(100),
    relevance_score INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (agent_run_id) REFERENCES agent_runs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    action VARCHAR(50) NOT NULL,
    resource VARCHAR(100),
    resource_id VARCHAR(100),
    detail TEXT,
    ip_address VARCHAR(45),
    success BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
