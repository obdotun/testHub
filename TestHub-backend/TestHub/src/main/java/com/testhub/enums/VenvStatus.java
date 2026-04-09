package com.testhub.enums;

public enum VenvStatus {
    NONE,        // pas encore créé
    INSTALLING,  // pip install en cours
    READY,       // venv prêt à l'emploi
    ERROR        // installation échouée
}
