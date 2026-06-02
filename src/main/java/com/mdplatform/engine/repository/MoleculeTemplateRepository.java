package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.MoleculeTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MoleculeTemplateRepository extends JpaRepository<MoleculeTemplate, Long> {

    Optional<MoleculeTemplate> findByMoleculeName(String moleculeName);

    List<MoleculeTemplate> findByMoleculeType(String moleculeType);

    List<MoleculeTemplate> findByIsSystemTemplateTrue();

    List<MoleculeTemplate> findByCreateUserId(Long createUserId);
}