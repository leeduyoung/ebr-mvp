package biz.page1.ebr.domain.batch;

import biz.page1.ebr.domain.recipe.MasterRecipe;
import biz.page1.ebr.domain.recipe.RecipeStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Batch 도메인 상태 머신 단위 테스트
 *
 * 상태 흐름: CREATED → RELEASED → IN_PROGRESS → COMPLETED
 *          (CANCELLED는 COMPLETED 제외 모든 상태에서 가능)
 */
class BatchTest {

    private MasterRecipe recipe;
    private Batch batch;

    @BeforeEach
    void setUp() {
        recipe = new MasterRecipe("RCP-001", "Test Recipe", "Description");

        RecipeStep step1 = new RecipeStep(1, "Step 1", "First step");
        RecipeStep step2 = new RecipeStep(2, "Step 2", "Second step");
        recipe.addStep(step1);
        recipe.addStep(step2);

        batch = new Batch("BATCH-001", recipe, 100.0, "kg");
    }

    @Nested
    @DisplayName("배치 생성 테스트")
    class BatchCreationTest {

        @Test
        @DisplayName("배치 생성 시 CREATED 상태로 초기화된다")
        void newBatch_shouldHaveCreatedStatus() {
            assertThat(batch.getStatus()).isEqualTo(BatchStatus.CREATED);
        }

