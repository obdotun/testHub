package com.testhub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Représente un fichier .robot indexé depuis le ZIP du projet.
 * Exemple : relativePath = "Tests/login/test_login.robot"
 */
@Entity
@Table(name = "robot_file", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"project_id", "relative_path"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RobotFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private TestProject project;

    /** Chemin relatif depuis la racine du projet : ex "Tests/login/test_login.robot" */
    @Column(name = "relative_path", nullable = false)
    private String relativePath;

    /** Nom de la suite (= nom du fichier sans extension) */
    @Column(nullable = false)
    private String suiteName;

    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime indexedAt;

    @OneToMany(mappedBy = "robotFile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TestCase> testCases = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        indexedAt = LocalDateTime.now();
    }
}