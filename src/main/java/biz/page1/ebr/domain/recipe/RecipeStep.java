package biz.page1.ebr.domain.recipe;

import biz.page1.ebr.domain.user.Qualification;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "recipe_steps", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"recipe_id", "step_number"})
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private MasterRecipe recipe;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "step_required_qualifications",
            joinColumns = @JoinColumn(name = "recipe_step_id"),
            inverseJoinColumns = @JoinColumn(name = "qualification_id")
    )
    private Set<Qualification> requiredQualifications = new HashSet<>();

    @OneToMany(mappedBy = "recipeStep", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<StepParameter> parameters = new ArrayList<>();

    public RecipeStep(Integer stepNumber, String name, String description) {
        this.stepNumber = stepNumber;
        this.name = name;
        this.description = description;
    }

    public void addParameter(StepParameter parameter) {
        parameters.add(parameter);
        parameter.setRecipeStep(this);
        parameter.setDisplayOrder(parameters.size());
    }

    public void addRequiredQualification(Qualification qualification) {
        requiredQualifications.add(qualification);
    }
}
