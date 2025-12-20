package biz.page1.ebr.domain.recipe;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "step_parameters")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StepParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_step_id", nullable = false)
    private RecipeStep recipeStep;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParameterType parameterType = ParameterType.TEXT;

    private boolean required = true;

    private Double minValue;
    private Double maxValue;

    @Column(length = 20)
    private String unit;

    private Integer displayOrder;

    public StepParameter(String name, ParameterType parameterType, boolean required) {
        this.name = name;
        this.parameterType = parameterType;
        this.required = required;
    }

    public StepParameter(String name, ParameterType parameterType, boolean required,
                         Double minValue, Double maxValue, String unit) {
        this.name = name;
        this.parameterType = parameterType;
        this.required = required;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.unit = unit;
    }
}
