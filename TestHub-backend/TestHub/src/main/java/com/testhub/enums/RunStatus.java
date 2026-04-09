package com.testhub.enums;

public enum RunStatus {
    PENDING,    // en attente de démarrage
    RUNNING,    // en cours d'exécution
    PASSED,     // tous les tests passés
    FAILED,     // au moins un test échoué
    ERROR,      // erreur système (RF non trouvé, timeout, etc.)
    CANCELLED   // annulé par l'utilisateur
}
