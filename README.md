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

- **Titre** sur deux lignes en tête du texte : `« SkyHand »` à la couleur du dialogue, puis le titre en blanc (`Menu du serveur`, `Bienvenue sur le serveur !`...). La barre de titre reste vide : l'icône d'avertissement du client y est seule. `titre` s'écrit en une ligne ou en liste de lignes, puis deux lignes grises avec les mots clés en jaune, dont une astuce discrète.
- **Boutons** sur 2 colonnes, sans gras, d'une seule couleur par thème : aqua pour la navigation (Mon île, Menu général, Warps, Règlement), vert pour l'économie (Boutique, Métiers, Hôtel des ventes), rose pour la progression (Quêtes, Votes et récompenses, Fonctionnalités uniques). Les libellés évitent le gris foncé, illisible sur le fond gris des boutons.
- **Infobulles** structurées comme celles des menus : phrase encadrée `« ... »` aux couleurs du thème, ligne `Commande: /xxx`, puis `(( Cliquez pour ouvrir ))`.
- **Bouton de fermeture** `⤾ Fermer`, comme le bouton Retour des menus.
- **Fond noir** : le joueur est aveuglé (effet Cécité) tant qu'un dialogue ouvert par le serveur est affiché, le texte se lit mieux que sur le décor. L'effet est retiré au clic sur un bouton du dialogue (les boutons « fermer » et le bouton du bas préviennent le serveur, Échap aussi quand il y a un bouton en bas) ; pour un bouton de lien ou Échap sans bouton en bas, le client ne dit rien : l'effet est retiré dès que le joueur tourne la caméra, se déplace ou ouvre un inventaire. Option `aveugler-pendant-dialogues`. Le menu ouvert depuis l'écran Échap ne passe pas par le serveur et n'est pas concerné.

Détails à connaître :

- **Commandes lancées par le joueur.** Seuls les dialogues de la section `dialogues` peuvent en lancer par la console (`[console]`, voir plus bas). Le plugin déclenche `PlayerCommandPreprocessEvent` avant d'exécuter la commande : c'est ainsi que DeluxeMenus ouvre ses menus pour `/is`, `/warp`, `/kits`... et que les plugins de restriction de commandes gardent la main.
- **L'aspect du menu est figé au démarrage** (le registre des dialogues ne se modifie plus ensuite). `/shdialogs reload` relit les commandes des boutons, les permissions et les messages ; si l'aspect du menu a changé, il prévient qu'un redémarrage est nécessaire.
- **Le client n'est jamais cru sur parole** : au clic, la commande est retrouvée dans `config.yml` d'après l'identifiant du bouton, jamais d'après ce que dit le client.
- **Nombre pair de boutons** : le menu est sur 2 colonnes, gardez un nombre pair de boutons actifs (les liens web, Discord, site, vote et boutique, se désactivent par deux).
- **Clients plus anciens que 1.21.6** (protocole 771, détectés avec ViaVersion) : ils ne voient pas le menu (ils ne connaissent pas les dialogues) mais se connectent normalement.

### Dialogues à la demande

La section `dialogues` de `config.yml` décrit autant de dialogues que voulu (titre, texte, boutons « url » ou « commande »). `/shdialogs open <dialogue>` en ouvre un : c'est ce que lancent les menus DeluxeMenus avec `[player] shdialogs open vote`, à la suite du texte classique du menu (l'adresse reste posée dans le chat). Dialogues fournis : `vote`, `discord`, `discord-liaison`, `nitro`, `boutique`, `grades`, `reglement`, `bienvenue`, `ami`.

