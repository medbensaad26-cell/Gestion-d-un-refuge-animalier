# Checklist de remise actualisée

État : **14 novembre 2026**. Ce document remplace l'ancienne checklist Phase 3A, qui décrivait notamment un admin nullable et un frontend à développer. Références actuelles : [guide retrouvé](university-requirements.md), spécification refuge et code livré. Aucune base personnelle n'est consultée en Phase 5.

| Exigence | État actuel / preuve | Avant remise |
|---|---|---|
| Architecture imposée | React, Java 21/Maven sans Spring Boot, HTTP/JSON, JDBC, MySQL | Présenter le parcours entre couches |
| Packages et modèles | Cinq entités, enum, constructeurs/accesseurs, DAO/services/contrôleurs et sécurité | Utiliser la [vue du projet](project-overview.md) |
| PK/FK et transactions | 5 tables InnoDB, 5 PK, 6 FK, 2 UNIQUE hors PK ; toutes les FK NOT NULL | Expliquer SQL 0..1 / cycle Java 1:1 du médical |
| UML et relationnel | [Classes, cas d'utilisation et ER](diagrams/README.md), sources et exports livrés | Relecture de l'étudiant et format final à confirmer |
| Animaux et médical | Gestion complète des animaux avec gardes d'historique ; consultation/édition du médical ; pas de suppression indépendante du dossier | Préparer des données fictives sur l'installation de démonstration |
| Annonces | Recherche/filtres publics, visibilité PUBLIEE ; création/édition/publication/retrait administrateur | Pas d'écran de suppression d'annonce : ne pas l'annoncer |
| Demandes | Soumission publique EN_ATTENTE affectée au seul admin ; liste/détails privés ; acceptation/refus ; adoption atomique et historique protégé | Ne pas présenter le statut comme librement rééditable |
| Authentification | BCrypt et sessions révocables ; connexion/déconnexion/protection confirmées manuellement par l'utilisateur | Utiliser le compte de démonstration créé localement ; ne publier aucun identifiant |
| Installation neuve | [Procédure sûre](fresh-installation.md), compte sans seed incompatible et saisie privée ; tests sur MySQL jetable | Installation sur le poste du professeur à effectuer avec sa configuration privée |
| SQL pédagogique | Schéma seul, lectures et exemples CRUD séparés, fixtures automatisées | **Faire accepter le découpage** : le guide demande le tout dans database.sql |
| Tests/build | Dernier bilan du 13 novembre : 244 backend, 213 frontend, build/lint réussis | Ne pas confondre ces preuves avec de nouveaux essais Phase 5 |
| Postman | Collection et bilans Newman disponibles ; tests HTTP Java réels isolés | Captures du Runner graphique seulement si exigées, sur données fictives |
| Dossier final et confidentialité | [Index](README.md), documents actuels distingués de l'historique | Exclure configuration privée, reset non approuvé, tokens, sauvegardes et données personnelles |

## À faire confirmer

Le guide retrouvé impose un diagramme de classes et un modèle relationnel. La spécification refuge fournit aussi les cas d'utilisation. Aucun autre diagramme n'est explicitement demandé dans les documents disponibles.

Restent à confirmer : conditionnement des fichiers SQL, éventuelle arborescence physique stricte des ressources Maven, format de remise/rapport/diagrammes, barème final et captures imposées. Voir [les références et leurs limites](university-requirements.md). Ces questions ne sont pas des fonctionnalités manquantes déduites arbitrairement.

Cette phase est documentaire : aucune migration, création de compte, réinitialisation, modification de données ou nouvelle décision d'adoption n'est effectuée.
