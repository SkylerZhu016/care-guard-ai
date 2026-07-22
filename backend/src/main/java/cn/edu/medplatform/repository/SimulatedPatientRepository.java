package cn.edu.medplatform.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cn.edu.medplatform.entity.*;
import java.util.List;

public interface SimulatedPatientRepository extends JpaRepository<SimulatedPatient, Long> {
    Page<SimulatedPatient> findByOwnerUserId(Long ownerUserId, Pageable p);
    @Query("SELECT p FROM SimulatedPatient p WHERE (:keyword IS NULL OR p.name LIKE %:keyword% OR p.patientNo LIKE %:keyword%)")
    Page<SimulatedPatient> search(@Param("keyword") String keyword, Pageable p);
}