- **Rechargés sans redémarrage** : contrairement au menu SkyHand, ils sont construits à l'exécution, `/shdialogs reload` suffit pour ajouter ou modifier un dialogue.
- **Une adresse par site** : changez-la dans le dialogue, tous les menus qui l'ouvrent la suivent.
- **Clients plus anciens que la 1.21.6** : pas de repli, seulement un avertissement envoyé à celui qui a lancé la commande (le joueur lui-même, ou le staff / la console s'ils l'ont ouvert à quelqu'un d'autre). Le texte classique du menu DeluxeMenus leur suffit.
- **Boutons de commande** : lancés par le joueur comme ceux du menu, la commande est retrouvée dans `config.yml` d'après les identifiants du dialogue et du bouton. `commande` s'écrit en une ligne ou en liste de lignes lancées dans l'ordre, chacune sans préfixe ou `[player] ...` (le joueur), `[console] ...` (la console) ou `[message] ...` (un message dans le chat du joueur).
- **Valeurs** : `/shdialogs open <dialogue> <joueur> <valeur 1> <valeur 2>...` remplace `{1}`, `{2}`... dans le titre, le texte, les libellés, les infobulles et les commandes ; la dernière valeur prend le reste de la ligne (espaces et couleurs compris), `{joueur}` est le pseudo. Un dialogue qui attend `{1}` ne s'ouvre pas sans ses valeurs (la console dit combien il en faut).
- **Sécurité des valeurs et de `[console]`** : des valeurs ne se donnent que par la console ou le staff (`shdialogs.open.others`), et un dialogue qui a un bouton `[console]` ne s'ouvre que par eux, même pour soi-même. Au clic, le serveur ne lance un tel bouton que si c'est ce dialogue qu'il a ouvert à ce joueur, et une seule fois par ouverture (un clic renvoyé par le client, ou après « fermer », est refusé). Le menu SkyHand refuse `[console]` : il s'ouvre depuis l'écran Échap sans passer par le serveur.
- **`attendre-menu: true`** : si le joueur a un menu ouvert (le lot d'une box qu'il regarde encore...), le dialogue s'affiche quand il le ferme (dans les 2 minutes) au lieu de fermer le menu.
- **Exemple, les cosmétiques gagnés dans une box** : la récompense du lot lance `shdialogs open nouveau-compagnon %player_name% Teckel &fCompagnon &6Teckel` ; le dialogue `nouveau-compagnon` dit « Compagnon Teckel est à vous ! », attend que le menu de la box soit fermé, et son bouton « Équiper maintenant » lance `[console] shcompagnons set {joueur} {1}` puis `[message] ... Vous avez équipé : {2}`. Ajouter un cosmétique à une box = une ligne dans ses commandes ; ce que fait le bouton reste dans `config.yml`.
- **Boutons « fermer »** (`fermer: true`) : ferment simplement le dialogue. Avec `bouton-fermer: ''`, le dialogue n'a plus de bouton en bas : c'est ainsi que `bienvenue` place « C'est parti ! » en premier, au-dessus de « Je joue avec un ami » et « Tutoriel détaillé ».
- **Pas de `[close]` dans les menus** : un `[close]` laisse un instant sans écran entre le menu et le dialogue (clignotement). Le menu reste affiché, et SHDialogs le ferme au tick suivant, juste avant d'ouvrir le dialogue (même tick, même image côté client).

### Dossier dialogues

