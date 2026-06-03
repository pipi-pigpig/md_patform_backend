package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "electrolyte_systems_table")
public class ElectrolyteSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "system_id")
    private Long systemId;

    @Column(name = "system_name", nullable = false)
    private String systemName;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_description")
    private String taskDescription;

    @Column(name = "solvent_info", nullable = false, columnDefinition = "JSON")
    private String solventInfo;

    @Column(name = "salt_info", columnDefinition = "JSON")
    private String saltInfo;

    @Column(name = "additive_info", columnDefinition = "JSON")
    private String additiveInfo;

    @Column(name = "temperature", nullable = false)
    private Double temperature;

    @Column(name = "pressure", nullable = false)
    private Double pressure;

    @Column(name = "box_size", nullable = false, columnDefinition = "JSON")
    private String boxSize;

    @Column(name = "boundary_conditions", nullable = false)
    private String boundaryConditions = "p p p";

    @Column(name = "total_atom_count")
    private Integer totalAtomCount;

    @Column(name = "is_public_template", nullable = false)
    private Boolean isPublicTemplate = false;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}