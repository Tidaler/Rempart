# Checklist avant publication sur Google Play

Cocher chaque point avant d'envoyer une version en production.

## Nom et identité
- [ ] Vérifier la marque « Rempart » en classe 9 sur [data.inpi.fr](https://data.inpi.fr) et [EUIPO eSearch](https://euipo.europa.eu/eSearch/). La recherche automatique n'a trouvé aucune appli de filtrage « Rempart » sur les stores (seulement « Mutuelle du Rempart » `fr.mutlab.rempart`, des jeux et des sites touristiques). La base INPI demande une vérification manuelle (protection anti-robot).
- [ ] Titre Play distinctif : « Rempart - Bloqueur de contenu adulte » (30 caractères max).
- [ ] Identifiant de paquet définitif (`org.tidalex.rempart` : **ne pourra plus changer** après la première publication).

## Qualité du code
- [ ] `./gradlew test` : tous les tests verts.
- [ ] `./gradlew connectedDebugAndroidTest` sur un émulateur Android récent **et** sur un vrai téléphone.
- [ ] `./gradlew lint` sans erreur.
- [ ] `./gradlew :app:bundleRelease` : R8 (minification et réduction des ressources) actif, appli testée en version release.
- [ ] Test manuel des contournements de [SECURITE.md](SECURITE.md) sur un vrai téléphone (navigation privée, DNS sécurisé de Chrome, autre VPN, arrêt forcé, changement d'heure, redémarrage).
- [ ] Batterie : 24 h d'usage normal, comparer `adb shell dumpsys batterystats org.tidalex.rempart` (objectif < 2 % par jour).

## Signature
- [ ] Créer la clé d'envoi (`keytool -genkeypair -v -keystore rempart-upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias rempart`) et la **sauvegarder hors du dépôt** (deux copies).
- [ ] Remplir `keystore.properties` (ignoré par git) : `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
- [ ] Activer **Play App Signing** dans la console.

## Règles Google Play (les plus sensibles pour Rempart)
- [ ] **VpnService** : déclaration dans la console (« Fonctionnalité principale : filtrage de contenu sur l'appareil »). L'appli ne doit pas collecter de données via le VPN. Ne pas utiliser le VPN pour la publicité ni pour rediriger le trafic.
- [ ] **Accessibilité** : `isAccessibilityTool="false"` → remplir le formulaire de déclaration avec une **vidéo** montrant la divulgation, le consentement et l'usage (fermeture d'une page adulte, garde des Réglages). La divulgation in-app apparaît avant l'ouverture des réglages d'accessibilité (déjà en place).
- [ ] **Administrateur de l'appareil** : justification (empêcher la désinstallation pendant le verrou choisi par l'utilisateur), divulgation claire, sortie possible (partenaire / délai).
- [ ] **Service de premier plan** `systemExempted` (VPN) : déclaration du type dans la console.
- [ ] Politique « Appareil et réseau » : pas de comportement caché, pas d'installation à l'insu d'autrui, sortie toujours possible (réinitialisation d'usine non bloquée, délai annulable).
- [ ] Classification du contenu : questionnaire IARC (l'appli ne montre aucun contenu adulte).
- [ ] Public cible : 18 ans et plus (outil d'auto-assistance pour adultes ; pas destiné aux enfants).

## Confidentialité
- [ ] Publier [CONFIDENTIALITE.md](CONFIDENTIALITE.md) à une URL publique (GitHub Pages) et la renseigner dans la console.
- [ ] Section « Sécurité des données » : aucune donnée collectée, aucune donnée partagée, données chiffrées en transit (sans objet), possibilité de suppression (désinstallation).
- [ ] Vérifier qu'aucune bibliothèque d'analyse ou de publicité n'a été ajoutée.

## Accessibilité de l'interface
- [ ] Parcours complet avec TalkBack.
- [ ] Taille de police maximale : rien n'est coupé.
- [ ] Contrastes vérifiés en thème clair et sombre.

## Fiche Play
- [ ] Captures d'écran (téléphone) en anglais et en français ; icône 512 × 512 ; bannière 1024 × 500.
- [ ] Description courte et longue, dans chaque langue livrée, mentionnant : gratuit, open source, sans publicité, sans collecte, usage personnel.
- [ ] Lien vers le code source.

## Langues
- [ ] `TranslationsTest` vert (chaque texte présent dans chaque langue).
- [ ] Vérifier l'appli avec le téléphone en anglais et en français, et via Réglages › Applis › Rempart › Langue (Android 13+).
