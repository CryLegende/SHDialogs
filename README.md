<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHDialogs

*Menu SkyHand dans l'écran Échap et les actions rapides, et formulaire de signalement `/signaler`, avec les dialogues de Minecraft 1.21.6+.*

<img src="https://img.shields.io/badge/Paper-1.21.11-f2a900?style=for-the-badge" alt="Paper 1.21.11"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Deux fonctions, toutes deux construites avec l'API de dialogues de Paper (aucun datapack) :

- **Menu SkyHand** : un dialogue `skyhand:menu` enregistré au démarrage du serveur (bootstrapper) et ajouté aux tags `PAUSE_SCREEN_ADDITIONS` (bouton dans l'écran Échap) et `QUICK_ACTIONS` (touche « actions rapides » du client). Chaque bouton lance une commande **comme si le joueur la tapait** (`/is`, `/shop`, `/jobs`, `/hdv`, `/quest`, `/warp`, `/vote`, `/skyhand`, `/signaler`...). Libellés, infobulles, commandes et boutons de lien sont dans `config.yml`.
- **`/signaler [pseudo]`** : formulaire avec le pseudo du joueur, un motif (ceux de TigerReports) et des détails facultatifs. À la validation, le plugin lance `/report <pseudo> <motif - détails>` : TigerReports enregistre le signalement et répond au joueur comme d'habitude. Aucune dépendance de compilation à TigerReports.

Détails à connaître :

- **Commandes lancées par le joueur, jamais par la console.** Le plugin déclenche `PlayerCommandPreprocessEvent` avant d'exécuter la commande : c'est ainsi que DeluxeMenus ouvre ses menus pour `/is`, `/warp`, `/kits`... et que les plugins de restriction de commandes gardent la main.
- **L'aspect du menu est figé au démarrage** (le registre des dialogues ne se modifie plus ensuite). `/shdialogs reload` relit les commandes des boutons, les permissions, les messages et tout `/signaler` ; si l'aspect du menu a changé, il prévient qu'un redémarrage est nécessaire.
- **Le client n'est jamais cru sur parole** : la clé du bouton, le jeton du formulaire (usage unique, expire au bout de `expiration-secondes`), le pseudo, le motif et la longueur des détails sont revérifiés côté serveur. Une réponse sans formulaire en attente est refusée.
- **Clients plus anciens que 1.21.6** (protocole 771, détectés avec ViaVersion) : `/signaler` répond par un message qui renvoie vers `/report <joueur> <motif>`. Ils ne voient pas le menu (ils ne connaissent pas les dialogues) mais se connectent normalement.
- Les motifs de `config.yml` doivent reprendre **exactement** le nom des motifs de TigerReports (`Config.DefaultReasons.ReasonN.Name`) ; un écart est signalé dans la console au démarrage et au reload.

Utilisé sur : **Opale** (Purpur/DivineMC 1.21.11, Java 25).

## Dépendances

- Optionnelles : `ViaVersion` (version du client), `TigerReports` (la commande `/report`)

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/signaler [pseudo]` | — | Ouvre le formulaire de signalement. | `shdialogs.signaler` |
| `/shdialogs reload` | — | Recharge la configuration. | `shdialogs.admin` |
| `/shdialogs menu` | — | Ouvre le menu SkyHand à la demande (outil de test du staff). | `shdialogs.menu` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shdialogs.signaler` | Utiliser `/signaler`. | `true` |
| `shdialogs.admin` | Recharger la configuration. | `op` |
| `shdialogs.menu` | Ouvrir le menu SkyHand par commande. | `op` |

## Configuration

Fichier par défaut repris dans le jar. Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHDialogs/
└── <a href="src/main/resources/config.yml">config.yml</a>
    ├── prefixe, couleurs        préfixe des messages et couleur par type (erreur, succès, info)
    ├── menu                     menu SkyHand (figé au démarrage, sauf les commandes)
    │   ├── actif, ecran-echap, actions-rapides
    │   ├── titre, titre-bouton, texte, colonnes, largeur-bouton, bouton-fermer
    │   └── boutons              un bloc par bouton : libelle, infobulle, commande (ou url), permission, actif
    ├── signalement              /signaler
    │   ├── commande             commande lancée par le joueur (par défaut report {joueur} {raison})
    │   ├── protocole-minimum, cooldown-secondes, expiration-secondes
    │   ├── pseudo-regex, pseudo-longueur-max, refuser-joueur-inconnu
    │   ├── details-longueur-max, details-lignes, details-hauteur
    │   ├── format-raison, raison-longueur-max
    │   ├── titre, texte, champs, boutons, infobulles, largeur
    │   ├── erreurs              erreurs affichées en haut du formulaire
    │   └── motifs               id, libelle, raison (texte exact du motif TigerReports)
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
