package biz.page1.ebr.repository;

import biz.page1.ebr.domain.recipe.RecipeStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RecipeStepRepository extends JpaRepository<RecipeStep, Long> {

    @Query("SELECT rs FROM RecipeStep rs LEFT JOIN FETCH rs.parameters WHERE rs.id = :id")
    Optional<RecipeStep> findByIdWithParameters(Long id);

    List<RecipeStep> findByRecipeIdOrderByStepNumberAsc(Long recipeId);
}
