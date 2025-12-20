package biz.page1.ebr.repository;

import biz.page1.ebr.domain.batch.BatchStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface BatchStepRepository extends JpaRepository<BatchStep, Long> {

    @Query("SELECT bs FROM BatchStep bs " +
           "LEFT JOIN FETCH bs.parameterValues pv " +
           "LEFT JOIN FETCH pv.stepParameter " +
           "LEFT JOIN FETCH bs.recipeStep rs " +
           "LEFT JOIN FETCH rs.requiredQualifications " +
           "WHERE bs.id = :id")
    Optional<BatchStep> findByIdWithDetails(Long id);

    @Query("SELECT bs FROM BatchStep bs " +
           "LEFT JOIN FETCH bs.parameterValues pv " +
           "LEFT JOIN FETCH pv.stepParameter " +
           "WHERE bs.batch.id = :batchId AND bs.stepNumber = :stepNumber")
    Optional<BatchStep> findByBatchIdAndStepNumber(Long batchId, Integer stepNumber);
}
