package com.mdplatform.model;

import javax.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "electrolyte_systems")
public class ElectrolyteSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "solvent_type", nullable = false)
    private SolventType solventType;

    @Column(name = "salt_formula", length = 100)
    private String saltFormula;

    @Column(precision = 10, scale = 4)
    private BigDecimal concentration;  // mol/L

    @Column(precision = 10, scale = 2)
    private BigDecimal temperature;    // K

    @Column(precision = 10, scale = 2)
    private BigDecimal pressure;       // bar

    @Column(name = "ec_ratio", precision = 5, scale = 2)
    private BigDecimal ecRatio;        // EC percentage

    @Column(name = "dmc_ratio", precision = 5, scale = 2)
    private BigDecimal dmcRatio;       // DMC percentage

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum SolventType {
        WATER,
        ACETONITRILE,
        DMSO,
        ETHANOL,
        EC_DMC,
        EC,
        DMC,
        EC_EMC,
        OTHER
    }
}