import { Component, signal } from '@angular/core';

interface HelpField { name: string; desc: string; }
interface HelpSection {
  id: string;
  icon: string;
  title: string;
  intro: string;
  fields?: HelpField[];
}

@Component({
  selector: 'app-help',
  standalone: true,
  templateUrl: './help.component.html',
  styleUrl: './help.component.scss',
})
export class HelpComponent {
  readonly openId = signal<string | null>('generation');

  toggle(id: string): void {
    this.openId.set(this.openId() === id ? null : id);
  }

  readonly sections: HelpSection[] = [
    {
      id: 'generation',
      icon: 'pi pi-plus-circle',
      title: 'Génération de campagne',
      intro: "Cet écran permet de créer, éditer et supprimer des campagnes. Une campagne décrit QUOI générer (configuration, catégorie), À QUEL RYTHME (paliers de charge / TPS) et QUELS CRITÈRES DE SUCCÈS (seuils SLA). Elle n'est pas exécutée ici : la génération se fait dans l'écran Orchestration.",
      fields: [
        { name: 'Nom', desc: "Nom de la campagne (ex. CAMP-DEMO). Sert à l'identifier dans les listes et les rapports. Obligatoire." },
        { name: 'Catégorie', desc: "Catégorie de la campagne (ex. DMAS). Détermine le type de transactions monétiques générées. Obligatoire." },
        { name: 'Description', desc: "Texte libre décrivant l'objectif de la campagne. Optionnel mais recommandé pour la traçabilité." },
        { name: 'Configuration (JSON)', desc: "Cœur de la campagne. JSON qui décrit la génération des transactions : mode de PAN (RANDOM = carte tirée au hasard parmi les cartes actives, FIXED = carte imposée), présence du PIN (WITH_PIN), et champs variables (VARIABLE_FIELDS) comme le montant (AMOUNT) avec un mode RANGE entre min et max (en centimes)." },
        { name: 'DE039 attendu', desc: "Code réponse ISO 8583 attendu (00 = transaction approuvée, autres codes = refus). Sert à vérifier que les transactions renvoient bien le résultat espéré. Une transaction dont le DE039 diffère est comptée comme erreur." },
        { name: 'SLA P95 max (ms)', desc: "Temps de réponse maximum toléré au 95e percentile, en millisecondes. Exemple : 500 signifie que 95% des transactions doivent répondre en moins de 500 ms. Au-delà, le SLA de performance est considéré comme non respecté." },
        { name: "SLA taux d'erreur max (%)", desc: "Taux d'erreur maximum toléré sur l'ensemble de la campagne, en pourcentage. Si dépassé en fin d'exécution, le verdict sera FAILED." },
        { name: 'SLA approbation min (%)', desc: "Taux d'approbation minimum attendu, en pourcentage. Exemple : 90 = au moins 90% des transactions doivent être approuvées pour que le SLA soit respecté." },
        { name: "Arrêt si taux d'erreur (%)", desc: "Seuil de protection. Si le taux d'erreur dépasse cette valeur PENDANT l'exécution, la campagne s'arrête automatiquement (statut STOPPED_ERROR_RATE) pour éviter de continuer une exécution défaillante." },
        { name: 'Active', desc: "Indique si la campagne est active et peut être lancée. Une campagne inactive reste enregistrée mais n'est pas exécutable." },
        { name: 'Paliers de charge (TPS)', desc: "Profil de montée en charge. Chaque palier définit un débit cible (TPS = transactions par seconde) sur une plage de temps (Début → Fin, en secondes depuis le lancement). Enchaîner plusieurs paliers permet de simuler une montée progressive : par exemple 5 TPS de 0 à 10s, puis 20 TPS de 10 à 30s. Chaque palier a un ordre (#), un instant de début, un instant de fin et une valeur de TPS." },
      ],
    },
    {
      id: 'orchestration',
      icon: 'pi pi-play',
      title: 'Orchestration de campagne',
      intro: "Cet écran permet de lancer l'exécution d'une campagne existante (génération effective des transactions selon les paliers TPS définis) et d'en suivre le déroulement en temps réel. Le lancement nécessite la permission CAMPAIGN_GENERATE.",
      fields: [
        { name: 'Lancer une campagne', desc: "Déclenche l'exécution asynchrone de la campagne. Le système génère les transactions selon le profil de charge (paliers TPS) et renvoie un identifiant d'exécution (campaignExecutionId) pour le suivi." },
        { name: "Statut d'exécution", desc: "État courant : RUNNING (en cours), COMPLETED (terminée normalement), ERROR (échec technique), STOPPED_ERROR_RATE (arrêt automatique car le seuil d'erreur a été dépassé)." },
      ],
    },
    {
      id: 'consultation',
      icon: 'pi pi-chart-line',
      title: 'Consultation des exécutions',
      intro: "Cet écran affiche l'historique et les résultats détaillés des exécutions de campagnes : nombre de transactions, taux d'approbation, temps de réponse, et le verdict final. Nécessite la permission EXECUTION_VIEW.",
      fields: [
        { name: 'Transactions (total / approuvées / refusées)', desc: "Nombre de transactions générées, combien ont été approuvées (DE039 = 00) et combien refusées. Le ratio détermine le taux d'approbation." },
        { name: 'Verdict', desc: "Résultat global de l'exécution au regard des SLA : PASSED (tous les critères respectés) ou FAILED (au moins un SLA non respecté). Le détail précise quel critère a échoué le cas échéant." },
        { name: 'TPS moyen', desc: "Débit réel moyen atteint pendant l'exécution (transactions par seconde), à comparer au TPS cible des paliers." },
        { name: 'Temps de réponse moyen', desc: "Latence moyenne des transactions, en millisecondes. À rapprocher du SLA P95 pour évaluer la performance." },
      ],
    },
    {
      id: 'dmas',
      icon: 'pi pi-credit-card',
      title: 'Monétique DMAS',
      intro: "Écran technique pour les opérations monétiques bas niveau : gestion des cartes de test, des clés cryptographiques (KEK, PEK) et des transactions unitaires. Réservé au profil EXPLOITATION (permission CARD_PROVISION).",
      fields: [
        { name: 'Cartes', desc: "Provisionnement et consultation des cartes de test (PAN, solde, statut). Les campagnes DMAS piochent parmi les cartes actives." },
        { name: 'Clés (KEK / PEK)', desc: "KEK (Key Encryption Key) : clé de transport initialisée par bootstrap. PEK (PIN Encryption Key) : clé de chiffrement du PIN, échangée après le sign-on. Ces clés sont nécessaires au traitement cryptographique des transactions." },
        { name: 'Sign-on', desc: "Ouverture de session réseau jPOS avec le simulateur. Prérequis à l'échange de clés PEK et aux transactions." },
      ],
    },
    {
      id: 'admin',
      icon: 'pi pi-cog',
      title: 'Administration',
      intro: "Gestion des utilisateurs, des rôles et des catalogues de référence. Réservé au profil ADMIN (permissions USER_MANAGE, ROLE_MANAGE, CATALOG_MANAGE).",
      fields: [
        { name: 'Utilisateurs', desc: "Création, modification, activation/désactivation des comptes. Chaque utilisateur a un rôle (ADMIN, EXPLOITATION, OBSERVATEUR) qui détermine ses permissions." },
        { name: 'Rôles & permissions', desc: "Les rôles regroupent des permissions. Les permissions contrôlent l'accès aux écrans et aux actions (ex. CAMPAIGN_CREATE, TPS_RUN, EXECUTION_VIEW)." },
        { name: 'Catalogues', desc: "Données de référence : types de messages ISO, catalogue des champs, plages de BIN. Utilisés lors de la génération des transactions." },
      ],
    },
  ];
}

