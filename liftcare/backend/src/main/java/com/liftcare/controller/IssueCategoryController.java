package com.liftcare.controller;

import com.liftcare.dto.IssueCategoryResponse;
import com.liftcare.repository.IssueCategoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/issue-categories")
public class IssueCategoryController {

    private final IssueCategoryRepository issueCategoryRepository;

    public IssueCategoryController(IssueCategoryRepository issueCategoryRepository) {
        this.issueCategoryRepository = issueCategoryRepository;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<IssueCategoryResponse>> getActiveCategories() {
        List<IssueCategoryResponse> list = issueCategoryRepository.findByActiveTrue().stream()
                .map(IssueCategoryResponse::from)
                .toList();
        return ResponseEntity.ok(list);
    }
}
