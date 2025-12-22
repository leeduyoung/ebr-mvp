package biz.page1.ebr.domain.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * User 자격 검증 단위 테스트
 *
 * 핵심 규칙:
 * - 작업자는 스텝 시작을 위해 필요한 자격을 보유해야 함
 * - 여러 자격이 필요한 경우 모든 자격을 보유해야 함
 */
class UserQualificationTest {

    private User user;
    private Qualification weighingQualification;
    private Qualification mixingQualification;
    private Qualification qaQualification;

    @BeforeEach
    void setUp() throws Exception {
        user = new User("operator1", "password", "Operator 1", UserRole.OPERATOR);

        weighingQualification = new Qualification("WEIGHING", "계량 자격", "계량 작업 수행 자격");
        setId(weighingQualification, 1L);

        mixingQualification = new Qualification("MIXING", "혼합 자격", "혼합 작업 수행 자격");
        setId(mixingQualification, 2L);

        qaQualification = new Qualification("QA_REVIEW", "품질 검사 자격", "품질 검사 수행 자격");
        setId(qaQualification, 3L);
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }

    @Nested
    @DisplayName("자격 추가 테스트")
    class AddQualificationTest {

        @Test
        @DisplayName("사용자에게 자격을 추가할 수 있다")
        void addQualification_shouldAddToUserQualifications() {
            user.addQualification(weighingQualification);

            assertThat(user.getQualifications()).contains(weighingQualification);
        }

        @Test
        @DisplayName("여러 자격을 추가할 수 있다")
        void addQualification_canAddMultiple() {
            user.addQualification(weighingQualification);
            user.addQualification(mixingQualification);

            assertThat(user.getQualifications())
                    .containsExactlyInAnyOrder(weighingQualification, mixingQualification);
        }

        @Test
        @DisplayName("같은 자격을 중복 추가해도 하나만 존재한다")
        void addQualification_duplicatesShouldBeIgnored() {
            user.addQualification(weighingQualification);
            user.addQualification(weighingQualification);

            assertThat(user.getQualifications()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("hasQualification() 테스트 - 단일 자격 확인")
    class HasQualificationTest {

        @Test
        @DisplayName("보유한 자격에 대해 true를 반환한다")
        void hasQualification_whenHas_shouldReturnTrue() {
            user.addQualification(weighingQualification);

            assertThat(user.hasQualification(weighingQualification)).isTrue();
        }

        @Test
        @DisplayName("보유하지 않은 자격에 대해 false를 반환한다")
        void hasQualification_whenNotHas_shouldReturnFalse() {
            assertThat(user.hasQualification(weighingQualification)).isFalse();
        }

        @Test
        @DisplayName("다른 자격을 보유해도 확인하는 자격이 없으면 false를 반환한다")
        void hasQualification_withDifferentQualification_shouldReturnFalse() {
            user.addQualification(mixingQualification);

            assertThat(user.hasQualification(weighingQualification)).isFalse();
        }
    }

    @Nested
    @DisplayName("hasAllQualifications() 테스트 - 복수 자격 확인")
    class HasAllQualificationsTest {

        @Test
        @DisplayName("빈 자격 세트에 대해 true를 반환한다")
        void hasAllQualifications_emptySet_shouldReturnTrue() {
            assertThat(user.hasAllQualifications(Set.of())).isTrue();
        }

        @Test
        @DisplayName("필요한 모든 자격을 보유하면 true를 반환한다")
        void hasAllQualifications_whenHasAll_shouldReturnTrue() {
            user.addQualification(weighingQualification);
            user.addQualification(mixingQualification);

            Set<Qualification> required = Set.of(weighingQualification, mixingQualification);

            assertThat(user.hasAllQualifications(required)).isTrue();
        }

        @Test
        @DisplayName("필요한 자격 중 일부만 보유하면 false를 반환한다")
        void hasAllQualifications_whenHasPartial_shouldReturnFalse() {
            user.addQualification(weighingQualification);

            Set<Qualification> required = Set.of(weighingQualification, mixingQualification);

            assertThat(user.hasAllQualifications(required)).isFalse();
        }

        @Test
        @DisplayName("필요한 자격이 하나도 없으면 false를 반환한다")
        void hasAllQualifications_whenHasNone_shouldReturnFalse() {
            Set<Qualification> required = Set.of(weighingQualification, mixingQualification);

            assertThat(user.hasAllQualifications(required)).isFalse();
        }

        @Test
        @DisplayName("필요한 자격보다 더 많이 보유해도 true를 반환한다")
        void hasAllQualifications_whenHasMore_shouldReturnTrue() {
            user.addQualification(weighingQualification);
            user.addQualification(mixingQualification);
            user.addQualification(qaQualification);

            Set<Qualification> required = Set.of(weighingQualification);

            assertThat(user.hasAllQualifications(required)).isTrue();
        }
    }

    @Nested
    @DisplayName("사용자 역할 테스트")
    class UserRoleTest {

        @Test
        @DisplayName("ADMIN 역할의 사용자를 생성할 수 있다")
        void canCreateAdminUser() {
            User admin = new User("admin", "password", "Admin User", UserRole.ADMIN);

            assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        }

        @Test
        @DisplayName("SUPERVISOR 역할의 사용자를 생성할 수 있다")
        void canCreateSupervisorUser() {
            User supervisor = new User("supervisor", "password", "Supervisor", UserRole.SUPERVISOR);

            assertThat(supervisor.getRole()).isEqualTo(UserRole.SUPERVISOR);
        }

        @Test
        @DisplayName("OPERATOR 역할의 사용자를 생성할 수 있다")
        void canCreateOperatorUser() {
            assertThat(user.getRole()).isEqualTo(UserRole.OPERATOR);
        }
    }

    @Nested
    @DisplayName("사용자 활성화 상태 테스트")
    class UserEnabledTest {

        @Test
        @DisplayName("새로 생성된 사용자는 기본적으로 활성화 상태이다")
        void newUser_shouldBeEnabled() {
            assertThat(user.isEnabled()).isTrue();
        }
    }
}
