package biz.page1.ebr.domain.batch;

import biz.page1.ebr.domain.recipe.MasterRecipe;
import biz.page1.ebr.domain.recipe.RecipeStep;
import biz.page1.ebr.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "batches")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String batchNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private MasterRecipe recipe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStatus status = BatchStatus.CREATED;

    private Double plannedQuantity;

    @Column(length = 20)
    private String quantityUnit;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNumber ASC")
    private List<BatchStep> steps = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime releasedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Batch(String batchNumber, MasterRecipe recipe, Double plannedQuantity, String quantityUnit) {
        this.batchNumber = batchNumber;
        this.recipe = recipe;
        this.plannedQuantity = plannedQuantity;
        this.quantityUnit = quantityUnit;

        // Create batch steps from recipe steps
        for (RecipeStep recipeStep : recipe.getSteps()) {
            BatchStep batchStep = new BatchStep(recipeStep, recipeStep.getStepNumber());
            batchStep.setBatch(this);
            this.steps.add(batchStep);
        }
    }

    public void release() {
        if (this.status != BatchStatus.CREATED) {
            throw new IllegalStateException("Only CREATED batches can be released");
        }
        this.status = BatchStatus.RELEASED;
        this.releasedAt = LocalDateTime.now();
    }

    public void start() {
        if (this.status != BatchStatus.RELEASED) {
            throw new IllegalStateException("Only RELEASED batches can be started");
        }
        this.status = BatchStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    public void complete() {
        if (this.status != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only IN_PROGRESS batches can be completed");
        }
        this.status = BatchStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status == BatchStatus.COMPLETED) {
            throw new IllegalStateException("COMPLETED batches cannot be cancelled");
        }
        this.status = BatchStatus.CANCELLED;
    }

    public BatchStep getStep(int stepNumber) {
        return steps.stream()
                .filter(s -> s.getStepNumber() == stepNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepNumber));
    }

    public BatchStep getCurrentStep() {
        return steps.stream()
                .filter(s -> s.getStatus() == StepStatus.IN_PROGRESS)
                .findFirst()
                .orElse(null);
    }

    public BatchStep getNextPendingStep() {
        return steps.stream()
                .filter(s -> s.getStatus() == StepStatus.PENDING)
                .findFirst()
                .orElse(null);
    }

    public boolean allStepsCompleted() {
        return steps.stream().allMatch(BatchStep::isCompleted);
    }

    public int getCompletedStepCount() {
        return (int) steps.stream().filter(BatchStep::isCompleted).count();
    }

    public int getTotalStepCount() {
        return steps.size();
    }

    public double getProgressPercentage() {
        if (steps.isEmpty()) return 0;
        return (double) getCompletedStepCount() / getTotalStepCount() * 100;
    }
}
