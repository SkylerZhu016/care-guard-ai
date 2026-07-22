package cn.edu.medplatform.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cn.edu.medplatform.entity.*;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @Query("SELECT a FROM AuditLog a WHERE (:username IS NULL OR a.username LIKE %:username%) AND (:action IS NULL OR a.action LIKE %:action%) AND (:dateFrom IS NULL OR a.createdAt >= :dateFrom) AND (:dateTo IS NULL OR a.createdAt <= :dateTo) ORDER BY a.createdAt DESC")
    Page<AuditLog> search(@Param("username") String username, @Param("action") String action, @Param("dateFrom") java.time.LocalDateTime dateFrom, @Param("dateTo") java.time.LocalDateTime dateTo, Pageable p);
}
