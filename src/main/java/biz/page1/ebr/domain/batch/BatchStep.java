package biz.page1.ebr.domain.batch;

import biz.page1.ebr.domain.recipe.RecipeStep;
import biz.page1.ebr.domain.recipe.StepParameter;
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
@Table(name = "batch_steps", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"batch_id", "step_number"})
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_step_id", nullable = false)
    private RecipeStep recipeStep;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepStatus status = StepStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @OneToMany(mappedBy = "batchStep", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StepParameterValue> parameterValues = new ArrayList<>();

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public BatchStep(RecipeStep recipeStep, Integer stepNumber) {
        this.recipeStep = recipeStep;
        this.stepNumber = stepNumber;

        // Create parameter value placeholders
        for (StepParameter param : recipeStep.getParameters()) {
            StepParameterValue pv = new StepParameterValue(param);
            pv.setBatchStep(this);
            this.parameterValues.add(pv);
        }
    }

    public void start(User operator) {
        if (this.status != StepStatus.PENDING) {
            throw new IllegalStateException("Step can only be started from PENDING status");
        }
        this.status = StepStatus.IN_PROGRESS;
        this.operator = operator;
        this.startedAt = LocalDateTime.now();
    }

    public void complete() {
        if (this.status != StepStatus.IN_PROGRESS) {
            throw new IllegalStateException("Step can only be completed from IN_PROGRESS status");
        }
        this.status = StepStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public boolean isCompleted() {
        return this.status == StepStatus.COMPLETED;
    }

    public boolean isPending() {
        return this.status == StepStatus.PENDING;
    }

    public boolean isInProgress() {
        return this.status == StepStatus.IN_PROGRESS;
    }
}
