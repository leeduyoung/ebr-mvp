package biz.page1.ebr.repository;

import biz.page1.ebr.domain.batch.Batch;
import biz.page1.ebr.domain.batch.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {
    Optional<Batch> findByBatchNumber(String batchNumber);
    boolean existsByBatchNumber(String batchNumber);

    List<Batch> findByStatus(BatchStatus status);
    List<Batch> findByStatusIn(List<BatchStatus> statuses);

    @Query("SELECT b FROM Batch b LEFT JOIN FETCH b.steps WHERE b.id = :id")
    Optional<Batch> findByIdWithSteps(Long id);

    @Query("SELECT b FROM Batch b LEFT JOIN FETCH b.recipe WHERE b.status IN :statuses ORDER BY b.createdAt DESC")
    List<Batch> findByStatusInWithRecipe(List<BatchStatus> statuses);

    @Query("SELECT b FROM Batch b LEFT JOIN FETCH b.recipe ORDER BY b.createdAt DESC")
    List<Batch> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COUNT(b) FROM Batch b WHERE b.status = :status")
    long countByStatus(BatchStatus status);
}
