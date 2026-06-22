package com.staging.sg.common.repository;

import com.staging.sg.common.entity.IsoFieldCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IsoFieldCatalogRepository extends JpaRepository<IsoFieldCatalog, Long> {
    List<IsoFieldCatalog> findByEnabledTrueOrderByDisplayOrderAsc();
    Optional<IsoFieldCatalog> findByFieldCode(String fieldCode);
}