Les dialogues peuvent aussi être rangés dans `plugins/SHDialogs/dialogues/`. Chaque fichier `.yml` du dossier, sous-dossiers compris, ajoute des dialogues : les clés de premier niveau sont les identifiants, avec le même format que la section `dialogues` de `config.yml` (sans cette section au-dessus, les dialogues s'écrivent à la racine du fichier). `/shdialogs reload` relit le dossier comme `config.yml`.

Les fichiers sont lus après `config.yml`, par ordre alphabétique de chemin. Un identifiant déjà défini est refusé, le premier lu gagne et le doublon est signalé par `/shdialogs reload` et `/shdialogs list`. Un fichier illisible empêche le rechargement, l'ancienne configuration reste en place et le fichier est nommé ; au démarrage il est ignoré et signalé dans la console et dans `/shdialogs list`.

### Placeholders PlaceholderAPI

Avec PlaceholderAPI (dépendance facultative), les `%...%` du `titre`, du `texte`, des `libelle`, des `infobulle`, du `bouton-fermer`, du champ texte (`libelle` et `defaut`) et des valeurs des conditions sont remplacés pour le joueur qui reçoit le dialogue, à l'ouverture. `{1}`, `{2}` et `{joueur}` sont remplacés d'abord. Sans PlaceholderAPI les textes restent tels quels. Les commandes des boutons ne reçoivent pas de placeholders, elles utilisent `{joueur}`.

### Conditions

Une condition est une chaîne `"<A> <opérateur> <B>"`, avec l'un de ces opérateurs.

| Opérateur | Sens |
|---|---|
| `==`, `!=` | égal, différent |
| `>=`, `<=`, `>`, `<` | comparaison de nombres |
| `contient`, `!contient` | A contient B, A ne contient pas B (texte) |

A et B passent par les valeurs (`{1}`, `{joueur}`, et `{saisie}` pour une exigence) puis par PlaceholderAPI. Si A et B se lisent comme des nombres (espaces et séparateurs de milliers ignorés, virgule ou point décimal, par exemple `300 000`, `1,5` ou `1.000.000`), ils sont comparés comme des nombres. Sinon le texte est comparé sans les codes couleur, sans tenir compte de la casse ni des accents, et `>=`, `<=`, `>` et `<` sont faux. Une liste de conditions (`si`) est vraie quand toutes le sont.

### Versions

La section `versions` d'un dialogue le change selon le joueur. Chaque version a un nom, des conditions (`si`) et peut remplacer `titre`, `texte`, `boutons` (tous à la fois), `bouton-fermer`, `colonnes`, `largeur-bouton` et `champ` (`false` pour le retirer). Tout le reste vient du dialogue.

À l'ouverture, la première version, dans l'ordre du fichier, dont toutes les conditions sont vraies est montrée, sinon le dialogue lui-même. Le serveur retient la version montrée à chaque joueur : au clic, le bouton est cherché dans cette version, jamais d'après ce que dit le client. Un dialogue à versions est construit à chaque ouverture.

```yaml
grade:
  titre:
    - '&7&l« &b&lSkyHand &8&l»'
    - '&fGrade de %player_name%'
  texte:
    - '&7Version de base.'
  boutons:
    base:
      libelle: '&7Continuer'
      commande: '[message] &7Bienvenue.'
  versions:
    expert:
      si:
        - '%player_level% >= 10'
        - '%player_gamemode% != creative'
      texte:
        - '&7Version pour les joueurs de niveau 10 et plus.'
```

### Champ texte

La clé `champ` ajoute un champ où le joueur tape un texte (champ de saisie des dialogues de Minecraft). Elle accepte `libelle` (le texte au-dessus du champ), `defaut` (le texte de départ, vide par défaut) et `longueur-max` (nombre de caractères au plus, 32 par défaut).

Le texte tapé arrive avec le clic du bouton. Les commandes, les messages et les conditions des boutons peuvent l'utiliser avec `{saisie}`, après un nettoyage fait côté serveur : on ne garde que les lettres (accents compris), les chiffres, les espaces, les apostrophes et les traits d'union, les espaces multiples sont réduits à un et le texte est coupé à `longueur-max`. Rien d'autre n'entre jamais dans une commande, `abc; op x` devient `abc op x` et `§c` devient `c`. Sans champ, `{saisie}` est vide. `{saisie}` ne se place jamais en début de commande.

```yaml
runes:
  titre:
    - '&7&l« &d&lSkyHand &8&l»'
    - '&fLe mot des runes'
  champ:
    libelle: '&7Votre mot'
    longueur-max: 12
  boutons:
    valider:
      libelle: '&a&lValider'
      commande:
        - '[console] say {joueur} propose {saisie}'
```

### Exigences d'un bouton

La clé `exige` d'un bouton demande des objets et des conditions avant de lancer sa commande.

| Clé | Rôle |
|---|---|
| `objets` | liste de `MATERIAL:quantité` ou `#tag_minecraft:quantité`, par exemple `HONEY_BOTTLE:3` ou `#minecraft:logs:4` ; suivie de `lore` (`RESIN_BRICK:8 lore`), la ligne accepte aussi un objet qui ne porte qu'un lore en plus |
| `si` | conditions, comme ci-dessus |
| `refus` | lignes lancées si l'exigence n'est pas remplie, avec les mêmes préfixes que `commande` (`[player]`, `[console]`, `[message]`) ; vide, le joueur reçoit un message d'erreur simple |

Au clic, si toutes les conditions sont vraies et que l'inventaire contient les objets, ils sont retirés puis les `commande` du bouton sont lancées. Sinon seules les lignes `refus` sont lancées et rien n'est retiré. Seuls comptent les objets normaux, sans nom personnalisé, sans lore, sans enchantement ni données de plugin : un objet renommé n'est ni compté ni retiré. Une ligne finie par `lore` accepte en plus un objet dont la seule différence est son lore, comme les objets de rituel donnés par un plugin avec leur explication. Le contrôle et le retrait se font dans le même tick, sur le scheduler du joueur (les mondes tournent en parallèle sur DivineMC). Plusieurs lignes qui visent la même matière ne comptent pas deux fois le même objet.

```yaml
echange:
  titre:
    - '&7&l« &d&lSkyHand &8&l»'
    - '&fLe troqueur'
  boutons:
    echanger:
      libelle: '&a&lÉchanger'
      exige:
        objets:
          - 'HONEY_BOTTLE:3'
          - '#minecraft:logs:4'
        si:
          - '%player_level% >= 10'
        refus:
          - '[message] &8&l« &c&lSH &8&l» &7Il vous manque &cquelque chose&7.'
      commande:
        - '[console] give {joueur} diamond 1'
```

Un `[console]` dans `refus` compte comme un `[console]` de `commande` : le dialogue ne s'ouvre que par la console ou le staff, une seule fois par ouverture. C'est vrai aussi d'un `[console]` placé dans une version.

Utilisé sur : **Opale** (Purpur/DivineMC 1.21.11, Java 25).

## Dépendances

- Optionnelle : `ViaVersion` (version du client, pour refuser `/shdialogs menu` aux clients trop anciens)
- Optionnelle : `PlaceholderAPI` (placeholders `%...%` dans les textes et les conditions des dialogues)

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shdialogs reload` | — | Relit `config.yml`, `messages.yml` et le dossier `dialogues`. Si l'un d'eux est invalide, rien n'est remplacé et le fichier fautif est indiqué. Signale les identifiants définis deux fois. | `shdialogs.admin.reload` |
| `/shdialogs list` | — | Liste le menu et les dialogues de `config.yml` et du dossier `dialogues` : chargés (avec leur fichier), désactivés, en doublon ou en erreur (avec la cause). | `shdialogs.admin.list` |
| `/shdialogs menu` | — | Ouvre le menu SkyHand à la demande (outil de test du staff, joueur seulement). | `shdialogs.menu` |
| `/shdialogs openlater <ticks> <dialogue> <joueur> [valeurs...]` | — | Ouvre le dialogue après 1 à 200 ticks, le temps de laisser jouer l'animation d'un PNJ ou un effet ; seule la dernière ouverture demandée pour un joueur s'affiche (deux clics rapides n'ouvrent qu'un dialogue) | `shdialogs.open.others` |
| `/shdialogs open <dialogue> [joueur] [valeurs...]` | — | Ouvre un dialogue de la section `dialogues` (à soi-même, ou à un joueur pour la console et le staff), avec ses valeurs `{1}`, `{2}`... | `shdialogs.open` (tous), `shdialogs.open.others` (op : autre joueur, valeurs, dialogue avec `[console]`) |

La console peut utiliser `reload`, `list` et `open` avec un joueur.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shdialogs.admin` | Toutes les sous-commandes d'administration. | `op` |
| `shdialogs.admin.reload` | `/shdialogs reload`. | `op` |
| `shdialogs.admin.list` | `/shdialogs list`. | `op` |
| `shdialogs.menu` | Ouvrir le menu SkyHand par commande. | `op` |
| `shdialogs.open` | Ouvrir un dialogue pour soi-même (menus DeluxeMenus). | tous |
| `shdialogs.open.others` | Ouvrir un dialogue à un autre joueur, avec des valeurs, ou un dialogue qui a un bouton `[console]`. | `op` |

Le champ `permission` d'un bouton ou d'un dialogue du `config.yml` accepte n'importe quel nœud ; vide, il est ouvert à tous.

## Configuration

Fichiers par défaut repris dans le jar ; ils sont créés au premier démarrage s'ils n'existent pas. Si `messages.yml` manque alors que `config.yml` contient encore les anciennes sections `prefixe`, `couleurs` et `messages`, il est créé à partir de ces textes. Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHDialogs/
├── <a href="src/main/resources/config.yml">config.yml</a>
│   ├── aveugler-pendant-dialogues   effet Cécité tant qu'un dialogue est affiché
│   ├── menu                     menu SkyHand (figé au démarrage, sauf les commandes)
│   │   ├── actif, ecran-echap, actions-rapides
│   │   ├── titre, titre-bouton, texte, colonnes, largeur-bouton, bouton-fermer
│   │   └── boutons              un bloc par bouton : libelle, infobulle, commande (ligne ou liste ; ou url, ou fermer), permission, actif
│   │       ├── ile, general, warps, reglement        navigation
│   │       ├── boutique, metiers, hdv                économie
│   │       ├── quetes, vote, skyhand                 progression
│   │       └── discord, site, voter, boutique-web    liens web (bouton « url »)
│   └── dialogues                dialogues à la demande (rechargés par /shdialogs reload)
│       ├── titre, texte, boutons, colonnes, largeur-bouton, bouton-fermer, permission, attendre-menu, actif
│       ├── champ                libelle, defaut, longueur-max (champ texte, {saisie})
│       ├── versions             un bloc par version : si, puis titre, texte, boutons, bouton-fermer, colonnes, largeur-bouton, champ
│       ├── boutons.*.exige      objets, si, refus (objets à retirer, conditions, lignes de refus)
│       ├── nouveau-compagnon, nouvelle-particule, nouveau-titre    cosmétiques gagnés dans une box (valeurs {1} {2}, [console])
│       └── vote, bienvenue, reglement                              exemples
├── dialogues/                   un fichier .yml par groupe de dialogues, sous-dossiers compris (même format que la section dialogues)
└── <a href="src/main/resources/messages.yml">messages.yml</a>
    ├── prefixe, couleurs        préfixe des messages et couleur par type (erreur, succès, info)
    └── erreur, succes, info, avertissement    messages du chat, par type
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
