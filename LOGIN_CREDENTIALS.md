# Identifiants de connexion — 2M Market

## Il n'y a plus de compte par défaut

Les versions précédentes livraient `admin` / `admin123` et `employe` / `admin123`,
codés en dur dans le script d'initialisation et publiés dans ce fichier. C'était
un défaut de sécurité : toute personne ayant accès au dépôt connaissait les
identifiants de toutes les installations.

Ce n'est plus le cas. **Aucun mot de passe n'est livré avec l'application.**

## Premier démarrage

Sur une base vide, l'application demande de créer le compte administrateur :
nom d'utilisateur au choix, mot de passe de 8 caractères minimum, saisi deux fois.
C'est le seul point d'entrée initial.

## Mot de passe oublié

Les mots de passe sont hachés avec BCrypt : ils ne peuvent pas être relus,
seulement remplacés. Utilisez l'outil console fourni :

```bash
java -cp target/2M-market-1.0-SNAPSHOT.jar util.ResetPassword
```

Il liste les comptes existants, demande lequel modifier, puis le nouveau mot de
passe (masqué à la saisie lorsque le terminal le permet). Le mot de passe est
haché puis écrit directement en base : il n'est ni affiché ni enregistré sur disque.

> Ne supprimez pas la base pour « repartir de zéro » comme le conseillait la
> version précédente de ce document : vous perdriez ventes, stock et historique.

## Créer des comptes employés

Depuis l'application, connecté en tant qu'Admin : **Gestion des utilisateurs**.

Créez **un compte nominatif par employé**. C'est ce qui rend la *Recette du jour*
exploitable : chaque vente, ajout de stock et note est rattaché à l'employé
connecté. Avec un compte partagé, tout est attribué à la même personne et le
suivi par employé perd son sens.

## Rôles

| Rôle | Accès |
|---|---|
| `Admin` | Toutes les fonctionnalités : utilisateurs, stock, ventes, statistiques, suppression forcée de produits |
| `Employé` | Caisse, consultation et ajout de stock, recette du jour, notes |

## Bonnes pratiques

- Un compte par personne, jamais de compte partagé.
- Supprimer le compte d'un employé qui quitte le magasin.
- Le mot de passe de la **base de données** se trouve dans
  `%APPDATA%\2M-Market\database.properties`, protégé par ACL et exclu de Git.
  Il est distinct des mots de passe applicatifs décrits ici.
