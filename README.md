# Rempart

Rempart est une appli Android pour arrêter la pornographie. Elle bloque les sites pour adultes et les images trop dénudées sur tout le téléphone. Et surtout, elle tient bon dans les moments de faiblesse : une fois verrouillée, on ne peut plus la couper sur un coup de tête.

Elle est gratuite et open source. Pas de pub, pas d'achat, pas de compte. Rien ne quitte le téléphone.

Rempart sert à se protéger soi-même, sur son propre téléphone. Il n'a pas de mode caché et ne permet de surveiller personne.

L'appli s'affiche dans la langue du téléphone. Pour l'instant : anglais et français. Pour ajouter une langue, il suffit de traduire les fichiers `strings.xml`.

## Comment ça marche

Rempart combine plusieurs protections, parce qu'aucune ne suffit seule.

**Un filtre réseau sur le téléphone.** Rempart crée un VPN local qui ne sort pas du téléphone. Il intercepte les demandes d'adresse (DNS) et refuse environ 148 000 sites pour adultes (listes HaGeZi et StevenBlack). Des mots-clés attrapent les sites trop récents pour être dans les listes. Google, Bing, DuckDuckGo et YouTube passent en mode strict.

**Pas de contournement par DNS chiffré.** Chrome, Firefox ou une appli peuvent essayer de demander les adresses à Google ou Cloudflare en direct. Rempart coupe ces connexions, et le navigateur revient au DNS normal, donc filtré.

**Un œil sur les navigateurs et les applis.** Grâce au service d'accessibilité, Rempart lit la barre d'adresse des navigateurs, y compris en navigation privée. Il regarde aussi les pages ouvertes dans d'autres applis (Xender, Vidmate…). Si c'est interdit, la page se ferme et un écran d'encouragement apparaît.

**Un verrou.** Une fois verrouillé, Android refuse de désinstaller Rempart, et les réglages qui permettraient de le couper ne s'ouvrent plus. Il n'y a que deux façons d'en sortir :
- le code de ton partenaire de confiance (après 5 erreurs, il faut attendre de plus en plus longtemps) ;
- une demande de déverrouillage, qui ne s'exécute qu'après 72 h de téléphone allumé. Tu peux l'annuler à tout moment. Changer l'heure ne sert à rien.

**Pour les plus motivés**, le mode « propriétaire de l'appareil » bloque aussi le mode sans échec, l'ajout d'utilisateurs et le changement de VPN. Il s'active avec un ordinateur, voir [docs/DEVICE_OWNER.md](docs/DEVICE_OWNER.md).

**De l'aide, pas seulement des murs.** Un compteur de jours sans rechute, un écran de respiration quand l'envie monte, un bouton pour appeler ton partenaire, des ressources d'aide et un journal qui montre si quelqu'un a tenté de couper la protection.

Soyons clairs sur une limite : aucune appli ne peut être impossible à retirer. Une réinitialisation d'usine efface tout, et Rempart ne l'empêche pas exprès. Le détail des contournements testés est dans [docs/SECURITE.md](docs/SECURITE.md).

## Organisation du code

```
:core                   Kotlin pur, sans Android : listes, mots-clés, SafeSearch, verrou,
                        délai, code partenaire, garde des Réglages, compteur.
                        Il servira de base commune pour la future version iPhone.
:filter-dns             VPN local, lecture des paquets DNS, résolveur filtrant,
                        mise à jour des listes chaque semaine.
:filter-accessibility   Service d'accessibilité : navigateurs, pages dans les applis, Réglages.
:lock                   Sauvegarde du verrou, administrateur, mode propriétaire, battement.
:filter-image           Détection d'images (en préparation, sans modèle pour l'instant).
:app                    Interface Jetpack Compose, qui relie le tout.
```

## Construire et tester

Il faut un JDK 17 ou plus récent et le SDK Android (Android Studio l'installe). Ouvre le dossier dans Android Studio avec **File › Open**, ou passe par la ligne de commande :

```bash
./gradlew test                  # tests sur PC
./gradlew :app:assembleDebug    # APK de développement
./gradlew lint                  # analyse du code
bash tools/e2e_emulator.sh      # tests sur l'émulateur ou un téléphone de test
```

Le script `e2e_emulator.sh` prépare l'appareil (VPN, accessibilité, administrateur), lance les tests, puis vérifie qu'Android refuse bien la désinstallation.

Aujourd'hui, 113 tests passent sur PC et 12 sur un émulateur Android 16.

Pour mettre à jour les listes livrées avec l'appli : `python tools/update_blocklists.py`.

## Licence

GPL-3.0, voir [LICENSE](LICENSE). Les listes tierces et leurs licences sont dans [NOTICE.md](NOTICE.md).

## Pour aller plus loin

- [Sécurité et contournements](docs/SECURITE.md)
- [Mode propriétaire de l'appareil](docs/DEVICE_OWNER.md)
- [Politique de confidentialité](docs/CONFIDENTIALITE.md)
- [Avant de publier sur Google Play](docs/CHECKLIST_PUBLICATION.md)
- [Plan pour l'iPhone](docs/PLAN_IPHONE.md)
- [Maquettes des écrans](docs/maquettes/)
