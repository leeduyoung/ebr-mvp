package biz.page1.ebr.service;

import biz.page1.ebr.domain.recipe.*;
import biz.page1.ebr.domain.user.Qualification;
import biz.page1.ebr.domain.user.User;
import biz.page1.ebr.repository.MasterRecipeRepository;
import biz.page1.ebr.repository.QualificationRepository;
import biz.page1.ebr.repository.RecipeStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

    private final MasterRecipeRepository recipeRepository;
    private final RecipeStepRepository stepRepository;
    private final QualificationRepository qualificationRepository;

    public List<MasterRecipe> findAll() {
        return recipeRepository.findAllByOrderByCreatedAtDesc();
    }

    public MasterRecipe findById(Long id) {
        return recipeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Recipe not found: " + id));
    }

    public MasterRecipe findByIdWithSteps(Long id) {
        return recipeRepository.findByIdWithSteps(id)
                .orElseThrow(() -> new IllegalArgumentException("Recipe not found: " + id));
    }

    public List<MasterRecipe> findApprovedRecipes() {
        return recipeRepository.findByStatus(RecipeStatus.APPROVED);
    }

    @Transactional
    public MasterRecipe createRecipe(String code, String name, String description, User createdBy) {
        if (recipeRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Recipe code already exists: " + code);
        }

        MasterRecipe recipe = new MasterRecipe(code, name, description);
        recipe.setCreatedBy(createdBy);
        return recipeRepository.save(recipe);
    }

    @Transactional
    public MasterRecipe updateRecipe(Long id, String name, String description) {
        MasterRecipe recipe = findById(id);
        if (recipe.getStatus() != RecipeStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT recipes can be modified");
        }
        recipe.setName(name);
        recipe.setDescription(description);
        return recipe;
    }

    @Transactional
    public void approveRecipe(Long id) {
        MasterRecipe recipe = findById(id);
        if (recipe.getSteps().isEmpty()) {
            throw new IllegalStateException("Recipe must have at least one step to be approved");
        }
        recipe.approve();
    }

    @Transactional
    public RecipeStep addStep(Long recipeId, String name, String description, List<Long> qualificationIds) {
        MasterRecipe recipe = findById(recipeId);
        if (recipe.getStatus() != RecipeStatus.DRAFT) {
            throw new IllegalStateException("Cannot add steps to non-DRAFT recipes");
        }

        RecipeStep step = new RecipeStep(recipe.getNextStepNumber(), name, description);
        recipe.addStep(step);

        if (qualificationIds != null && !qualificationIds.isEmpty()) {
            Set<Qualification> qualifications = qualificationIds.stream()
                    .map(qid -> qualificationRepository.findById(qid)
                            .orElseThrow(() -> new IllegalArgumentException("Qualification not found: " + qid)))
                    .collect(Collectors.toSet());
            qualifications.forEach(step::addRequiredQualification);
        }

        return stepRepository.save(step);
    }

    @Transactional
    public StepParameter addParameter(Long stepId, String name, ParameterType type,
                                       boolean required, Double minValue, Double maxValue, String unit) {
        RecipeStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepId));

        if (step.getRecipe().getStatus() != RecipeStatus.DRAFT) {
            throw new IllegalStateException("Cannot add parameters to non-DRAFT recipes");
        }

        StepParameter parameter = new StepParameter(name, type, required, minValue, maxValue, unit);
        step.addParameter(parameter);
        return parameter;
    }

    @Transactional
    public void deleteStep(Long recipeId, Long stepId) {
        MasterRecipe recipe = findByIdWithSteps(recipeId);
        if (recipe.getStatus() != RecipeStatus.DRAFT) {
            throw new IllegalStateException("Cannot delete steps from non-DRAFT recipes");
        }

        recipe.getSteps().removeIf(s -> s.getId().equals(stepId));

        // Re-number remaining steps
        int stepNum = 1;
        for (RecipeStep step : recipe.getSteps()) {
            step.setStepNumber(stepNum++);
        }
    }
}
