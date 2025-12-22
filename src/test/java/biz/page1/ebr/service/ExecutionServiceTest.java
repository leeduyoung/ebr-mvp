package biz.page1.ebr.service;

import biz.page1.ebr.domain.batch.*;
import biz.page1.ebr.domain.recipe.*;
import biz.page1.ebr.domain.user.Qualification;
import biz.page1.ebr.domain.user.User;
import biz.page1.ebr.domain.user.UserRole;
import biz.page1.ebr.repository.BatchRepository;
import biz.page1.ebr.repository.BatchStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ExecutionService 비즈니스 로직 단위 테스트
 *
 * 핵심 검증 로직:
 * - canStartStep(): 배치 상태 → 스텝 상태 → 이전 스텝 완료 → 작업자 자격
 * - completeStep(): 스텝 진행 중 → 작업자 일치 → 파라미터 검증 → 배치 자동 완료
 */
@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private BatchStepRepository batchStepRepository;

    @InjectMocks
    private ExecutionService executionService;

    private MasterRecipe recipe;
    private Batch batch;
    private User qualifiedOperator;
    private User unqualifiedOperator;
    private Qualification weighingQualification;
    private Qualification mixingQualification;

    @BeforeEach
    void setUp() throws Exception {
        // 자격 생성
        weighingQualification = new Qualification("WEIGHING", "계량 자격", "계량 작업 수행 자격");
        mixingQualification = new Qualification("MIXING", "혼합 자격", "혼합 작업 수행 자격");

        // ID 설정 (리플렉션 사용)
        setId(weighingQualification, 1L);
        setId(mixingQualification, 2L);

        // 레시피 생성
        recipe = new MasterRecipe("RCP-001", "Test Recipe", "Description");

        RecipeStep step1 = new RecipeStep(1, "Weighing", "계량 작업");
        step1.addRequiredQualification(weighingQualification);
        StepParameter param1 = new StepParameter("Weight", ParameterType.NUMBER, true, 90.0, 110.0, "kg");
        setId(param1, 1L);
        step1.addParameter(param1);

        RecipeStep step2 = new RecipeStep(2, "Mixing", "혼합 작업");
        step2.addRequiredQualification(mixingQualification);

        recipe.addStep(step1);
        recipe.addStep(step2);

        // 배치 생성
        batch = new Batch("BATCH-001", recipe, 100.0, "kg");
        setId(batch, 1L);

        // 사용자 생성
        qualifiedOperator = new User("operator1", "password", "Qualified Operator", UserRole.OPERATOR);
        qualifiedOperator.addQualification(weighingQualification);
        qualifiedOperator.addQualification(mixingQualification);
        setId(qualifiedOperator, 1L);

        unqualifiedOperator = new User("operator2", "password", "Unqualified Operator", UserRole.OPERATOR);
        setId(unqualifiedOperator, 2L);
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }

    @Nested
    @DisplayName("canStartStep() 테스트 - 스텝 시작 가능 여부 검증")
    class CanStartStepTest {

        @Test
        @DisplayName("CREATED 상태의 배치에서는 스텝을 시작할 수 없다")
        void canStartStep_whenBatchCreated_shouldReturnInvalid() {
            // batch는 기본적으로 CREATED 상태

            ExecutionService.StepValidationResult result =
                    executionService.canStartStep(batch, 1, qualifiedOperator);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("not in executable status"));
        }

        @Test
        @DisplayName("RELEASED 상태의 배치에서 첫 번째 스텝을 시작할 수 있다")
        void canStartStep_whenBatchReleased_firstStep_shouldReturnValid() {
            batch.release();

            ExecutionService.StepValidationResult result =
                    executionService.canStartStep(batch, 1, qualifiedOperator);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("IN_PROGRESS 상태의 배치에서도 스텝을 시작할 수 있다")
        void canStartStep_whenBatchInProgress_shouldReturnValid() {
            batch.release();
            batch.start();
            // 첫 번째 스텝 완료 상태로 설정
            BatchStep step1 = batch.getStep(1);
            step1.start(qualifiedOperator);
            step1.complete();

            ExecutionService.StepValidationResult result =
                    executionService.canStartStep(batch, 2, qualifiedOperator);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("이미 IN_PROGRESS인 스텝은 시작할 수 없다")
        void canStartStep_whenStepInProgress_shouldReturnInvalid() {
            batch.release();
            BatchStep step1 = batch.getStep(1);
            step1.start(qualifiedOperator);

            ExecutionService.StepValidationResult result =
                    executionService.canStartStep(batch, 1, qualifiedOperator);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("not in PENDING status"));
        }

        @Test
        @DisplayName("이미 COMPLETED인 스텝은 시작할 수 없다")
        void canStartStep_whenStepCompleted_shouldReturnInvalid() {
            batch.release();
            BatchStep step1 = batch.getStep(1);
            step1.start(qualifiedOperator);
            step1.complete();

            ExecutionService.StepValidationResult result =
                    executionService.canStartStep(batch, 1, qualifiedOperator);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("not in PENDING status"));
        }

        @Test
        @DisplayName("이전 스텝이 완료되지 않으면 다음 스텝을 시작할 수 없다")
        void canStartStep_whenPreviousStepNotCompleted_shouldReturnInvalid() {
            batch.release();

            ExecutionService.StepValidationResult result =
                    executionService.canStartStep(batch, 2, qualifiedOperator);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("Previous step"));
        }

        @Test
        @DisplayName("필요한 자격이 없으면 스텝을 시작할 수 없다")
        void canStartStep_whenMissingQualification_shouldReturnInvalid() {
            batch.release();

            ExecutionService.StepValidationResult result =
                    executionService.canStartStep(batch, 1, unqualifiedOperator);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("Missing required qualifications"));
        }

        @Test
        @DisplayName("자격 요구사항이 없는 스텝은 누구나 시작할 수 있다")
        void canStartStep_whenNoQualificationRequired_anyOperatorCanStart() {
            // 자격 요구사항 없는 레시피 생성
            MasterRecipe simpleRecipe = new MasterRecipe("RCP-002", "Simple", "No qualifications");
            RecipeStep simpleStep = new RecipeStep(1, "Simple Step", "No qualification needed");
            simpleRecipe.addStep(simpleStep);

            Batch simpleBatch = new Batch("BATCH-002", simpleRecipe, 50.0, "kg");
            simpleBatch.release();

            ExecutionService.StepValidationResult result =
                    executionService.canStartStep(simpleBatch, 1, unqualifiedOperator);

            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("startStep() 테스트 - 스텝 시작")
    class StartStepTest {

        @Test
        @DisplayName("첫 번째 스텝 시작 시 배치가 IN_PROGRESS로 전환된다")
        void startStep_firstStep_shouldStartBatch() {
            batch.release();
            when(batchRepository.findByIdWithSteps(1L)).thenReturn(Optional.of(batch));

            executionService.startStep(1L, 1, qualifiedOperator);

            assertThat(batch.getStatus()).isEqualTo(BatchStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("스텝 시작 시 스텝이 IN_PROGRESS로 전환된다")
        void startStep_shouldChangeStepToInProgress() {
            batch.release();
            when(batchRepository.findByIdWithSteps(1L)).thenReturn(Optional.of(batch));

            BatchStep startedStep = executionService.startStep(1L, 1, qualifiedOperator);

            assertThat(startedStep.getStatus()).isEqualTo(StepStatus.IN_PROGRESS);
            assertThat(startedStep.getOperator()).isEqualTo(qualifiedOperator);
        }

        @Test
        @DisplayName("검증에 실패하면 예외가 발생한다")
        void startStep_whenValidationFails_shouldThrowException() {
            batch.release();
            when(batchRepository.findByIdWithSteps(1L)).thenReturn(Optional.of(batch));

            assertThatThrownBy(() -> executionService.startStep(1L, 1, unqualifiedOperator))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot start step");
        }
    }

    @Nested
    @DisplayName("completeStep() 테스트 - 스텝 완료")
    class CompleteStepTest {

        @BeforeEach
        void setUpForComplete() {
            batch.release();
            batch.getStep(1).start(qualifiedOperator);
        }

        @Test
        @DisplayName("스텝을 시작한 작업자만 완료할 수 있다")
        void completeStep_byDifferentOperator_shouldThrowException() {
            when(batchRepository.findByIdWithSteps(1L)).thenReturn(Optional.of(batch));

            assertThatThrownBy(() ->
                    executionService.completeStep(1L, 1, Map.of(1L, "100.0"), unqualifiedOperator))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("same operator who started it");
        }

        @Test
        @DisplayName("IN_PROGRESS가 아닌 스텝은 완료할 수 없다")
        void completeStep_whenNotInProgress_shouldThrowException() {
            // 스텝을 다시 PENDING으로 (새 배치 생성)
            Batch newBatch = new Batch("BATCH-002", recipe, 100.0, "kg");
            newBatch.release();

            try {
                setId(newBatch, 2L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            when(batchRepository.findByIdWithSteps(2L)).thenReturn(Optional.of(newBatch));

            assertThatThrownBy(() ->
                    executionService.completeStep(2L, 1, Map.of(), qualifiedOperator))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not in progress");
        }

        @Test
        @DisplayName("모든 스텝 완료 시 배치가 자동으로 COMPLETED 상태가 된다")
        void completeStep_allStepsComplete_shouldCompleteBatch() {
            batch.start();
            when(batchRepository.findByIdWithSteps(1L)).thenReturn(Optional.of(batch));

            // Step 1 완료
            executionService.completeStep(1L, 1, Map.of(1L, "100.0"), qualifiedOperator);

            // Step 2 시작 및 완료
            batch.getStep(2).start(qualifiedOperator);
            when(batchRepository.findByIdWithSteps(1L)).thenReturn(Optional.of(batch));
            executionService.completeStep(1L, 2, Map.of(), qualifiedOperator);

            assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("validateParameters() 테스트 - 파라미터 검증")
    class ValidateParametersTest {

        private BatchStep batchStep;

        @BeforeEach
        void setUpStep() {
            batch.release();
            batchStep = batch.getStep(1);
            batchStep.start(qualifiedOperator);
        }

        @Test
        @DisplayName("필수 파라미터가 누락되면 오류를 반환한다")
        void validateParameters_whenRequiredMissing_shouldReturnError() {
            var errors = executionService.validateParameters(batchStep, Map.of());

            assertThat(errors).anyMatch(e -> e.contains("Required field"));
        }

        @Test
        @DisplayName("필수 파라미터가 빈 문자열이면 오류를 반환한다")
        void validateParameters_whenRequiredEmpty_shouldReturnError() {
            var errors = executionService.validateParameters(batchStep, Map.of(1L, ""));

            assertThat(errors).anyMatch(e -> e.contains("Required field"));
        }

        @Test
        @DisplayName("NUMBER 타입에 숫자가 아닌 값이 들어오면 오류를 반환한다")
        void validateParameters_whenNotNumber_shouldReturnError() {
            var errors = executionService.validateParameters(batchStep, Map.of(1L, "not a number"));

            assertThat(errors).anyMatch(e -> e.contains("must be a valid number"));
        }

        @Test
        @DisplayName("NUMBER 타입 값이 최소값보다 작으면 오류를 반환한다")
        void validateParameters_whenBelowMin_shouldReturnError() {
            var errors = executionService.validateParameters(batchStep, Map.of(1L, "80.0"));

            assertThat(errors).anyMatch(e -> e.contains("below minimum"));
        }

        @Test
        @DisplayName("NUMBER 타입 값이 최대값보다 크면 오류를 반환한다")
        void validateParameters_whenAboveMax_shouldReturnError() {
            var errors = executionService.validateParameters(batchStep, Map.of(1L, "120.0"));

            assertThat(errors).anyMatch(e -> e.contains("exceeds maximum"));
        }

        @Test
        @DisplayName("유효한 파라미터 값은 오류 없이 통과한다")
        void validateParameters_whenValid_shouldReturnEmpty() {
            var errors = executionService.validateParameters(batchStep, Map.of(1L, "100.0"));

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("경계값(최소값)은 유효하다")
        void validateParameters_atMinBoundary_shouldBeValid() {
            var errors = executionService.validateParameters(batchStep, Map.of(1L, "90.0"));

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("경계값(최대값)은 유효하다")
        void validateParameters_atMaxBoundary_shouldBeValid() {
            var errors = executionService.validateParameters(batchStep, Map.of(1L, "110.0"));

            assertThat(errors).isEmpty();
        }
    }
}
