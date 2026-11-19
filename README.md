# Rempart

**Rempart** est une application Android gratuite, open source, sans publicité et sans achat intégré, qui aide les personnes dépendantes à la pornographie à s'en libérer. Elle bloque les sites pour adultes et les contenus trop dénudés sur tout le téléphone, et elle s'appuie sur un **partenaire de confiance** et un **délai d'attente** pour qu'on ne puisse pas la couper dans un moment de faiblesse.

> Rempart sert uniquement à **se protéger soi-même**, sur son propre téléphone, de son plein gré. Il n'a ni mode caché ni surveillance à distance. Il ne collecte aucune donnée.

L'application suit la langue du téléphone (anglais et français pour l'instant ; ajouter une langue revient à traduire les fichiers `values/strings.xml`).

## Ce que fait Rempart

| Protection | Comment | Module |
|---|---|---|
| Sites pour adultes, dans **tous** les navigateurs, en navigation privée aussi | VPN **local** de filtrage DNS (≈148 000 domaines, listes HaGeZi NSFW + StevenBlack), mots-clés pour les sites pas encore listés | `:filter-dns` |
| Recherche sécurisée imposée | Google, Bing, DuckDuckGo et YouTube (mode restreint) redirigés par DNS vers leur version stricte | `:filter-dns` |
| Contournement par DNS chiffré (DoH/DoT), DNS en dur (8.8.8.8…) | Résolveurs publics routés dans le VPN : DNS classique filtré, DoH/DoT/QUIC coupés, liste HaGeZi « DoH bypass » | `:filter-dns` |
| Navigateurs intégrés aux applis (Xender, Vidmate…), navigateurs inconnus | Service d'accessibilité : lit la barre d'adresse et le contenu des WebView, ferme la page, affiche un écran d'encouragement | `:filter-accessibility` |
| Impossible de désactiver ou désinstaller | Administrateur de l'appareil + garde des Réglages (fiche de l'app, accessibilité, VPN, DNS privé, désinstallation Play Store) | `:lock`, `:filter-accessibility` |
| Seules sorties | **Code du partenaire de confiance** (empreinte PBKDF2, pause croissante après 5 erreurs) **ou** demande de déverrouillage exécutée après **72 h** de temps réel, annulable | `:core`, `:lock` |
| Option avancée la plus solide | Mode « propriétaire de l'appareil » (ADB) : bloque aussi le mode sans échec, l'ajout d'utilisateurs, le changement de VPN, le DNS privé | `:lock`, [docs/DEVICE_OWNER.md](docs/DEVICE_OWNER.md) |
| Accompagnement | Compteur de jours sans rechute, écran d'encouragement avec respiration guidée, appel du partenaire, ressources d'aide, journal d'intégrité | `:app` |

**Limite honnête :** aucune application ne peut être rendue totalement impossible à retirer. Une réinitialisation d'usine efface toujours tout ; Rempart ne l'empêche volontairement pas. Voir [docs/SECURITE.md](docs/SECURITE.md) pour le modèle de menace complet.

## Architecture

```
:core                   Kotlin pur (sans Android) : listes, mots-clés, SafeSearch, verrou,
                        délai monotone, code partenaire, garde des Réglages, compteur.
                        → deviendra le module partagé Kotlin Multiplatform pour l'iPhone.
:filter-dns             VpnService local, analyse IP/UDP/DNS, résolveur amont filtrant,
                        mise à jour hebdomadaire des listes (WorkManager).
:filter-accessibility   AccessibilityService : navigateurs, WebView, garde des Réglages.
:lock                   Persistance du verrou, administrateur, mode propriétaire, battement.
:filter-image           (prévu) classifieur d'images sur l'appareil, optionnel.
:app                    Interface Jetpack Compose, assemblage des modules.
```

## Construire et tester

Prérequis : JDK 17+ et le SDK Android (installé par Android Studio). Ouvrir le dossier dans Android Studio (**File › Open**) ou utiliser la ligne de commande :

```bash
./gradlew test                      # tests unitaires et d'intégration (PC)
./gradlew :app:assembleDebug        # APK de développement
./gradlew connectedDebugAndroidTest # tests d'instrumentation (émulateur ou téléphone branché)
./gradlew lint                      # analyse statique
```

Tests de bout en bout sur l'émulateur (prépare le consentement VPN, l'accessibilité et l'administrateur, puis vérifie que la désinstallation est refusée) : `bash tools/e2e_emulator.sh`.

État actuel : 113 tests sur PC (unitaires, intégration, sécurité, performance, traductions) et 12 tests d'instrumentation verts sur un émulateur Android 16.

Mettre à jour les listes embarquées : `python tools/update_blocklists.py`.

## Licence

GPL-3.0 — voir [LICENSE](LICENSE). Les listes de blocage tierces et leurs licences sont décrites dans [NOTICE.md](NOTICE.md).

## Documentation

- [docs/SECURITE.md](docs/SECURITE.md) — modèle de menace, contournements testés
- [docs/DEVICE_OWNER.md](docs/DEVICE_OWNER.md) — mode propriétaire de l'appareil (avancé)
- [docs/CONFIDENTIALITE.md](docs/CONFIDENTIALITE.md) — politique de confidentialité
- [docs/CHECKLIST_PUBLICATION.md](docs/CHECKLIST_PUBLICATION.md) — avant la publication sur Google Play
- [docs/PLAN_IPHONE.md](docs/PLAN_IPHONE.md) — plan de la version iPhone
- [docs/maquettes/](docs/maquettes/) — maquettes des écrans
