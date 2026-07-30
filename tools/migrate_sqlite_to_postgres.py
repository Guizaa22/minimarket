#!/usr/bin/env python3
"""
Migration des donnees SQLite (MarketDB.db) vers PostgreSQL.

Conversions appliquees :
  * dates : entiers epoch-millis  -> TIMESTAMP reel
  * montants : REAL (flottant)    -> NUMERIC (decimal exact)
  * sequences SERIAL resynchronisees apres import

Le script est idempotent : relancer ne cree pas de doublons (ON CONFLICT DO NOTHING).

Usage :
    python migrate_sqlite_to_postgres.py --sqlite MarketDB.db \
        --pg "postgresql://market_app:MOTDEPASSE@localhost:5432/market2m"

    # ou via la variable d'environnement DATABASE_URL
    python migrate_sqlite_to_postgres.py --sqlite MarketDB.db
"""

import argparse
import os
import sqlite3
import sys
from datetime import datetime, timezone
from decimal import Decimal

try:
    import psycopg2
    import psycopg2.extras
except ImportError:
    sys.exit("psycopg2 manquant.  Installez-le avec :  pip install psycopg2-binary")


# --------------------------------------------------------------------------
# Conversions
# --------------------------------------------------------------------------

def to_timestamp(value):
    """
    Convertit une date SQLite (millis, secondes ou texte) en datetime naif,
    en restituant l'heure MURALE d'origine.

    L'application ecrivait Timestamp.valueOf(LocalDateTime.now()), soit l'heure
    locale, stockee en millisecondes epoch par le pilote SQLite. La colonne
    PostgreSQL cible etant un TIMESTAMP sans fuseau relu par toLocalDateTime(),
    c'est bien l'heure locale qu'il faut y replacer : passer par UTC decalerait
    toutes les dates et ferait basculer les ventes de debut de nuit sur la veille.
    """
    if value is None:
        return None

    if isinstance(value, (int, float)):
        v = float(value)
        # > 1e11 => millisecondes ; sinon secondes
        if v > 1e11:
            v /= 1000.0
        return datetime.fromtimestamp(v)      # fuseau local, volontairement

    text = str(value).strip()
    if not text:
        return None
    for fmt in ("%Y-%m-%d %H:%M:%S.%f", "%Y-%m-%d %H:%M:%S",
                "%Y-%m-%dT%H:%M:%S.%f", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(text, fmt)
        except ValueError:
            continue
    if text.isdigit():
        return to_timestamp(int(text))
    raise ValueError(f"Date non reconnue : {value!r}")


def to_numeric(value, default="0"):
    """Convertit un REAL SQLite en Decimal a 3 decimales."""
    if value is None:
        return Decimal(default)
    return Decimal(str(value)).quantize(Decimal("0.001"))


def sqlite_rows(cur, table):
    try:
        cur.execute(f"SELECT * FROM {table}")
    except sqlite3.OperationalError:
        return []
    cols = [d[0] for d in cur.description]
    return [dict(zip(cols, row)) for row in cur.fetchall()]


def has_table(cur, table):
    cur.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table,))
    return cur.fetchone() is not None


# --------------------------------------------------------------------------
# Migration
# --------------------------------------------------------------------------

