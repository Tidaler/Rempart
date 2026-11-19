# Plan de la version iPhone

L'iPhone viendra **après** la version Android. Ce document dit ce qu'il faut, ce qui est réutilisable et ce qui est différent. Rien n'est écrit pour iOS pour l'instant.

## Prérequis (à la charge du développeur)

| Besoin | Détail |
|---|---|
| Un Mac | Xcode ne fonctionne que sur macOS (un Mac mini d'occasion suffit ; à défaut, un Mac loué dans le cloud). |
| Xcode | Gratuit sur le Mac App Store. |
| Compte Apple Developer | 99 $/an, obligatoire pour publier et pour les autorisations ci-dessous. |
| Autorisation **Family Controls** | `com.apple.developer.family-controls` : à demander à Apple via un formulaire, avec une justification (contrôle de contenu pour soi-même). Délai de quelques jours à quelques semaines. |
| Un iPhone de test | iOS 16 ou plus. |

## Ce qu'Apple permet (et ne permet pas)

iOS n'a ni service d'accessibilité lisant les autres applis, ni administrateur de l'appareil. L'équivalent passe par les frameworks **Screen Time** :

| Besoin Rempart | Solution iOS |
|---|---|
| Bloquer les sites pour adultes dans Safari et les WebView | `ManagedSettings` : `webContent.blockedByFilter = .auto(...)` (filtre adulte d'Apple) + liste de domaines bloqués |
| Bloquer ~150 000 domaines | `NEDNSProxyProvider` (proxy DNS, appareils supervisés) **ou** extension `NEFilterDataProvider` (filtre de contenu réseau) ; à défaut, résolveur DNS filtrant via un profil `NEDNSSettingsManager` (DoH vers Cloudflare for Families / AdGuard Family) |
| SafeSearch | Même réécriture DNS que sur Android, dans l'extension réseau |
| Empêcher la désinstallation | `ManagedSettings` : `application.denyAppRemoval = true` (avec l'autorisation Family Controls en mode « individuel ») |
| Empêcher de couper la protection | Autorisation Family Controls individuelle : la retirer demande le code Screen Time ou Face ID ; **le partenaire peut tenir le code Screen Time** |
| Partenaire + délai 72 h | Logique `:core` réutilisée ; l'horloge monotone iOS est `ProcessInfo.systemUptime` / `mach_continuous_time` |
| Écran d'encouragement | `ShieldConfigurationDataSource` (écran de blocage personnalisé) + l'app |

**Limite à accepter :** sur iOS, l'utilisateur garde la main sur l'autorisation Family Controls, sauf si le **code Screen Time** est tenu par le partenaire. C'est donc ce code qui jouera le rôle du « code partenaire » ; le délai de 72 h ne pourra s'appliquer qu'aux réglages internes de Rempart.

## Réutilisation du code Android

Le module `:core` est déjà en Kotlin pur, sans dépendance Android, précisément pour cela :

1. Convertir `:core` en module **Kotlin Multiplatform** (`commonMain` = code actuel ; remplacer `java.net.IDN`, `java.security`, `javax.crypto`, `java.time` par des `expect/actual` ou par les bibliothèques `kotlinx-datetime` et `cryptography-kotlin`).
2. Générer un framework iOS (`XCFramework`) consommé par l'app SwiftUI.
3. Réutilisé tel quel : listes et correspondance de domaines, mots-clés, SafeSearch, machine à états du verrou, délai monotone, code partenaire, compteur de jours, journal d'intégrité.
4. Réécrit en Swift : interface (SwiftUI), extensions réseau, intégration Screen Time.

Les tests de `:core` (57) passent alors aussi sur iOS via `iosSimulatorArm64Test`.

## Licence et App Store

Le code de Rempart est sous GPL-3.0. Les conditions de l'App Store ont historiquement été jugées incompatibles avec la GPL quand **un tiers** y publie du code GPL d'autrui. L'auteur détenant les droits sur son propre code peut publier ; il faut en revanche vérifier les **listes HaGeZi (GPL-3.0)** : les télécharger au lancement plutôt que les embarquer, ou utiliser uniquement des listes MIT dans le binaire iOS. À trancher avant la soumission.

## Étapes proposées

1. Acheter/louer un Mac, ouvrir le compte Apple Developer, demander l'autorisation Family Controls.
2. Convertir `:core` en KMP et faire passer ses tests sur iOS (peut se faire sur Windows pour la partie JVM, mais la compilation iOS exige le Mac).
3. Prototype SwiftUI : autorisation Family Controls individuelle, `denyAppRemoval`, filtre web adulte d'Apple.
4. Extension réseau (proxy DNS ou filtre de contenu) avec les listes et SafeSearch.
5. Écran de blocage personnalisé, compteur, partenaire.
6. TestFlight, puis soumission avec une note d'examen détaillée (usage personnel, aucune collecte).
