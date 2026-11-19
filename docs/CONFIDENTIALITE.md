# Politique de confidentialité de Rempart

*Dernière mise à jour : 19 novembre 2026*

Rempart est une application gratuite et open source (GPL-3.0) qui aide son utilisateur à bloquer les contenus pour adultes sur son propre téléphone.

## Données collectées : aucune

Rempart **ne collecte, ne stocke sur un serveur, ne vend et ne partage aucune donnée personnelle**. Il n'y a ni compte, ni publicité, ni outil d'analyse, ni traceur.

## Ce qui reste sur le téléphone

Uniquement sur l'appareil, dans le stockage privé de l'application :
- l'état du verrou et une **empreinte chiffrée** (PBKDF2) du code du partenaire, jamais le code lui-même ;
- le prénom et le numéro du partenaire, si l'utilisateur les saisit (pour le bouton « Appeler ») ;
- des compteurs : jours sans rechute, nombre de contenus bloqués (jamais la liste des sites) ;
- un journal d'intégrité (ex. « verrou activé », « filtre coupé »), sans aucun site visité.

Ces données ne sont pas incluses dans les sauvegardes et sont effacées à la désinstallation.

## Service VPN

Rempart utilise l'API `VpnService` d'Android pour créer un **VPN local** sur le téléphone. Il ne fait passer par ce VPN que les requêtes DNS et les connexions vers des résolveurs DNS publics connus (pour empêcher le contournement). **Aucun trafic n'est envoyé à un serveur de Rempart** ; le reste du trafic ne passe pas par le VPN.

Les requêtes DNS autorisées sont transmises au résolveur filtrant choisi par l'utilisateur (par défaut Cloudflare for Families ; aussi AdGuard DNS Family ou CleanBrowsing), comme le ferait n'importe quel téléphone avec son DNS habituel. Les requêtes vers des domaines bloqués ne sortent pas du téléphone.

## Service d'accessibilité

Rempart utilise l'API d'accessibilité d'Android, avec le consentement explicite de l'utilisateur, pour lire l'adresse et le texte des pages affichées, afin de fermer les contenus pour adultes, et pour empêcher l'accès aux réglages qui désactiveraient la protection quand l'utilisateur l'a verrouillée. Ces informations sont analysées sur le téléphone et oubliées immédiatement ; elles ne sont **ni enregistrées ni transmises**.

## Administrateur de l'appareil

Utilisé uniquement pour empêcher la désinstallation pendant le verrouillage choisi par l'utilisateur. Aucune autre politique n'est demandée.

## Réseau

Une fois par semaine, en Wi-Fi, Rempart télécharge des listes publiques de domaines depuis GitHub (raw.githubusercontent.com). Aucune information sur l'utilisateur n'est envoyée.

## Usage personnel uniquement

Rempart est conçu pour être installé par une personne sur son propre téléphone, de son plein gré. Il n'a pas de mode caché et ne permet aucune surveillance à distance.

## Contact

Questions : ouvrir un ticket sur https://github.com/Tidaler/Rempart/issues
