package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingDocumentRepository extends JpaRepository<OnboardingDocument, UUID> {
    List<OnboardingDocument> findByCaseIdOrderByTypeAscDocumentVersionDesc(UUID caseId);
    Optional<OnboardingDocument> findFirstByCaseIdAndTypeOrderByDocumentVersionDesc(UUID caseId, DocumentType type);
}
