package biz.page1.ebr.service;

import biz.page1.ebr.domain.batch.Batch;
import biz.page1.ebr.domain.batch.BatchStatus;
import biz.page1.ebr.domain.recipe.MasterRecipe;
import biz.page1.ebr.domain.recipe.RecipeStatus;
import biz.page1.ebr.domain.user.User;
import biz.page1.ebr.repository.BatchRepository;
import biz.page1.ebr.repository.MasterRecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BatchService {

    private final BatchRepository batchRepository;
    private final MasterRecipeRepository recipeRepository;

    public List<Batch> findAll() {
        return batchRepository.findAllByOrderByCreatedAtDesc();
    }

    public Batch findById(Long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + id));
    }

    public Batch findByIdWithSteps(Long id) {
        return batchRepository.findByIdWithSteps(id)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + id));
    }

    public List<Batch> findExecutableBatches() {
        return batchRepository.findByStatusInWithRecipe(
                List.of(BatchStatus.RELEASED, BatchStatus.IN_PROGRESS)
        );
    }

    public long countByStatus(BatchStatus status) {
        return batchRepository.countByStatus(status);
    }

    @Transactional
    public Batch createBatch(Long recipeId, Double plannedQuantity, String quantityUnit, User createdBy) {
        MasterRecipe recipe = recipeRepository.findByIdWithSteps(recipeId)
                .orElseThrow(() -> new IllegalArgumentException("Recipe not found: " + recipeId));

        if (recipe.getStatus() != RecipeStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED recipes can be used to create batches");
        }

        String batchNumber = generateBatchNumber();
        Batch batch = new Batch(batchNumber, recipe, plannedQuantity, quantityUnit);
        batch.setCreatedBy(createdBy);

        return batchRepository.save(batch);
    }

    @Transactional
    public void releaseBatch(Long id) {
        Batch batch = findById(id);
        batch.release();
    }

    @Transactional
    public void cancelBatch(Long id) {
        Batch batch = findById(id);
        batch.cancel();
    }

    private String generateBatchNumber() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = batchRepository.count() + 1;
        return String.format("BATCH-%s-%04d", datePrefix, count);
    }
}
