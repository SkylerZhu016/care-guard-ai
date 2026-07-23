package cn.edu.medplatform.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cn.edu.medplatform.entity.*;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @Query("SELECT a FROM AuditLog a WHERE (a.username LIKE %:username% OR :username = '') AND (a.action LIKE %:action% OR :action = '') ORDER BY a.createdAt DESC")
    Page<AuditLog> search(@Param("username") String username, @Param("action") String action, Pageable p);
}
