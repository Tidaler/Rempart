# Mode « propriétaire de l'appareil » (avancé)

Le mode propriétaire (*Device Owner*) donne à Rempart des pouvoirs qu'une appli normale n'a pas. C'est la protection **la plus solide**, mais elle demande un ordinateur et un téléphone **sans aucun compte** (Google, Samsung, Xiaomi…) au moment de l'activation.

## Ce que le mode propriétaire ajoute

| Restriction | Effet |
|---|---|
| `setUninstallBlocked` | Désinstallation impossible, même en retirant l'administrateur |
| `DISALLOW_SAFE_BOOT` | Plus de mode sans échec |
| `DISALLOW_ADD_USER`, `DISALLOW_USER_SWITCH` | Pas d'autre utilisateur ou invité non filtré |
| `DISALLOW_CONFIG_VPN` + VPN permanent verrouillé | Impossible de remplacer Rempart par un autre VPN ; aucun trafic sans le filtre |
| `DISALLOW_CONFIG_PRIVATE_DNS` (Android 10+) | DNS privé non modifiable |

La **réinitialisation d'usine n'est jamais bloquée** : c'est la sortie de dernier recours, voulue.

Ces restrictions ne s'appliquent que **quand le verrou est actif**. Quand il est ouvert (code partenaire ou délai écoulé), le bouton « Désactiver la protection » les lève et retire le mode propriétaire.

## Activation

1. Sauvegarder ses données, puis **retirer tous les comptes** du téléphone (Réglages › Comptes). Sur un téléphone neuf ou réinitialisé, sauter l'ajout de compte.
2. Activer le **débogage USB** (Réglages › À propos › appuyer 7 fois sur « Numéro de build », puis Options pour les développeurs › Débogage USB).
3. Installer Rempart, puis brancher le téléphone à l'ordinateur et lancer :

   ```bash
   adb shell dpm set-device-owner org.tidalex.rempart/.lock.RempartAdminReceiver
   ```

4. Rajouter ses comptes, ouvrir Rempart, terminer la mise en place et verrouiller.
5. **Désactiver le débogage USB** (sinon ADB reste une porte de sortie).

## Retrait

Ouvrir le verrou (code partenaire ou délai), puis « Désactiver la protection ». En dernier recours : réinitialisation d'usine.