        @Test
        @DisplayName("배치 생성 시 레시피의 스텝들이 BatchStep으로 복사된다")
        void newBatch_shouldCopyRecipeSteps() {
            assertThat(batch.getSteps()).hasSize(2);
            assertThat(batch.getSteps().get(0).getStepNumber()).isEqualTo(1);
            assertThat(batch.getSteps().get(1).getStepNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("배치 생성 시 모든 스텝은 PENDING 상태이다")
        void newBatch_allStepsShouldBePending() {
            assertThat(batch.getSteps())
                    .allMatch(step -> step.getStatus() == StepStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("release() 테스트 - 배치 출시")
    class ReleaseTest {

        @Test
        @DisplayName("CREATED 상태에서 release() 호출 시 RELEASED 상태로 전환된다")
        void release_fromCreated_shouldChangeToReleased() {
            batch.release();

            assertThat(batch.getStatus()).isEqualTo(BatchStatus.RELEASED);
            assertThat(batch.getReleasedAt()).isNotNull();
        }

        @Test
        @DisplayName("RELEASED 상태에서 release() 호출 시 예외가 발생한다")
        void release_fromReleased_shouldThrowException() {
            batch.release();

            assertThatThrownBy(() -> batch.release())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only CREATED batches can be released");
        }

        @Test
        @DisplayName("IN_PROGRESS 상태에서 release() 호출 시 예외가 발생한다")
        void release_fromInProgress_shouldThrowException() {
            batch.release();
            batch.start();

            assertThatThrownBy(() -> batch.release())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only CREATED batches can be released");
        }

        @Test
        @DisplayName("COMPLETED 상태에서 release() 호출 시 예외가 발생한다")
        void release_fromCompleted_shouldThrowException() {
            batch.release();
            batch.start();
            batch.complete();

            assertThatThrownBy(() -> batch.release())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only CREATED batches can be released");
        }
    }

    @Nested
    @DisplayName("start() 테스트 - 배치 시작")
    class StartTest {

        @Test
        @DisplayName("RELEASED 상태에서 start() 호출 시 IN_PROGRESS 상태로 전환된다")
        void start_fromReleased_shouldChangeToInProgress() {
            batch.release();

            batch.start();

            assertThat(batch.getStatus()).isEqualTo(BatchStatus.IN_PROGRESS);
            assertThat(batch.getStartedAt()).isNotNull();
        }

        @Test
        @DisplayName("CREATED 상태에서 start() 호출 시 예외가 발생한다")
        void start_fromCreated_shouldThrowException() {
            assertThatThrownBy(() -> batch.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only RELEASED batches can be started");
        }

        @Test
        @DisplayName("IN_PROGRESS 상태에서 start() 호출 시 예외가 발생한다")
        void start_fromInProgress_shouldThrowException() {
            batch.release();
            batch.start();

            assertThatThrownBy(() -> batch.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only RELEASED batches can be started");
        }
    }

    @Nested
    @DisplayName("complete() 테스트 - 배치 완료")
    class CompleteTest {

        @Test
        @DisplayName("IN_PROGRESS 상태에서 complete() 호출 시 COMPLETED 상태로 전환된다")
        void complete_fromInProgress_shouldChangeToCompleted() {
            batch.release();
            batch.start();

            batch.complete();

            assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(batch.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("CREATED 상태에서 complete() 호출 시 예외가 발생한다")
        void complete_fromCreated_shouldThrowException() {
            assertThatThrownBy(() -> batch.complete())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only IN_PROGRESS batches can be completed");
        }

        @Test
        @DisplayName("RELEASED 상태에서 complete() 호출 시 예외가 발생한다")
        void complete_fromReleased_shouldThrowException() {
            batch.release();

            assertThatThrownBy(() -> batch.complete())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only IN_PROGRESS batches can be completed");
        }
    }

    @Nested
    @DisplayName("cancel() 테스트 - 배치 취소")
    class CancelTest {

        @Test
        @DisplayName("CREATED 상태에서 cancel() 호출 시 CANCELLED 상태로 전환된다")
        void cancel_fromCreated_shouldChangeToCancelled() {
            batch.cancel();

            assertThat(batch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
        }

        @Test
        @DisplayName("RELEASED 상태에서 cancel() 호출 시 CANCELLED 상태로 전환된다")
        void cancel_fromReleased_shouldChangeToCancelled() {
            batch.release();

            batch.cancel();

            assertThat(batch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
        }

        @Test
        @DisplayName("IN_PROGRESS 상태에서 cancel() 호출 시 CANCELLED 상태로 전환된다")
        void cancel_fromInProgress_shouldChangeToCancelled() {
            batch.release();
            batch.start();

            batch.cancel();

            assertThat(batch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
        }

        @Test
        @DisplayName("COMPLETED 상태에서 cancel() 호출 시 예외가 발생한다")
        void cancel_fromCompleted_shouldThrowException() {
            batch.release();
            batch.start();
            batch.complete();

            assertThatThrownBy(() -> batch.cancel())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("COMPLETED batches cannot be cancelled");
        }
    }

    @Nested
    @DisplayName("스텝 조회 및 진행률 테스트")
    class StepQueryTest {

        @Test
        @DisplayName("getStep()으로 특정 스텝을 조회할 수 있다")
        void getStep_shouldReturnCorrectStep() {
            BatchStep step1 = batch.getStep(1);
            BatchStep step2 = batch.getStep(2);

            assertThat(step1.getStepNumber()).isEqualTo(1);
            assertThat(step2.getStepNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("존재하지 않는 스텝 번호로 조회 시 예외가 발생한다")
        void getStep_withInvalidNumber_shouldThrowException() {
            assertThatThrownBy(() -> batch.getStep(999))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Step not found: 999");
        }

        @Test
        @DisplayName("getCurrentStep()은 IN_PROGRESS 상태인 스텝을 반환한다")
        void getCurrentStep_shouldReturnInProgressStep() {
            // 초기에는 진행 중인 스텝이 없음
            assertThat(batch.getCurrentStep()).isNull();
        }

        @Test
        @DisplayName("getNextPendingStep()은 첫 번째 PENDING 상태인 스텝을 반환한다")
        void getNextPendingStep_shouldReturnFirstPendingStep() {
            BatchStep nextStep = batch.getNextPendingStep();

            assertThat(nextStep).isNotNull();
            assertThat(nextStep.getStepNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("모든 스텝이 완료되지 않았을 때 allStepsCompleted()는 false를 반환한다")
        void allStepsCompleted_whenNotAllCompleted_shouldReturnFalse() {
            assertThat(batch.allStepsCompleted()).isFalse();
        }

        @Test
        @DisplayName("진행률은 완료된 스텝 비율로 계산된다")
        void getProgressPercentage_shouldCalculateCorrectly() {
            assertThat(batch.getProgressPercentage()).isEqualTo(0.0);
            assertThat(batch.getCompletedStepCount()).isEqualTo(0);
            assertThat(batch.getTotalStepCount()).isEqualTo(2);
        }
    }
}
