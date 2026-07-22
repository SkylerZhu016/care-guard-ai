package cn.edu.medplatform.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cn.edu.medplatform.entity.*;
import java.util.List;

public interface FollowupRecordRepository extends JpaRepository<FollowupRecord, Long> {
    List<FollowupRecord> findByPatientIdOrderByCreatedAtAsc(Long patientId);
    List<FollowupRecord> findByTaskId(Long taskId);
}
