package com.testhub.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Un test case extrait d'un fichier .robot.
 * Cible d'exécution en mode SINGLE_TEST :
 *   robot --test "Nom Du Test" Tests/login/test_login.robot
 */
@Entity
@Table(name = "test_case", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"robot_file_id", "name"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "robot_file_id", nullable = false)
    private RobotFile robotFile;

    /** Nom exact tel qu'il apparaît dans le fichier .robot */
    @Column(nullable = false)
    private String name;

    /** Ordre d'apparition dans le fichier (1-based) */
    @Column(nullable = false)
    private int position;

    /** Tags [Tags] extraits (séparés par des virgules) */
    @Column(length = 500)
    private String tags;
}