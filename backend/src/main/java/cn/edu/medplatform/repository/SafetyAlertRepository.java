package cn.edu.medplatform.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cn.edu.medplatform.entity.*;
import java.util.List;

public interface SafetyAlertRepository extends JpaRepository<SafetyAlert, Long> {
    @Query("SELECT a FROM SafetyAlert a WHERE (:type IS NULL OR a.type = :type) AND (:status IS NULL OR a.status = :status) ORDER BY a.createdAt DESC")
    Page<SafetyAlert> search(@Param("type") String type, @Param("status") String status, Pageable p);
}
