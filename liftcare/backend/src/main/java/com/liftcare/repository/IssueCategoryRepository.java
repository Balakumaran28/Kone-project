package com.liftcare.repository;

import com.liftcare.model.IssueCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IssueCategoryRepository extends JpaRepository<IssueCategory, Long> {

    Optional<IssueCategory> findByCode(String code);

    boolean existsByCode(String code);

    List<IssueCategory> findByActiveTrue();
}
