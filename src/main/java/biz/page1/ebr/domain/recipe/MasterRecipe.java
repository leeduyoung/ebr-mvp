package biz.page1.ebr.domain.recipe;

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
@Table(name = "master_recipes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MasterRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecipeStatus status = RecipeStatus.DRAFT;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNumber ASC")
    private List<RecipeStep> steps = new ArrayList<>();

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public MasterRecipe(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public void addStep(RecipeStep step) {
        steps.add(step);
        step.setRecipe(this);
    }

    public void approve() {
        if (this.status != RecipeStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT recipes can be approved");
        }
        this.status = RecipeStatus.APPROVED;
    }

    public boolean isApproved() {
        return this.status == RecipeStatus.APPROVED;
    }

    public int getNextStepNumber() {
        return steps.size() + 1;
    }
}