def migrate(sqlite_path, pg_dsn, dry_run=False):
    if not os.path.exists(sqlite_path):
        sys.exit(f"Base SQLite introuvable : {sqlite_path}")

    src = sqlite3.connect(sqlite_path)
    scur = src.cursor()

    dst = psycopg2.connect(pg_dsn)
    dcur = dst.cursor()

    stats = {}

    def report(name, n):
        stats[name] = n
        print(f"  {name:24} {n:>5} ligne(s)")

    print(f"\nSource  : {sqlite_path}")
    print(f"Cible   : {pg_dsn.split('@')[-1] if '@' in pg_dsn else pg_dsn}")
    print("\nMigration en cours...")

    # ---- utilisateurs -----------------------------------------------------
    rows = sqlite_rows(scur, "utilisateurs")
    for r in rows:
        dcur.execute(
            """INSERT INTO utilisateurs (id, username, password_hash, role, date_creation)
               VALUES (%s, %s, %s, %s, COALESCE(%s, CURRENT_TIMESTAMP))
               ON CONFLICT (id) DO NOTHING""",
            (r["id"], r["username"], r["password_hash"],
             r.get("role") or "Employé", to_timestamp(r.get("date_creation"))))
    report("utilisateurs", len(rows))

    # ---- categories -------------------------------------------------------
    rows = sqlite_rows(scur, "categories")
    for r in rows:
        dcur.execute(
            """INSERT INTO categories (id, nom, description, date_creation)
               VALUES (%s, %s, %s, COALESCE(%s, CURRENT_TIMESTAMP))
               ON CONFLICT (id) DO NOTHING""",
            (r["id"], r["nom"], r.get("description"),
             to_timestamp(r.get("date_creation"))))
    report("categories", len(rows))

    # ---- fournisseurs -----------------------------------------------------
    rows = sqlite_rows(scur, "fournisseurs")
    for r in rows:
        dcur.execute(
            """INSERT INTO fournisseurs (id, nom, contact, telephone, email, adresse, date_creation)
               VALUES (%s, %s, %s, %s, %s, %s, COALESCE(%s, CURRENT_TIMESTAMP))
               ON CONFLICT (id) DO NOTHING""",
            (r["id"], r["nom"], r.get("contact"), r.get("telephone"),
             r.get("email"), r.get("adresse"), to_timestamp(r.get("date_creation"))))
    report("fournisseurs", len(rows))

    # ---- produits ---------------------------------------------------------
    rows = sqlite_rows(scur, "produits")
    for r in rows:
        dcur.execute(
            """INSERT INTO produits (id, code_barre, nom, categorie, category_id,
                                     prix_achat_actuel, prix_vente_defaut,
                                     quantite_stock, unite, seuil_alerte, date_derniere_maj)
               VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, COALESCE(%s, CURRENT_TIMESTAMP))
               ON CONFLICT (id) DO NOTHING""",
            (r["id"], r["code_barre"], r["nom"], r.get("categorie"), r.get("category_id"),
             to_numeric(r.get("prix_achat_actuel")), to_numeric(r.get("prix_vente_defaut")),
             max(0, int(r.get("quantite_stock") or 0)),
             r.get("unite") or "unité", int(r.get("seuil_alerte") or 10),
             to_timestamp(r.get("date_derniere_maj"))))
    report("produits", len(rows))

    # ---- credits_fournisseur ---------------------------------------------
    rows = sqlite_rows(scur, "credits_fournisseur")
    for r in rows:
        dcur.execute(
            """INSERT INTO credits_fournisseur (id, fournisseur_id, montant, date_creation, date_maj)
               VALUES (%s, %s, %s, COALESCE(%s, CURRENT_TIMESTAMP), COALESCE(%s, CURRENT_TIMESTAMP))
               ON CONFLICT (id) DO NOTHING""",
            (r["id"], r["fournisseur_id"], to_numeric(r.get("montant")),
             to_timestamp(r.get("date_creation")), to_timestamp(r.get("date_maj"))))
    report("credits_fournisseur", len(rows))

    # ---- ventes -----------------------------------------------------------
    rows = sqlite_rows(scur, "ventes")
    for r in rows:
        dcur.execute(
            """INSERT INTO ventes (id, date_vente, total_vente, id_utilisateur,
                                   type_paiement, date_creation)
               VALUES (%s, %s, %s, %s, %s, COALESCE(%s, CURRENT_TIMESTAMP))
               ON CONFLICT (id) DO NOTHING""",
            (r["id"], to_timestamp(r["date_vente"]), to_numeric(r.get("total_vente")),
             r["id_utilisateur"], r.get("type_paiement") or "Espèces",
             to_timestamp(r.get("date_creation"))))
    report("ventes", len(rows))

    # ---- detailsvente -----------------------------------------------------
    rows = sqlite_rows(scur, "detailsvente")
    skipped = 0
    for r in rows:
        if int(r.get("quantite") or 0) <= 0:
            skipped += 1
            continue
        dcur.execute(
            """INSERT INTO detailsvente (id, id_vente, id_produit, quantite,
                                         prix_vente_unitaire, prix_achat_unitaire)
               VALUES (%s, %s, %s, %s, %s, %s)
               ON CONFLICT (id) DO NOTHING""",
            (r["id"], r["id_vente"], r["id_produit"], int(r["quantite"]),
             to_numeric(r.get("prix_vente_unitaire")),
             to_numeric(r.get("prix_achat_unitaire"))))
    report("detailsvente", len(rows) - skipped)
    if skipped:
        print(f"    ({skipped} ligne(s) ignoree(s) : quantite <= 0)")

    # ---- ajouts_stock -----------------------------------------------------
    if has_table(scur, "ajouts_stock"):
        rows = sqlite_rows(scur, "ajouts_stock")
        for r in rows:
            dcur.execute(
                """INSERT INTO ajouts_stock (id, produit_id, employe_id, fournisseur_id,
                                             quantite, montant_paiement, credit_utilise,
                                             notes, date_ajout)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                   ON CONFLICT (id) DO NOTHING""",
                (r["id"], r["produit_id"], r["employe_id"], r.get("fournisseur_id"),
                 int(r["quantite"]), to_numeric(r.get("montant_paiement")),
                 to_numeric(r.get("credit_utilise")), r.get("notes"),
                 to_timestamp(r["date_ajout"])))
        report("ajouts_stock", len(rows))

    # ---- paiements_fournisseur -------------------------------------------
    if has_table(scur, "paiements_fournisseur"):
        rows = sqlite_rows(scur, "paiements_fournisseur")
        for r in rows:
            dcur.execute(
                """INSERT INTO paiements_fournisseur (id, fournisseur_id, employe_id,
                                                      montant, notes, date_paiement)
                   VALUES (%s, %s, %s, %s, %s, %s)
                   ON CONFLICT (id) DO NOTHING""",
                (r["id"], r["fournisseur_id"], r["employe_id"],
                 to_numeric(r.get("montant")), r.get("notes"),
                 to_timestamp(r["date_paiement"])))
        report("paiements_fournisseur", len(rows))

    # ---- deplacements_employe --------------------------------------------
    if has_table(scur, "deplacements_employe"):
        rows = sqlite_rows(scur, "deplacements_employe")
        for r in rows:
            dcur.execute(
                """INSERT INTO deplacements_employe (id, employe_id, date_debut, date_fin,
                                                     destination, notes, heures_travaillees)
                   VALUES (%s, %s, %s, %s, %s, %s, %s)
                   ON CONFLICT (id) DO NOTHING""",
                (r["id"], r["employe_id"], to_timestamp(r["date_debut"]),
                 to_timestamp(r.get("date_fin")), r.get("destination"), r.get("notes"),
                 to_numeric(r.get("heures_travaillees"))))
        report("deplacements_employe", len(rows))

    # ---- notes_jour -------------------------------------------------------
    if has_table(scur, "notes_jour"):
        rows = sqlite_rows(scur, "notes_jour")
        for r in rows:
            dcur.execute(
                """INSERT INTO notes_jour (id, employe_id, type_note, montant,
                                           description, date_note)
                   VALUES (%s, %s, %s, %s, %s, %s)
                   ON CONFLICT (id) DO NOTHING""",
                (r["id"], r["employe_id"], r.get("type_note") or "Autre",
                 to_numeric(r.get("montant")), r.get("description") or "",
                 to_timestamp(r["date_note"])))
        report("notes_jour", len(rows))

    # ---- resynchroniser les sequences SERIAL ------------------------------
    print("\nResynchronisation des sequences...")
    for table in ("utilisateurs", "categories", "produits", "ventes", "detailsvente",
                  "fournisseurs", "credits_fournisseur", "paiements_fournisseur",
                  "ajouts_stock", "deplacements_employe", "notes_jour"):
        dcur.execute(
            f"""SELECT setval(pg_get_serial_sequence('{table}', 'id'),
                              COALESCE((SELECT MAX(id) FROM {table}), 1),
                              (SELECT MAX(id) IS NOT NULL FROM {table}))""")
    print("  fait.")

    if dry_run:
        dst.rollback()
        print("\n[--dry-run] Aucune ecriture conservee.")
    else:
        dst.commit()
        print("\nMigration terminee et validee.")

    src.close()
    dst.close()
    return stats


def main():
    ap = argparse.ArgumentParser(description="Migre MarketDB.db (SQLite) vers PostgreSQL")
    ap.add_argument("--sqlite", default="MarketDB.db", help="chemin du fichier SQLite")
    ap.add_argument("--pg", default=os.environ.get("DATABASE_URL"),
                    help="URL PostgreSQL (defaut : $DATABASE_URL)")
    ap.add_argument("--dry-run", action="store_true",
                    help="simule la migration sans rien ecrire")
    args = ap.parse_args()

    if not args.pg:
        sys.exit("Indiquez --pg ou definissez DATABASE_URL.")

    migrate(args.sqlite, args.pg, args.dry_run)


if __name__ == "__main__":
    main()
