package biz.page1.ebr.service;

import biz.page1.ebr.domain.batch.*;
import biz.page1.ebr.domain.recipe.ParameterType;
import biz.page1.ebr.domain.recipe.RecipeStep;
import biz.page1.ebr.domain.recipe.StepParameter;
import biz.page1.ebr.domain.user.Qualification;
import biz.page1.ebr.domain.user.User;
import biz.page1.ebr.repository.BatchRepository;
import biz.page1.ebr.repository.BatchStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExecutionService {

    private final BatchRepository batchRepository;
    private final BatchStepRepository batchStepRepository;

    public List<Batch> findExecutableBatches() {
        return batchRepository.findByStatusInWithRecipe(
                List.of(BatchStatus.RELEASED, BatchStatus.IN_PROGRESS)
        );
    }

    public Batch findBatchWithSteps(Long batchId) {
        return batchRepository.findByIdWithSteps(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
    }

    public BatchStep findStepWithDetails(Long batchId, int stepNumber) {
        return batchStepRepository.findByBatchIdAndStepNumber(batchId, stepNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Step not found: batch=" + batchId + ", step=" + stepNumber));
    }

    /**
     * Check if operator can start the given step
     */
    public StepValidationResult canStartStep(Batch batch, int stepNumber, User operator) {
        List<String> errors = new ArrayList<>();

        // 1. Check batch status
        if (batch.getStatus() != BatchStatus.RELEASED && batch.getStatus() != BatchStatus.IN_PROGRESS) {
            errors.add("Batch is not in executable status: " + batch.getStatus());
            return new StepValidationResult(false, errors);
        }

        BatchStep step = batch.getStep(stepNumber);

        // 2. Check step status
        if (step.getStatus() != StepStatus.PENDING) {
            errors.add("Step is not in PENDING status: " + step.getStatus());
            return new StepValidationResult(false, errors);
        }

        // 3. Check previous steps are completed (step sequence enforcement)
        if (stepNumber > 1) {
            BatchStep previousStep = batch.getStep(stepNumber - 1);
            if (!previousStep.isCompleted()) {
                errors.add("Previous step (Step " + (stepNumber - 1) + ") is not completed");
                return new StepValidationResult(false, errors);
            }
        }

        // 4. Check operator qualifications
        RecipeStep recipeStep = step.getRecipeStep();
        Set<Qualification> requiredQualifications = recipeStep.getRequiredQualifications();
        if (!requiredQualifications.isEmpty()) {
            if (!operator.hasAllQualifications(requiredQualifications)) {
                List<String> missingQualifications = requiredQualifications.stream()
                        .filter(q -> !operator.hasQualification(q))
                        .map(Qualification::getName)
                        .toList();
                errors.add("Missing required qualifications: " + String.join(", ", missingQualifications));
            }
        }

        return new StepValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Start a step
     */
    @Transactional
    public BatchStep startStep(Long batchId, int stepNumber, User operator) {
        Batch batch = findBatchWithSteps(batchId);

        StepValidationResult validation = canStartStep(batch, stepNumber, operator);
        if (!validation.isValid()) {
            throw new IllegalStateException("Cannot start step: " + String.join(", ", validation.getErrors()));
        }

        BatchStep step = batch.getStep(stepNumber);
        step.start(operator);

        // If this is the first step, start the batch
        if (batch.getStatus() == BatchStatus.RELEASED) {
            batch.start();
        }

        return step;
    }

    /**
     * Validate parameter values
     */
    public List<String> validateParameters(BatchStep step, Map<Long, String> parameterValues) {
        List<String> errors = new ArrayList<>();

        for (StepParameterValue pv : step.getParameterValues()) {
            StepParameter param = pv.getStepParameter();
            String value = parameterValues.get(param.getId());

            // Check required
            if (param.isRequired() && (value == null || value.isBlank())) {
                errors.add("Required field: " + param.getName());
                continue;
            }

            if (value == null || value.isBlank()) {
                continue;
            }

            // Validate by type
            if (param.getParameterType() == ParameterType.NUMBER) {
                try {
                    double numValue = Double.parseDouble(value);

                    if (param.getMinValue() != null && numValue < param.getMinValue()) {
                        errors.add(String.format("%s: value %.2f is below minimum %.2f",
                                param.getName(), numValue, param.getMinValue()));
                    }
                    if (param.getMaxValue() != null && numValue > param.getMaxValue()) {
                        errors.add(String.format("%s: value %.2f exceeds maximum %.2f",
                                param.getName(), numValue, param.getMaxValue()));
                    }
                } catch (NumberFormatException e) {
                    errors.add(param.getName() + ": must be a valid number");
                }
            }
        }

        return errors;
    }

    /**
     * Complete a step with parameter values
     */
    @Transactional
    public BatchStep completeStep(Long batchId, int stepNumber, Map<Long, String> parameterValues, User operator) {
        Batch batch = findBatchWithSteps(batchId);
        BatchStep step = batch.getStep(stepNumber);

        // Check step is in progress
        if (!step.isInProgress()) {
            throw new IllegalStateException("Step is not in progress");
        }

        // Check operator is the one who started
        if (!step.getOperator().getId().equals(operator.getId())) {
            throw new IllegalStateException("Step must be completed by the same operator who started it");
        }

        // Validate parameters
        List<String> validationErrors = validateParameters(step, parameterValues);
        if (!validationErrors.isEmpty()) {
            throw new IllegalArgumentException("Validation errors: " + String.join(", ", validationErrors));
        }

        // Save parameter values
        for (StepParameterValue pv : step.getParameterValues()) {
            String value = parameterValues.get(pv.getStepParameter().getId());
            if (value != null && !value.isBlank()) {
                pv.setValue(value, operator);
            }
        }

        // Complete step
        step.complete();

        // If all steps completed, complete the batch
        if (batch.allStepsCompleted()) {
            batch.complete();
        }

        return step;
    }

    /**
     * Simple result class for validation
     */
    public record StepValidationResult(boolean isValid, List<String> errors) {
        public boolean isValid() {
            return isValid;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}
