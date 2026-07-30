#!/usr/bin/env python3
"""
Exporte les donnees de MarketDB.db (SQLite) vers un fichier .sql PostgreSQL.

Interet par rapport a migrate_sqlite_to_postgres.py :
  * aucune dependance Python cote cible (pas de psycopg2)
  * le fichier peut etre relu et verifie avant execution
  * s'applique directement a Railway :  psql "$DATABASE_URL" -f data.sql

Conversions appliquees :
  * dates  : entiers epoch-millis -> litteraux TIMESTAMP
  * montants : REAL (flottant)    -> litteraux NUMERIC exacts

Usage :
    python export_sqlite_to_sql.py --sqlite MarketDB.db --out data.sql
"""

import argparse
import os
import sqlite3
import sys
from datetime import datetime, timezone
from decimal import Decimal

# Ordre d'insertion : respecte les dependances de cles etrangeres.
TABLES = [
    ("utilisateurs", ["id", "username", "password_hash", "role", "date_creation"]),
    ("categories", ["id", "nom", "description", "date_creation"]),
    ("fournisseurs", ["id", "nom", "contact", "telephone", "email", "adresse", "date_creation"]),
    ("produits", ["id", "code_barre", "nom", "categorie", "category_id", "prix_achat_actuel",
                  "prix_vente_defaut", "quantite_stock", "unite", "seuil_alerte", "date_derniere_maj"]),
    ("credits_fournisseur", ["id", "fournisseur_id", "montant", "date_creation", "date_maj"]),
    ("ventes", ["id", "date_vente", "total_vente", "id_utilisateur", "type_paiement", "date_creation"]),
    ("detailsvente", ["id", "id_vente", "id_produit", "quantite",
                      "prix_vente_unitaire", "prix_achat_unitaire"]),
    ("ajouts_stock", ["id", "produit_id", "employe_id", "fournisseur_id", "quantite",
                      "montant_paiement", "credit_utilise", "notes", "date_ajout"]),
    ("paiements_fournisseur", ["id", "fournisseur_id", "employe_id", "montant", "notes", "date_paiement"]),
    ("deplacements_employe", ["id", "employe_id", "date_debut", "date_fin",
                              "destination", "notes", "heures_travaillees"]),
    ("notes_jour", ["id", "employe_id", "type_note", "montant", "description", "date_note"]),
]

# Colonnes a traiter comme des dates / des montants.
DATE_COLS = {"date_creation", "date_derniere_maj", "date_maj", "date_vente", "date_ajout",
             "date_paiement", "date_debut", "date_fin", "date_note"}
MONEY_COLS = {"prix_achat_actuel", "prix_vente_defaut", "prix_vente_unitaire",
              "prix_achat_unitaire", "total_vente", "montant", "montant_paiement",
              "credit_utilise", "heures_travaillees"}
DEFAULT_NOW = {"date_creation", "date_derniere_maj", "date_maj"}


def to_timestamp(value):
    """
    Reconstitue l'heure murale d'origine.

    L'application ecrivait Timestamp.valueOf(LocalDateTime.now()), c'est-a-dire
    l'heure locale, que le pilote SQLite enregistrait en millisecondes epoch.
    La colonne PostgreSQL cible est un TIMESTAMP sans fuseau, relu par
    getTimestamp().toLocalDateTime() : il faut donc y replacer l'heure LOCALE,
    et non l'heure UTC. Convertir en UTC decalerait toutes les dates et ferait
    basculer les ventes de debut de nuit sur la veille dans la recette du jour.
    """
    if value is None:
        return None
    if isinstance(value, (int, float)):
        v = float(value)
        if v > 1e11:            # millisecondes
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


def quote(value, column):
    """Rend un litteral SQL PostgreSQL pour la valeur donnee."""
    if column in DATE_COLS:
        ts = to_timestamp(value)
        if ts is None:
            return "CURRENT_TIMESTAMP" if column in DEFAULT_NOW else "NULL"
        return "TIMESTAMP '" + ts.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3] + "'"

    if value is None:
        return "NULL"

    if column in MONEY_COLS:
        return str(Decimal(str(value)).quantize(Decimal("0.001")))

    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, float)):
        return str(value)

    # Texte : doubler les apostrophes.
    return "'" + str(value).replace("'", "''") + "'"


def table_columns(cur, table):
    cur.execute(f"PRAGMA table_info({table})")
    return {row[1] for row in cur.fetchall()}


def has_table(cur, table):
    cur.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table,))
    return cur.fetchone() is not None


def export(sqlite_path, out_path):
    if not os.path.exists(sqlite_path):
        sys.exit(f"Base SQLite introuvable : {sqlite_path}")

    con = sqlite3.connect(sqlite_path)
    cur = con.cursor()

    lines = [
        "-- Donnees 2M Market exportees depuis SQLite",
        f"-- Source  : {os.path.abspath(sqlite_path)}",
        f"-- Genere  : {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        "--",
        "-- Prerequis : le schema doit deja exister (schema_postgres.sql).",
        "-- IMPORTANT : importer AVANT le premier lancement de l'application, sinon",
        "--             le compte administrateur cree au premier demarrage entre en",
        "--             conflit avec les comptes importes (contrainte UNIQUE username).",
        "-- Execution : psql \"$DATABASE_URL\" -f " + os.path.basename(out_path),
        "",
        "BEGIN;",
        "",
    ]

    total = 0
    summary = []

    for table, columns in TABLES:
        if not has_table(cur, table):
            continue

        available = table_columns(cur, table)
        cols = [c for c in columns if c in available]
        if not cols:
            continue

        cur.execute(f"SELECT {', '.join(cols)} FROM {table} ORDER BY id")
        rows = cur.fetchall()
        if not rows:
            continue

        lines.append(f"-- {table} ({len(rows)} ligne(s))")
        for row in rows:
            # Ignorer les lignes que les contraintes CHECK rejetteraient.
            record = dict(zip(cols, row))
            if table == "detailsvente" and (record.get("quantite") or 0) <= 0:
                continue
            if table == "produits" and (record.get("quantite_stock") or 0) < 0:
                record["quantite_stock"] = 0
                row = tuple(record[c] for c in cols)

            values = ", ".join(quote(v, c) for c, v in zip(cols, row))
            lines.append(
                f"INSERT INTO {table} ({', '.join(cols)}) VALUES ({values}) "
                f"ON CONFLICT DO NOTHING;")
        lines.append("")
        total += len(rows)
        summary.append((table, len(rows)))

    lines.append("-- Resynchronisation des sequences SERIAL")
    for table, _ in TABLES:
        if has_table(cur, table):
            lines.append(
                f"SELECT setval(pg_get_serial_sequence('{table}', 'id'), "
                f"COALESCE((SELECT MAX(id) FROM {table}), 1), "
                f"(SELECT MAX(id) IS NOT NULL FROM {table}));")
    lines += ["", "COMMIT;", ""]

    with open(out_path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))

    con.close()

    print(f"\nExport ecrit : {os.path.abspath(out_path)}")
    print(f"{total} ligne(s) au total :")
    for table, n in summary:
        print(f"  {table:24} {n:>5}")
    print("\nPour charger dans PostgreSQL :")
    print(f'  psql "$DATABASE_URL" -f {os.path.basename(out_path)}')


def main():
    ap = argparse.ArgumentParser(description="Exporte MarketDB.db vers un fichier .sql PostgreSQL")
    ap.add_argument("--sqlite", default="MarketDB.db")
    ap.add_argument("--out", default="data_postgres.sql")
    args = ap.parse_args()
    export(args.sqlite, args.out)


if __name__ == "__main__":
    main()
