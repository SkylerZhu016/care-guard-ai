package cn.edu.medplatform.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cn.edu.medplatform.entity.*;
import java.util.List;

public interface RuleDefinitionRepository extends JpaRepository<RuleDefinition, Long> {
    List<RuleDefinition> findByEnabledTrueAndDeletedFalseOrderByPriorityAsc();
    @Query("SELECT r FROM RuleDefinition r WHERE r.deleted = false AND (:category IS NULL OR r.category = :category) AND (:enabled IS NULL OR r.enabled = :enabled)")
    Page<RuleDefinition> search(@Param("category") String category, @Param("enabled") Boolean enabled, Pageable p);
}
