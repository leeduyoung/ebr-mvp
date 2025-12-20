package biz.page1.ebr.repository;

import biz.page1.ebr.domain.recipe.MasterRecipe;
import biz.page1.ebr.domain.recipe.RecipeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MasterRecipeRepository extends JpaRepository<MasterRecipe, Long> {
    Optional<MasterRecipe> findByCode(String code);
    boolean existsByCode(String code);
    List<MasterRecipe> findByStatus(RecipeStatus status);

    @Query("SELECT r FROM MasterRecipe r LEFT JOIN FETCH r.steps WHERE r.id = :id")
    Optional<MasterRecipe> findByIdWithSteps(Long id);

    List<MasterRecipe> findAllByOrderByCreatedAtDesc();
}
