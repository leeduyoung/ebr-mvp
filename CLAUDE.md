# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 저장소의 코드를 작업할 때 참고하는 가이드입니다.

## 프로젝트 개요

EBR (Electronic Batch Record) 시스템 - 제약/제조 배치 생산을 위한 규칙 기반 제조 실행 시스템. 레시피 정의부터 작업자 자격 검증 및 파라미터 검증을 포함한 단계별 실행까지 배치 생명주기를 관리합니다.

## 빌드 및 실행 명령어

```bash
# PostgreSQL 데이터베이스 시작 (앱 실행 전 필수)
docker-compose up -d

# 프로젝트 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun

# 테스트 실행
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "biz.page1.ebr.SomeTestClass"

# 단일 테스트 메서드 실행
./gradlew test --tests "biz.page1.ebr.SomeTestClass.testMethodName"
```

데이터베이스는 5433 포트에서 실행됩니다 (기본 5432가 아님).

## 아키텍처

### 도메인 모델 (Rich Domain)

시스템은 상태 머신 동작을 가진 DDD 스타일의 Rich Domain 엔티티를 사용합니다:

- **MasterRecipe** → **RecipeStep** → **StepParameter**: 템플릿 정의
- **Batch** → **BatchStep** → **StepParameterValue**: 레시피로부터 생성된 런타임 인스턴스
- **User** → **Qualification**: 작업자 권한

### 주요 도메인 규칙

**Batch 상태 머신**: `CREATED → RELEASED → IN_PROGRESS → COMPLETED` (`CANCELLED` 포함)
- 관리자만 배치를 release할 수 있음
- 첫 번째 스텝 시작 시 배치가 IN_PROGRESS로 전환
- 모든 스텝 완료 시 자동으로 COMPLETED로 전환

**Step 상태 머신**: `PENDING → IN_PROGRESS → COMPLETED`
- 스텝은 순서대로 실행되어야 함 (스텝 N은 스텝 N-1 완료 필요)
- 작업자는 스텝 시작을 위해 필요한 자격을 보유해야 함
- 스텝을 시작한 동일한 작업자가 완료해야 함
- 파라미터 값은 최소/최대 제약 조건에 대해 검증됨

### 레이어 구조

```
biz.page1.ebr/
├── domain/           # 비즈니스 로직을 가진 JPA 엔티티
│   ├── batch/        # Batch, BatchStep, StepParameterValue, enums
│   ├── recipe/       # MasterRecipe, RecipeStep, StepParameter, enums
│   └── user/         # User, Qualification, UserRole
├── repository/       # Spring Data JPA 리포지토리
├── service/          # 애플리케이션 서비스 (BatchService, ExecutionService, RecipeService)
├── controller/       # Spring MVC 컨트롤러 (Thymeleaf 뷰)
├── security/         # Spring Security UserDetails 구현
└── config/           # SecurityConfig, DataInitializer
```

### 검증 흐름 (ExecutionService)

`canStartStep()` 검증: 배치 상태 → 스텝 상태 → 이전 스텝 완료 여부 → 작업자 자격

`completeStep()` 검증: 스텝 진행 중 여부 → 작업자 일치 여부 → 파라미터 검증 → 모든 스텝 완료 시 배치 자동 완료

## 기술 스택

- Spring Boot 3.5.9 / Java 21
- PostgreSQL (docker-compose 사용)
- Spring Data JPA with Hibernate
- Spring Security (세션 기반)
- Thymeleaf 템플릿
- Lombok (보일러플레이트 코드 감소)
