package biz.page1.ebr.domain.batch;

public enum BatchStatus {
    CREATED,      // 생성됨
    RELEASED,     // 출시됨 (작업 가능)
    IN_PROGRESS,  // 진행 중
    COMPLETED,    // 완료
    CANCELLED     // 취소됨
}
