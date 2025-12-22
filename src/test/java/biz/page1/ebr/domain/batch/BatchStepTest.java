package biz.page1.ebr.domain.batch;

import biz.page1.ebr.domain.recipe.ParameterType;
import biz.page1.ebr.domain.recipe.RecipeStep;
import biz.page1.ebr.domain.recipe.StepParameter;
import biz.page1.ebr.domain.user.User;
import biz.page1.ebr.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * BatchStep 도메인 상태 머신 단위 테스트
 *
 * 상태 흐름: PENDING → IN_PROGRESS → COMPLETED
 *
 * 핵심 규칙:
 * - 스텝은 순서대로 실행되어야 함
 * - 스텝을 시작한 작업자가 완료해야 함
 */
class BatchStepTest {

    private RecipeStep recipeStep;
    private BatchStep batchStep;
    private User operator;

    @BeforeEach
    void setUp() {
        recipeStep = new RecipeStep(1, "Weighing", "Raw material weighing");

        StepParameter param1 = new StepParameter("Weight", ParameterType.NUMBER, true, 90.0, 110.0, "kg");
        StepParameter param2 = new StepParameter("Equipment ID", ParameterType.TEXT, true);
        recipeStep.addParameter(param1);
        recipeStep.addParameter(param2);

        batchStep = new BatchStep(recipeStep, 1);
        operator = new User("operator1", "password", "Operator 1", UserRole.OPERATOR);
    }

    @Nested
    @DisplayName("스텝 생성 테스트")
    class StepCreationTest {

        @Test
        @DisplayName("스텝 생성 시 PENDING 상태로 초기화된다")
        void newStep_shouldHavePendingStatus() {
            assertThat(batchStep.getStatus()).isEqualTo(StepStatus.PENDING);
            assertThat(batchStep.isPending()).isTrue();
        }

        @Test
        @DisplayName("스텝 생성 시 레시피 스텝의 파라미터들이 StepParameterValue로 복사된다")
        void newStep_shouldCopyParameters() {
            assertThat(batchStep.getParameterValues()).hasSize(2);
        }

        @Test
        @DisplayName("스텝 생성 시 작업자는 null이다")
        void newStep_operatorShouldBeNull() {
            assertThat(batchStep.getOperator()).isNull();
        }

        @Test
        @DisplayName("스텝 생성 시 시작/완료 시간은 null이다")
        void newStep_timestampsShouldBeNull() {
            assertThat(batchStep.getStartedAt()).isNull();
            assertThat(batchStep.getCompletedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("start() 테스트 - 스텝 시작")
    class StartTest {

        @Test
        @DisplayName("PENDING 상태에서 start() 호출 시 IN_PROGRESS 상태로 전환된다")
        void start_fromPending_shouldChangeToInProgress() {
            batchStep.start(operator);

            assertThat(batchStep.getStatus()).isEqualTo(StepStatus.IN_PROGRESS);
            assertThat(batchStep.isInProgress()).isTrue();
        }

        @Test
        @DisplayName("start() 호출 시 작업자가 저장된다")
        void start_shouldSaveOperator() {
            batchStep.start(operator);

            assertThat(batchStep.getOperator()).isEqualTo(operator);
        }

        @Test
        @DisplayName("start() 호출 시 시작 시간이 기록된다")
        void start_shouldRecordStartTime() {
            batchStep.start(operator);

            assertThat(batchStep.getStartedAt()).isNotNull();
        }

        @Test
        @DisplayName("IN_PROGRESS 상태에서 start() 호출 시 예외가 발생한다")
        void start_fromInProgress_shouldThrowException() {
            batchStep.start(operator);

            assertThatThrownBy(() -> batchStep.start(operator))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Step can only be started from PENDING status");
        }

        @Test
        @DisplayName("COMPLETED 상태에서 start() 호출 시 예외가 발생한다")
        void start_fromCompleted_shouldThrowException() {
            batchStep.start(operator);
            batchStep.complete();

            assertThatThrownBy(() -> batchStep.start(operator))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Step can only be started from PENDING status");
        }
    }

    @Nested
    @DisplayName("complete() 테스트 - 스텝 완료")
    class CompleteTest {

        @Test
        @DisplayName("IN_PROGRESS 상태에서 complete() 호출 시 COMPLETED 상태로 전환된다")
        void complete_fromInProgress_shouldChangeToCompleted() {
            batchStep.start(operator);

            batchStep.complete();

            assertThat(batchStep.getStatus()).isEqualTo(StepStatus.COMPLETED);
            assertThat(batchStep.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("complete() 호출 시 완료 시간이 기록된다")
        void complete_shouldRecordCompletionTime() {
            batchStep.start(operator);

            batchStep.complete();

            assertThat(batchStep.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("PENDING 상태에서 complete() 호출 시 예외가 발생한다")
        void complete_fromPending_shouldThrowException() {
            assertThatThrownBy(() -> batchStep.complete())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Step can only be completed from IN_PROGRESS status");
        }

        @Test
        @DisplayName("COMPLETED 상태에서 complete() 호출 시 예외가 발생한다")
        void complete_fromCompleted_shouldThrowException() {
            batchStep.start(operator);
            batchStep.complete();

            assertThatThrownBy(() -> batchStep.complete())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Step can only be completed from IN_PROGRESS status");
        }
    }

    @Nested
    @DisplayName("상태 확인 메서드 테스트")
    class StatusCheckTest {

        @Test
        @DisplayName("isPending()은 PENDING 상태에서만 true를 반환한다")
        void isPending_shouldReturnTrueOnlyForPendingStatus() {
            assertThat(batchStep.isPending()).isTrue();

            batchStep.start(operator);
            assertThat(batchStep.isPending()).isFalse();
        }

        @Test
        @DisplayName("isInProgress()는 IN_PROGRESS 상태에서만 true를 반환한다")
        void isInProgress_shouldReturnTrueOnlyForInProgressStatus() {
            assertThat(batchStep.isInProgress()).isFalse();

            batchStep.start(operator);
            assertThat(batchStep.isInProgress()).isTrue();

            batchStep.complete();
            assertThat(batchStep.isInProgress()).isFalse();
        }

        @Test
        @DisplayName("isCompleted()는 COMPLETED 상태에서만 true를 반환한다")
        void isCompleted_shouldReturnTrueOnlyForCompletedStatus() {
            assertThat(batchStep.isCompleted()).isFalse();

            batchStep.start(operator);
            assertThat(batchStep.isCompleted()).isFalse();

            batchStep.complete();
            assertThat(batchStep.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("파라미터 값 테스트")
    class ParameterValueTest {

        @Test
        @DisplayName("스텝 생성 시 파라미터 값들은 빈 상태이다")
        void newStep_parameterValuesShouldBeEmpty() {
            for (StepParameterValue pv : batchStep.getParameterValues()) {
                assertThat(pv.getValue()).isNull();
                assertThat(pv.getInputBy()).isNull();
                assertThat(pv.getInputAt()).isNull();
            }
        }

        @Test
        @DisplayName("파라미터 값 설정 시 값, 입력자, 입력 시간이 기록된다")
        void setValue_shouldRecordValueAndMetadata() {
            StepParameterValue paramValue = batchStep.getParameterValues().get(0);

            paramValue.setValue("100.5", operator);

            assertThat(paramValue.getValue()).isEqualTo("100.5");
            assertThat(paramValue.getInputBy()).isEqualTo(operator);
            assertThat(paramValue.getInputAt()).isNotNull();
        }
    }
}
