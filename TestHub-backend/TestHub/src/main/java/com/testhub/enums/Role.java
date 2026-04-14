package com.testhub.enums;

/**
 * Rôles hiérarchiques — chaque rôle inclut les permissions des rôles inférieurs.
 *
 * VIEWER < QA_ENGINEER < QA_LEAD < ADMIN
 */
public enum Role {

    VIEWER,       // lecture seule
    QA_ENGINEER,  // lancer tests + voir rapports
    QA_LEAD,      // créer projets + gérer venv + lancer tests
    ADMIN;        // tout + gérer utilisateurs

    /**
     * Vérifie si ce rôle a au moins le niveau requis.
     * Ex: QA_LEAD.hasAtLeast(QA_ENGINEER) → true
     */
    public boolean hasAtLeast(Role required) {
        return this.ordinal() >= required.ordinal();
    }
}