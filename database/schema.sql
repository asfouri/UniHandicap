-- ============================================================
-- BASE DE DONNÉES : universite_accessibilite
-- SGBD            : MySQL
-- ============================================================

CREATE DATABASE IF NOT EXISTS universite_accessibilite
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE universite_accessibilite;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS Journal_Audit;
DROP TABLE IF EXISTS Notifications;
DROP TABLE IF EXISTS Reclamations;
DROP TABLE IF EXISTS Demandes_Amenagement;
DROP TABLE IF EXISTS Profils_Accessibilite;
DROP TABLE IF EXISTS Utilisateurs;
SET FOREIGN_KEY_CHECKS = 1;

-- ─────────────────────────────────────────
-- 1. TABLE : Utilisateurs
-- ─────────────────────────────────────────
CREATE TABLE Utilisateurs (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    nom           VARCHAR(100)  NOT NULL,
    prenom        VARCHAR(100)  NOT NULL,
    email         VARCHAR(200)  NOT NULL UNIQUE,
    mot_de_passe  VARCHAR(255)  NOT NULL,
    role          VARCHAR(20)   NOT NULL,
    date_creation DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actif         TINYINT(1)    NOT NULL DEFAULT 1,
    CONSTRAINT chk_role CHECK (role IN ('ETUDIANT', 'ADMIN'))
);

-- ─────────────────────────────────────────
-- 2. TABLE : Profils_Accessibilite
-- ─────────────────────────────────────────
CREATE TABLE Profils_Accessibilite (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id INT          NOT NULL,
    profil_couleur VARCHAR(50)  NOT NULL,
    theme_applique VARCHAR(50)  NOT NULL DEFAULT 'STANDARD',
    date_test      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_profils_utilisateur
        FOREIGN KEY (utilisateur_id) REFERENCES Utilisateurs(id)
        ON DELETE CASCADE
);

-- ─────────────────────────────────────────
-- 3. TABLE : Demandes_Amenagement
-- ─────────────────────────────────────────
CREATE TABLE Demandes_Amenagement (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    etudiant_id       INT          NOT NULL,
    type_amenagement  VARCHAR(100) NOT NULL,
    description       TEXT         NOT NULL,
    statut            VARCHAR(20)  NOT NULL DEFAULT 'EN_ATTENTE',
    document_joint    VARCHAR(500) NULL,
    date_soumission   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_traitement   DATETIME     NULL,
    admin_id          INT          NULL,
    commentaire_admin TEXT         NULL,
    archivee          TINYINT(1)   NOT NULL DEFAULT 0,
    date_archive      DATETIME     NULL,
    CONSTRAINT chk_statut_demande CHECK (statut IN ('EN_ATTENTE', 'ACCEPTEE', 'REFUSEE')),
    CONSTRAINT fk_demande_etudiant FOREIGN KEY (etudiant_id) REFERENCES Utilisateurs(id),
    CONSTRAINT fk_demande_admin FOREIGN KEY (admin_id) REFERENCES Utilisateurs(id)
);

-- ─────────────────────────────────────────
-- 4. TABLE : Reclamations
-- ─────────────────────────────────────────
CREATE TABLE Reclamations (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    etudiant_id     INT          NOT NULL,
    sujet           VARCHAR(200) NOT NULL,
    description     TEXT         NOT NULL,
    document_joint  VARCHAR(500) NULL,
    statut          VARCHAR(20)  NOT NULL DEFAULT 'EN_ATTENTE',
    date_soumission DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_resolution DATETIME     NULL,
    admin_id        INT          NULL,
    reponse_admin   TEXT         NULL,
    archivee        TINYINT(1)   NOT NULL DEFAULT 0,
    date_archive    DATETIME     NULL,
    CONSTRAINT chk_statut_reclamation CHECK (statut IN ('EN_ATTENTE', 'EN_COURS', 'RESOLUE')),
    CONSTRAINT fk_reclamation_etudiant FOREIGN KEY (etudiant_id) REFERENCES Utilisateurs(id),
    CONSTRAINT fk_reclamation_admin FOREIGN KEY (admin_id) REFERENCES Utilisateurs(id)
);

-- ─────────────────────────────────────────
-- 5. TABLE : Notifications
-- ─────────────────────────────────────────
CREATE TABLE Notifications (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id INT         NOT NULL,
    message       VARCHAR(500) NOT NULL,
    lue           TINYINT(1)   NOT NULL DEFAULT 0,
    date_creation DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_utilisateur
        FOREIGN KEY (utilisateur_id) REFERENCES Utilisateurs(id)
);

-- ─────────────────────────────────────────
-- 6. TABLE : Journal_Audit
-- ─────────────────────────────────────────
CREATE TABLE Journal_Audit (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    admin_id        INT          NOT NULL,
    action          VARCHAR(100) NOT NULL,
    entite          VARCHAR(50)  NOT NULL,
    entite_id       INT          NOT NULL,
    ancienne_valeur TEXT         NULL,
    nouvelle_valeur TEXT         NULL,
    date_action     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_admin
        FOREIGN KEY (admin_id) REFERENCES Utilisateurs(id)
);

-- ─────────────────────────────────────────
-- DONNÉES D'EXEMPLE
-- ─────────────────────────────────────────

-- Passwords: admin@universite.fr → 123456  |  students → 1234567
-- SHA-256('123456') = 8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92
-- SHA-256('1234567') = 8bb0cf6eb9b17d0f7d22b456f121257dc1254e1f01665370476383ea776df414
INSERT INTO Utilisateurs (nom, prenom, email, mot_de_passe, role)
VALUES ('Dupont', 'Marie', 'admin@universite.fr',
        '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'ADMIN');

INSERT INTO Utilisateurs (nom, prenom, email, mot_de_passe, role)
VALUES ('Martin', 'Lucas', 'lucas.martin@etudiant.fr',
        '8bb0cf6eb9b17d0f7d22b456f121257dc1254e1f01665370476383ea776df414', 'ETUDIANT');

INSERT INTO Utilisateurs (nom, prenom, email, mot_de_passe, role)
VALUES ('Bernard', 'Sophie', 'sophie.bernard@etudiant.fr',
        '8bb0cf6eb9b17d0f7d22b456f121257dc1254e1f01665370476383ea776df414', 'ETUDIANT');

INSERT INTO Demandes_Amenagement (etudiant_id, type_amenagement, description, statut)
VALUES (2, 'EXAMEN', 'Je sollicite un tiers-temps supplémentaire pour mes examens en raison de ma déficience visuelle.', 'EN_ATTENTE');

INSERT INTO Demandes_Amenagement (etudiant_id, type_amenagement, description, statut, admin_id, date_traitement, commentaire_admin)
VALUES (3, 'ACCESSIBILITE', 'Besoin d\'un accès par ascenseur au bâtiment B pour cause de mobilité réduite.', 'ACCEPTEE', 1, NOW(), 'Demande approuvée, accès accordé.');

INSERT INTO Reclamations (etudiant_id, sujet, description, statut)
VALUES (2, 'Salle non accessible', 'La salle 204 du bâtiment A ne dispose pas de rampe d\'accès pour fauteuil roulant.', 'EN_ATTENTE');

INSERT INTO Notifications (utilisateur_id, message)
VALUES (3, 'Votre demande d\'aménagement a été acceptée.');
