package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "molecule_template_table")
public class MoleculeTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "molecule_id")
    private Long moleculeId;

    @Column(name = "molecule_name", unique = true, nullable = false)
    private String moleculeName;

    @Column(name = "molecule_type")
    private String moleculeType;

    private String formula;

    private String smiles;

    @Column(name = "molecular_weight")
    private Double molecularWeight;

    @Column(name = "atom_count")
    private Integer atomCount;

    @Column(name = "net_charge")
    private Double netCharge;

    @Column(name = "force_field_type")
    private String forceFieldType;

    @Column(name = "lt_file_path", length = 500)
    private String ltFilePath;

    @Column(name = "single_pdb_path", length = 500)
    private String singlePdbPath;

    @Column(name = "force_field_params", columnDefinition = "JSON")
    private String forceFieldParams;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_system_template")
    private Boolean isSystemTemplate;

    @Column(name = "create_user_id")
    private Long createUserId;

    @Column(name = "create_time")
    private LocalDateTime createTime = LocalDateTime.now();

    @Column(name = "update_time")
    private LocalDateTime updateTime = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}