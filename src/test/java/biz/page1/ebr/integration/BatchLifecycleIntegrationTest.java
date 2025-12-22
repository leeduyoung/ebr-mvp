package biz.page1.ebr.integration;

import biz.page1.ebr.domain.batch.Batch;
import biz.page1.ebr.domain.batch.BatchStatus;
import biz.page1.ebr.domain.batch.BatchStep;
import biz.page1.ebr.domain.batch.StepStatus;
import biz.page1.ebr.domain.recipe.*;
import biz.page1.ebr.domain.user.Qualification;
import biz.page1.ebr.domain.user.User;
import biz.page1.ebr.domain.user.UserRole;
import biz.page1.ebr.repository.*;
import biz.page1.ebr.service.BatchService;
import biz.page1.ebr.service.ExecutionService;
import biz.page1.ebr.service.RecipeService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * E2E 통합 테스트 - 배치 전체 생명주기
 *
 * 시나리오:
 * 1. 레시피 생성 및 승인
 * 2. 배치 생성 및 출시
 * 3. 작업자 자격 검증
 * 4. 스텝별 순차 실행
 * 5. 파라미터 검증
 * 6. 배치 자동 완료
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BatchLifecycleIntegrationTest {

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private BatchService batchService;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private MasterRecipeRepository recipeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QualificationRepository qualificationRepository;

    @Autowired
    private BatchRepository batchRepository;

    private User admin;
    private User qualifiedOperator;
    private User partiallyQualifiedOperator;
    private Qualification weighingQualification;
    private Qualification mixingQualification;
    private MasterRecipe recipe;

    @BeforeEach
    void setUp() {
        // 자격 생성
        weighingQualification = qualificationRepository.save(
                new Qualification("WEIGHING", "계량 자격", "계량 작업 수행 자격"));
        mixingQualification = qualificationRepository.save(
                new Qualification("MIXING", "혼합 자격", "혼합 작업 수행 자격"));

        // 사용자 생성
        admin = new User("admin", "password", "Admin User", UserRole.ADMIN);
        admin = userRepository.save(admin);

        qualifiedOperator = new User("operator1", "password", "Qualified Operator", UserRole.OPERATOR);
        qualifiedOperator.addQualification(weighingQualification);
        qualifiedOperator.addQualification(mixingQualification);
        qualifiedOperator = userRepository.save(qualifiedOperator);

        partiallyQualifiedOperator = new User("operator2", "password", "Partial Operator", UserRole.OPERATOR);
        partiallyQualifiedOperator.addQualification(weighingQualification);
        partiallyQualifiedOperator = userRepository.save(partiallyQualifiedOperator);

        // 레시피 생성
        recipe = createTestRecipe();
    }

    private MasterRecipe createTestRecipe() {
        MasterRecipe newRecipe = new MasterRecipe("RCP-TEST-001", "테스트 레시피", "통합 테스트용 레시피");

        // Step 1: 계량 (WEIGHING 자격 필요)
        RecipeStep step1 = new RecipeStep(1, "원료 계량", "원료를 계량합니다");
        step1.addRequiredQualification(weighingQualification);
        StepParameter weight = new StepParameter("Weight", ParameterType.NUMBER, true, 90.0, 110.0, "kg");
        StepParameter equipmentId = new StepParameter("Equipment ID", ParameterType.TEXT, true);
        step1.addParameter(weight);
        step1.addParameter(equipmentId);
        newRecipe.addStep(step1);

        // Step 2: 혼합 (MIXING 자격 필요)
        RecipeStep step2 = new RecipeStep(2, "혼합", "원료를 혼합합니다");
        step2.addRequiredQualification(mixingQualification);
        StepParameter mixingTime = new StepParameter("Mixing Time", ParameterType.NUMBER, true, 10.0, 30.0, "min");
        step2.addParameter(mixingTime);
        newRecipe.addStep(step2);

        // Step 3: 검사 (자격 불필요)
        RecipeStep step3 = new RecipeStep(3, "검사", "최종 검사를 수행합니다");
        StepParameter passed = new StepParameter("Inspection Passed", ParameterType.BOOLEAN, true);
        step3.addParameter(passed);
        newRecipe.addStep(step3);

        newRecipe = recipeRepository.save(newRecipe);
        newRecipe.approve();

        return recipeRepository.save(newRecipe);
    }

    @Nested
    @DisplayName("전체 배치 생명주기 테스트")
    class FullLifecycleTest {

        @Test
        @DisplayName("배치 생성 → 출시 → 모든 스텝 완료 → 배치 완료")
        void completeBatchLifecycle() {
            // 1. 배치 생성
            Batch batch = batchService.createBatch(recipe.getId(), 100.0, "kg", admin);
            assertThat(batch.getStatus()).isEqualTo(BatchStatus.CREATED);
            assertThat(batch.getSteps()).hasSize(3);

            // 2. 배치 출시
            batchService.releaseBatch(batch.getId());
            batch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();
            assertThat(batch.getStatus()).isEqualTo(BatchStatus.RELEASED);

            // 3. Step 1 실행 (계량)
            Map<Long, String> step1Params = getParameterMap(batch.getStep(1), "100.0", "BAL-001");
            executionService.startStep(batch.getId(), 1, qualifiedOperator);

            batch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();
            assertThat(batch.getStatus()).isEqualTo(BatchStatus.IN_PROGRESS);
            assertThat(batch.getStep(1).getStatus()).isEqualTo(StepStatus.IN_PROGRESS);

            executionService.completeStep(batch.getId(), 1, step1Params, qualifiedOperator);
            batch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();
            assertThat(batch.getStep(1).getStatus()).isEqualTo(StepStatus.COMPLETED);

            // 4. Step 2 실행 (혼합)
            Map<Long, String> step2Params = getParameterMap(batch.getStep(2), "20.0");
            executionService.startStep(batch.getId(), 2, qualifiedOperator);
            executionService.completeStep(batch.getId(), 2, step2Params, qualifiedOperator);

            batch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();
            assertThat(batch.getStep(2).getStatus()).isEqualTo(StepStatus.COMPLETED);

            // 5. Step 3 실행 (검사 - 자격 불필요)
            Map<Long, String> step3Params = getParameterMap(batch.getStep(3), "true");
            executionService.startStep(batch.getId(), 3, qualifiedOperator);
            executionService.completeStep(batch.getId(), 3, step3Params, qualifiedOperator);

            // 6. 배치 자동 완료 확인
            batch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();
            assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(batch.allStepsCompleted()).isTrue();
            assertThat(batch.getProgressPercentage()).isEqualTo(100.0);
        }

        private Map<Long, String> getParameterMap(BatchStep step, String... values) {
            Map<Long, String> params = new HashMap<>();
            var paramValues = step.getParameterValues();
            for (int i = 0; i < Math.min(values.length, paramValues.size()); i++) {
                params.put(paramValues.get(i).getStepParameter().getId(), values[i]);
            }
            return params;
        }
    }

    @Nested
    @DisplayName("자격 검증 통합 테스트")
    class QualificationValidationTest {

        private Batch releasedBatch;

        @BeforeEach
        void setUpBatch() {
            Batch batch = batchService.createBatch(recipe.getId(), 100.0, "kg", admin);
            batchService.releaseBatch(batch.getId());
            releasedBatch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();
        }

        @Test
        @DisplayName("필요한 자격이 없는 작업자는 스텝을 시작할 수 없다")
        void unqualifiedOperator_cannotStartStep() {
            // partiallyQualifiedOperator는 WEIGHING만 있음

            // Step 1 (WEIGHING 필요) - 시작 가능
            var result1 = executionService.canStartStep(releasedBatch, 1, partiallyQualifiedOperator);
            assertThat(result1.isValid()).isTrue();

            // Step 1 완료
            executionService.startStep(releasedBatch.getId(), 1, partiallyQualifiedOperator);
            Map<Long, String> step1Params = new HashMap<>();
            for (var pv : releasedBatch.getStep(1).getParameterValues()) {
                if (pv.getStepParameter().getParameterType() == ParameterType.NUMBER) {
                    step1Params.put(pv.getStepParameter().getId(), "100.0");
                } else {
                    step1Params.put(pv.getStepParameter().getId(), "BAL-001");
                }
            }
            executionService.completeStep(releasedBatch.getId(), 1, step1Params, partiallyQualifiedOperator);

            // Step 2 (MIXING 필요) - 시작 불가
            releasedBatch = batchRepository.findByIdWithSteps(releasedBatch.getId()).orElseThrow();
            var result2 = executionService.canStartStep(releasedBatch, 2, partiallyQualifiedOperator);
            assertThat(result2.isValid()).isFalse();
            assertThat(result2.getErrors()).anyMatch(e -> e.contains("Missing required qualifications"));
        }

        @Test
        @DisplayName("자격이 필요 없는 스텝은 누구나 시작할 수 있다")
        void noQualificationRequired_anyOperatorCanStart() {
            // 먼저 Step 1, 2를 qualifiedOperator가 완료
            executionService.startStep(releasedBatch.getId(), 1, qualifiedOperator);
            Map<Long, String> step1Params = new HashMap<>();
            for (var pv : releasedBatch.getStep(1).getParameterValues()) {
                if (pv.getStepParameter().getParameterType() == ParameterType.NUMBER) {
                    step1Params.put(pv.getStepParameter().getId(), "100.0");
                } else {
                    step1Params.put(pv.getStepParameter().getId(), "BAL-001");
                }
            }
            executionService.completeStep(releasedBatch.getId(), 1, step1Params, qualifiedOperator);

            releasedBatch = batchRepository.findByIdWithSteps(releasedBatch.getId()).orElseThrow();
            executionService.startStep(releasedBatch.getId(), 2, qualifiedOperator);
            Map<Long, String> step2Params = new HashMap<>();
            for (var pv : releasedBatch.getStep(2).getParameterValues()) {
                step2Params.put(pv.getStepParameter().getId(), "20.0");
            }
            executionService.completeStep(releasedBatch.getId(), 2, step2Params, qualifiedOperator);

            // Step 3 (자격 불필요) - partiallyQualifiedOperator도 시작 가능
            releasedBatch = batchRepository.findByIdWithSteps(releasedBatch.getId()).orElseThrow();
            var result = executionService.canStartStep(releasedBatch, 3, partiallyQualifiedOperator);
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("순차 실행 검증 통합 테스트")
    class SequentialExecutionTest {

        private Batch releasedBatch;

        @BeforeEach
        void setUpBatch() {
            Batch batch = batchService.createBatch(recipe.getId(), 100.0, "kg", admin);
            batchService.releaseBatch(batch.getId());
            releasedBatch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();
        }

        @Test
        @DisplayName("이전 스텝이 완료되지 않으면 다음 스텝을 시작할 수 없다")
        void cannotSkipSteps() {
            // Step 2를 바로 시작 시도
            var result = executionService.canStartStep(releasedBatch, 2, qualifiedOperator);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("Previous step"));
        }

        @Test
        @DisplayName("스텝을 시작한 작업자만 완료할 수 있다")
        void onlyStarterCanComplete() {
            executionService.startStep(releasedBatch.getId(), 1, qualifiedOperator);

            Map<Long, String> params = new HashMap<>();
            for (var pv : releasedBatch.getStep(1).getParameterValues()) {
                if (pv.getStepParameter().getParameterType() == ParameterType.NUMBER) {
                    params.put(pv.getStepParameter().getId(), "100.0");
                } else {
                    params.put(pv.getStepParameter().getId(), "BAL-001");
                }
            }

            // 다른 작업자가 완료 시도
            assertThatThrownBy(() ->
                    executionService.completeStep(releasedBatch.getId(), 1, params, partiallyQualifiedOperator))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("same operator");
        }
    }

    @Nested
    @DisplayName("파라미터 검증 통합 테스트")
    class ParameterValidationIntegrationTest {

        private Batch releasedBatch;

        @BeforeEach
        void setUpBatch() {
            Batch batch = batchService.createBatch(recipe.getId(), 100.0, "kg", admin);
            batchService.releaseBatch(batch.getId());
            releasedBatch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();
            executionService.startStep(releasedBatch.getId(), 1, qualifiedOperator);
            releasedBatch = batchRepository.findByIdWithSteps(releasedBatch.getId()).orElseThrow();
        }

        @Test
        @DisplayName("범위를 벗어난 값은 거부된다")
        void outOfRangeValue_shouldBeRejected() {
            Map<Long, String> params = new HashMap<>();
            for (var pv : releasedBatch.getStep(1).getParameterValues()) {
                if (pv.getStepParameter().getParameterType() == ParameterType.NUMBER) {
                    params.put(pv.getStepParameter().getId(), "150.0"); // 범위 초과
                } else {
                    params.put(pv.getStepParameter().getId(), "BAL-001");
                }
            }

            assertThatThrownBy(() ->
                    executionService.completeStep(releasedBatch.getId(), 1, params, qualifiedOperator))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds maximum");
        }

        @Test
        @DisplayName("필수 파라미터 누락 시 거부된다")
        void missingRequiredParameter_shouldBeRejected() {
            Map<Long, String> params = new HashMap<>();
            // 모든 파라미터 누락

            assertThatThrownBy(() ->
                    executionService.completeStep(releasedBatch.getId(), 1, params, qualifiedOperator))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Required field");
        }

        @Test
        @DisplayName("유효한 파라미터는 저장된다")
        void validParameters_shouldBeSaved() {
            Map<Long, String> params = new HashMap<>();
            for (var pv : releasedBatch.getStep(1).getParameterValues()) {
                if (pv.getStepParameter().getParameterType() == ParameterType.NUMBER) {
                    params.put(pv.getStepParameter().getId(), "100.0");
                } else {
                    params.put(pv.getStepParameter().getId(), "BAL-001");
                }
            }

            executionService.completeStep(releasedBatch.getId(), 1, params, qualifiedOperator);

            releasedBatch = batchRepository.findByIdWithSteps(releasedBatch.getId()).orElseThrow();
            BatchStep completedStep = releasedBatch.getStep(1);

            assertThat(completedStep.getParameterValues())
                    .allMatch(pv -> pv.getValue() != null);
        }
    }

    @Nested
    @DisplayName("배치 취소 통합 테스트")
    class BatchCancellationTest {

        @Test
        @DisplayName("진행 중인 배치를 취소할 수 있다")
        void canCancelInProgressBatch() {
            Batch batch = batchService.createBatch(recipe.getId(), 100.0, "kg", admin);
            batchService.releaseBatch(batch.getId());
            executionService.startStep(batch.getId(), 1, qualifiedOperator);

            batchService.cancelBatch(batch.getId());

            batch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();
            assertThat(batch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
        }

        @Test
        @DisplayName("완료된 배치는 취소할 수 없다")
        void cannotCancelCompletedBatch() {
            // 배치를 완료 상태로 만들기
            Batch batch = batchService.createBatch(recipe.getId(), 100.0, "kg", admin);
            batchService.releaseBatch(batch.getId());

            // 모든 스텝 완료
            for (int stepNum = 1; stepNum <= 3; stepNum++) {
                batch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();
                executionService.startStep(batch.getId(), stepNum, qualifiedOperator);

                Map<Long, String> params = new HashMap<>();
                for (var pv : batch.getStep(stepNum).getParameterValues()) {
                    var param = pv.getStepParameter();
                    if (param.getParameterType() == ParameterType.NUMBER) {
                        // Use the middle of the valid range
                        double midValue = (param.getMinValue() + param.getMaxValue()) / 2;
                        params.put(param.getId(), String.valueOf(midValue));
                    } else if (param.getParameterType() == ParameterType.BOOLEAN) {
                        params.put(param.getId(), "true");
                    } else {
                        params.put(param.getId(), "TEST-001");
                    }
                }
                executionService.completeStep(batch.getId(), stepNum, params, qualifiedOperator);
            }

            // 완료된 배치 취소 시도
            Long batchId = batch.getId();
            assertThatThrownBy(() -> batchService.cancelBatch(batchId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("COMPLETED");
        }
    }

    @Nested
    @DisplayName("진행률 추적 통합 테스트")
    class ProgressTrackingTest {

        @Test
        @DisplayName("스텝 완료에 따라 진행률이 올바르게 계산된다")
        void progressIsCalculatedCorrectly() {
            Batch batch = batchService.createBatch(recipe.getId(), 100.0, "kg", admin);
            batchService.releaseBatch(batch.getId());
            batch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();

            // 초기 진행률: 0%
            assertThat(batch.getProgressPercentage()).isEqualTo(0.0);
            assertThat(batch.getCompletedStepCount()).isEqualTo(0);
            assertThat(batch.getTotalStepCount()).isEqualTo(3);

            // Step 1 완료 후: 33.33%
            executionService.startStep(batch.getId(), 1, qualifiedOperator);
            Map<Long, String> params = new HashMap<>();
            batch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();
            for (var pv : batch.getStep(1).getParameterValues()) {
                if (pv.getStepParameter().getParameterType() == ParameterType.NUMBER) {
                    params.put(pv.getStepParameter().getId(), "100.0");
                } else {
                    params.put(pv.getStepParameter().getId(), "BAL-001");
                }
            }
            executionService.completeStep(batch.getId(), 1, params, qualifiedOperator);

            batch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();
            assertThat(batch.getCompletedStepCount()).isEqualTo(1);
            assertThat(batch.getProgressPercentage()).isCloseTo(33.33, within(0.01));

            // Step 2 완료 후: 66.67%
            executionService.startStep(batch.getId(), 2, qualifiedOperator);
            params.clear();
            batch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();
            for (var pv : batch.getStep(2).getParameterValues()) {
                params.put(pv.getStepParameter().getId(), "20.0");
            }
            executionService.completeStep(batch.getId(), 2, params, qualifiedOperator);

            batch = batchRepository.findByIdWithSteps(batch.getId()).orElseThrow();
            assertThat(batch.getCompletedStepCount()).isEqualTo(2);
            assertThat(batch.getProgressPercentage()).isCloseTo(66.67, within(0.01));
        }
    }
}
