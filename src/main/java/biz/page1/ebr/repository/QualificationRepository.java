package biz.page1.ebr.repository;

import biz.page1.ebr.domain.user.Qualification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QualificationRepository extends JpaRepository<Qualification, Long> {
    Optional<Qualification> findByCode(String code);
    boolean existsByCode(String code);
}
