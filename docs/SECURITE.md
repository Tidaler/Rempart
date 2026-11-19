# Sécurité et contournements

Rempart protège une personne **contre elle-même dans un moment de faiblesse**, pas contre un expert déterminé disposant de temps. Le but est de rendre chaque contournement assez long et pénible pour que l'envie retombe (souvent en moins de 15 minutes).

## Couches de protection

1. **VPN DNS local** (`:filter-dns`) : tout le DNS du téléphone passe par Rempart.
2. **Accessibilité** (`:filter-accessibility`) : barre d'adresse des navigateurs, WebView des applis, garde des Réglages.
3. **Administrateur de l'appareil** (`:lock`) : Android refuse la désinstallation.
4. **Verrou** (`:core`, `:lock`) : code partenaire ou délai de 72 h.
5. **Option : propriétaire de l'appareil** : restrictions système (voir [DEVICE_OWNER.md](DEVICE_OWNER.md)).
6. **Résolveur amont filtrant** (Cloudflare for Families par défaut) : seconde barrière pour les domaines absents des listes.

## Contournements et réponses (chacun couvert par un test automatisé)

| Contournement | Réponse | Test |
|---|---|---|
| Navigation privée | Le DNS et la barre d'adresse restent visibles par Rempart | `ContentPolicyTest`, `ScreenAnalyzerTest.incognito…` |
| Majuscules, point final, port, `https://user@…`, punycode / IDN | Normalisation avant comparaison | `DomainBlocklistTest` |
| Sous-domaine (`m.`, `www.`, `fr.`) | Correspondance « joker » sur tous les parents | `DomainBlocklistTest` |
| Site récent pas encore listé | Mots-clés dans le nom de domaine, résolveur amont filtrant, mises à jour hebdomadaires | `ContentPolicyTest`, `DnsFilterEngineTest` |
| Recherche explicite (images Google…) | SafeSearch imposé par DNS + mots-clés dans la barre d'adresse | `SafeSearchTest`, `ScreenAnalyzerTest` |
| « DNS sécurisé » de Chrome, DoH de Firefox, DoH d'une appli | Noms DoH bloqués, IP des résolveurs routées et coupées (TCP RST), canari Firefox `use-application-dns.net` → NXDOMAIN | `DnsFilterEngineTest` |
| DNS en dur vers 8.8.8.8 / 1.1.1.1 | Intercepté dans le VPN et **filtré** | `DnsFilterEngineTest.hardcoded plain DNS…` |
| DNS privé Android (DoT) en mode strict | Écran « DNS privé » bloqué par le garde ; en mode propriétaire, restriction système | `SettingsGuardTest` |
| CNAME vers un domaine interdit | Réponse amont analysée | `DnsFilterEngineTest.CNAME cloaking…` |
| Paquets malformés | Analyseurs robustes (fuzzing 20 000 paquets) | `PacketCodecTest` |
| Autre VPN (gratuit, proxy) | Un seul VPN actif sur Android : l'activer coupe Rempart → événement au journal, notification ; la fiche VPN est protégée ; « VPN permanent + bloquer sans VPN » recommandé ; en mode propriétaire, `DISALLOW_CONFIG_VPN` | `SettingsGuardTest` |
| Désactiver l'accessibilité | Écran de Rempart dans Accessibilité bloqué pendant le verrou ; interruption journalisée | `SettingsGuardTest`, `ScreenAnalyzerTest` |
| Désinstaller (Réglages, Play Store, appui long) | Administrateur actif + écrans bloqués | `SettingsGuardTest` |
| Arrêt forcé / vider les données | Fiche de l'app bloquée ; le délai et la pause anti-force brute survivent à l'arrêt | `LockRepositoryTest` |
| Changer l'heure ou le fuseau | Le délai n'utilise que l'horloge monotone (`elapsedRealtime`), jamais l'heure | `LockEngineTest.changing the wall clock…` |
| Redémarrer en boucle | Le délai ne peut jamais compter plus que le temps réellement écoulé | `LockEngineTest.countdown never counts more…` |
| Deviner le code partenaire | 5 essais, puis pause 1 min, 2 min, 4 min… jusqu'à 24 h, conservée après redémarrage de l'app | `LockEngineTest`, `LockRepositoryTest` |
| Lire le code dans les données de l'app (root) | Seule une empreinte PBKDF2-SHA256 salée est stockée | `PartnerSecretAndCodecTest` |
| Modifier les données du verrou (root) | Décodage défensif : en cas de doute, on reste verrouillé | `PartnerSecretAndCodecTest`, `LockRepositoryTest` |
| Sauvegarde / restauration | Sauvegardes désactivées (`allowBackup=false`, règles d'extraction vides) | manifeste |

## Limites connues (assumées et documentées)

- **Réinitialisation d'usine** : efface tout. Volontairement jamais bloquée.
- **Mode sans échec** : les applis tierces n'y tournent pas. Sans mode propriétaire, on peut y retirer l'administrateur. L'interruption est journalisée au retour (« Protection interrompue »). Le mode propriétaire bloque le mode sans échec.
- **Autre utilisateur / invité** : Rempart ne protège que l'utilisateur où il est installé. Le mode propriétaire bloque l'ajout d'utilisateurs.
- **ADB** (débogage USB) : un utilisateur averti peut désactiver l'accessibilité par ADB. Ne pas laisser le débogage USB activé.
- **VPN de contournement chiffrant tout le trafic** : s'il remplace Rempart, Rempart ne filtre plus (Android n'autorise qu'un VPN). D'où le « VPN permanent + bloquer les connexions sans VPN » et, en mode propriétaire, `DISALLOW_CONFIG_VPN`.
- **Images dénudées sur des sites généralistes** (réseaux sociaux, recherche d'images) : couvert partiellement par SafeSearch et les mots-clés. Le classifieur d'images sur l'appareil (`:filter-image`) est prévu en option.
