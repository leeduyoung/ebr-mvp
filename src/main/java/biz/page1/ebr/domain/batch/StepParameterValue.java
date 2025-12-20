package biz.page1.ebr.domain.batch;

import biz.page1.ebr.domain.recipe.StepParameter;
import biz.page1.ebr.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "step_parameter_values")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StepParameterValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_step_id", nullable = false)
    private BatchStep batchStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_parameter_id", nullable = false)
    private StepParameter stepParameter;

    @Column(columnDefinition = "TEXT")
    private String value;

    private LocalDateTime inputAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "input_by")
    private User inputBy;

    public StepParameterValue(StepParameter stepParameter) {
        this.stepParameter = stepParameter;
    }

    public void setValue(String value, User user) {
        this.value = value;
        this.inputBy = user;
        this.inputAt = LocalDateTime.now();
    }
}
