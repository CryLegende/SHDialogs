<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHDialogs

*Menu SkyHand dans l'écran Échap et les actions rapides, avec les dialogues de Minecraft 1.21.6+.*

<img src="https://img.shields.io/badge/Paper-1.21.11-f2a900?style=for-the-badge" alt="Paper 1.21.11"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

**Menu SkyHand** : un dialogue `skyhand:menu` enregistré au démarrage du serveur (bootstrapper, aucun datapack) et ajouté aux tags `PAUSE_SCREEN_ADDITIONS` (bouton dans l'écran Échap) et `QUICK_ACTIONS` (touche « actions rapides » du client). Chaque bouton lance une commande **comme si le joueur la tapait** : `/is`, `/is more`, `/warp`, `/regles`, `/shop`, `/jobs`, `/hdv`, `/quest`, `/vote`, `/skyhand`. Libellés, infobulles, commandes et boutons de lien sont dans `config.yml`.

Rendu aux couleurs du serveur (même charte que les menus DeluxeMenus) :

- **Titre** `« SkyHand » Menu`, puis deux lignes grises avec les mots clés en jaune, dont une astuce discrète.
- **Boutons** sur 2 colonnes, sans gras, d'une seule couleur par thème : aqua pour la navigation (Mon île, Menu général, Warps, Règlement), vert pour l'économie (Boutique, Métiers, Hôtel des ventes), rose pour la progression (Quêtes, Votes et récompenses, Fonctionnalités uniques). Les libellés évitent le gris foncé, illisible sur le fond gris des boutons.
- **Infobulles** structurées comme celles des menus : phrase encadrée `« ... »` aux couleurs du thème, ligne `Commande: /xxx`, puis `(( Cliquez pour ouvrir ))`.
- **Bouton de fermeture** `⤾ Fermer`, comme le bouton Retour des menus.

Détails à connaître :

- **Commandes lancées par le joueur, jamais par la console.** Le plugin déclenche `PlayerCommandPreprocessEvent` avant d'exécuter la commande : c'est ainsi que DeluxeMenus ouvre ses menus pour `/is`, `/warp`, `/kits`... et que les plugins de restriction de commandes gardent la main.
- **L'aspect du menu est figé au démarrage** (le registre des dialogues ne se modifie plus ensuite). `/shdialogs reload` relit les commandes des boutons, les permissions et les messages ; si l'aspect du menu a changé, il prévient qu'un redémarrage est nécessaire.
- **Le client n'est jamais cru sur parole** : au clic, la commande est retrouvée dans `config.yml` d'après l'identifiant du bouton, jamais d'après ce que dit le client.
- **Nombre pair de boutons** : le menu est sur 2 colonnes, gardez un nombre pair de boutons actifs (les liens web, Discord, site, vote et boutique, se désactivent par deux).
- **Clients plus anciens que 1.21.6** (protocole 771, détectés avec ViaVersion) : ils ne voient pas le menu (ils ne connaissent pas les dialogues) mais se connectent normalement.

### Dialogues à la demande

La section `dialogues` de `config.yml` décrit autant de dialogues que voulu (titre, texte, boutons « url » ou « commande »). `/shdialogs open <dialogue>` en ouvre un : c'est ce que lancent les menus DeluxeMenus avec `[player] shdialogs open vote`, à la suite du texte classique du menu (l'adresse reste posée dans le chat). Dialogues fournis : `vote`, `discord`, `discord-liaison`, `nitro`, `boutique`, `grades`, `reglement`.

- **Rechargés sans redémarrage** : contrairement au menu SkyHand, ils sont construits à l'exécution, `/shdialogs reload` suffit pour ajouter ou modifier un dialogue.
- **Une adresse par site** : changez-la dans le dialogue, tous les menus qui l'ouvrent la suivent.
- **Clients plus anciens que la 1.21.6** : pas de repli, seulement un avertissement envoyé à celui qui a lancé la commande (le joueur lui-même, ou le staff / la console s'ils l'ont ouvert à quelqu'un d'autre). Le texte classique du menu DeluxeMenus leur suffit.
- **Boutons de commande** : lancés par le joueur comme ceux du menu, la commande est retrouvée dans `config.yml` d'après les identifiants du dialogue et du bouton.
- **Pas de `[close]` dans les menus** : un `[close]` laisse un instant sans écran entre le menu et le dialogue (clignotement). Le menu reste affiché, et SHDialogs le ferme au tick suivant, juste avant d'ouvrir le dialogue (même tick, même image côté client).

Utilisé sur : **Opale** (Purpur/DivineMC 1.21.11, Java 25).

## Dépendances

- Optionnelle : `ViaVersion` (version du client, pour refuser `/shdialogs menu` aux clients trop anciens)

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shdialogs reload` | — | Recharge la configuration. | `shdialogs.admin` |
| `/shdialogs menu` | — | Ouvre le menu SkyHand à la demande (outil de test du staff). | `shdialogs.menu` |
| `/shdialogs open <dialogue> [joueur]` | — | Ouvre un dialogue de la section `dialogues` (à soi-même, ou à un joueur pour la console et le staff). | `shdialogs.open` (tous), `shdialogs.open.others` (op) |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shdialogs.admin` | Recharger la configuration. | `op` |
| `shdialogs.menu` | Ouvrir le menu SkyHand par commande. | `op` |
| `shdialogs.open` | Ouvrir un dialogue pour soi-même (menus DeluxeMenus). | tous |
| `shdialogs.open.others` | Ouvrir un dialogue à un autre joueur. | `op` |

## Configuration

Fichier par défaut repris dans le jar ; il est créé au premier démarrage s'il n'existe pas. Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHDialogs/
└── <a href="src/main/resources/config.yml">config.yml</a>
    ├── prefixe, couleurs        préfixe des messages et couleur par type (erreur, succès, info)
    ├── menu                     menu SkyHand (figé au démarrage, sauf les commandes)
    │   ├── actif, ecran-echap, actions-rapides
    │   ├── titre, titre-bouton, texte, colonnes, largeur-bouton, bouton-fermer
    │   └── boutons              un bloc par bouton : libelle, infobulle, commande (ou url), permission, actif
    │       ├── ile, general, warps, reglement        navigation
    │       ├── boutique, metiers, hdv                économie
    │       ├── quetes, vote, skyhand                 progression
    │       └── discord, site, voter, boutique-web    liens web (bouton « url »)
    ├── dialogues                dialogues à la demande (rechargés par /shdialogs reload)
    │   └── vote, discord, discord-liaison, nitro, boutique, grades, reglement
    └── messages                 messages du chat, par type (erreur, succes, info)
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`. Il n'utilise que des classes présentes dans `paper-api 1.21.11` et dans `paper-api 26.3` (bytecode identique).

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
