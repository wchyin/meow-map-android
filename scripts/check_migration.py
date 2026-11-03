"""Small host-side SQLite check of the SQL emitted by Room's migration compiler.

Run after Gradle compilation: python scripts/check_migration.py
This complements (does not replace) the Android migration test.
"""
import json
import re
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "app/schemas/cn.edu.meowmap.data.MeowDatabase"
GENERATED = ROOT / "app/build/generated/ksp/debug/kotlin/cn/edu/meowmap/data"


def database(version):
    schema = json.loads((SCHEMAS / f"{version}.json").read_text(encoding="utf-8"))["database"]
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    for entity in schema["entities"]:
        name = entity["tableName"]
        db.execute(entity["createSql"].replace("${TABLE_NAME}", name))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", name))
    return db


def migrate(db, start=1, end=3):
    for version in range(start, end):
        source = (GENERATED / f"MeowDatabase_AutoMigration_{version}_{version+1}_Impl.kt").read_text(encoding="utf-8")
        statements = re.findall(r'connection\.execSQL\(("(?:[^"\\]|\\.)*")\)', source)
        if not statements:
            raise AssertionError("No generated migration SQL found; build the app first")
        for literal in statements:
            db.execute(json.loads(literal))


class MigrationTest(unittest.TestCase):
    def test_existing_records_and_interactions_survive(self):
        with database(1) as db:
            db.execute("INSERT INTO users VALUES ('u','a@example.com','同学','hash','salt')")
            db.execute("INSERT INTO records VALUES ('r','u','橘猫','描述','图书馆',23,113,1,1,0)")
            db.execute("INSERT INTO photos VALUES ('p','r','/photos/original.jpg',0)")
            db.execute("INSERT INTO likes VALUES ('r','u')")
            db.execute("INSERT INTO comments VALUES ('c','r','u','留言',1)")
            migrate(db)
            for table in ["users", "records", "photos", "likes", "comments"]:
                self.assertEqual(1, db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0])
            self.assertEqual("橘猫", db.execute("SELECT title FROM records").fetchone()[0])
            self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())
            self.assertEqual(0, db.execute("SELECT COUNT(*) FROM drafts").fetchone()[0])

    def test_migrated_schema_matches_fresh_v3(self):
        with database(1) as upgraded, database(3) as fresh:
            migrate(upgraded)
            tables = fresh.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall()
            for (table,) in tables:
                for pragma in ["table_info", "foreign_key_list", "index_list"]:
                    self.assertEqual(fresh.execute(f"PRAGMA {pragma}('{table}')").fetchall(), upgraded.execute(f"PRAGMA {pragma}('{table}')").fetchall(), f"{table}: {pragma}")

    def test_draft_slots_are_distinct_for_each_account_and_record(self):
        with database(1) as db:
            migrate(db)
            sql = "INSERT INTO drafts VALUES (?,?,'未完成','','',23,113,0,'[]',1)"
            db.execute(sql, ("alice", "new"))
            db.execute(sql, ("bob", "new"))
            db.execute(sql, ("alice", "existing-record"))
            self.assertEqual(3, db.execute("SELECT COUNT(*) FROM drafts").fetchone()[0])
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute(sql, ("alice", "new"))

    def test_v2_upgrade_keeps_drafts_and_observations_are_account_scoped(self):
        with database(2) as db:
            db.execute("INSERT INTO users VALUES ('alice','a@example.com','A','hash','salt')")
            db.execute("INSERT INTO users VALUES ('bob','b@example.com','B','hash','salt')")
            db.execute("INSERT INTO drafts VALUES ('alice','new','未完成','','',23,113,0,'[]',1)")
            migrate(db, start=2)
            db.execute("INSERT INTO observations VALUES ('n','alice',1,'图书馆',2,'小橘','白爪子',1,1)")
            self.assertEqual(1, db.execute("SELECT COUNT(*) FROM drafts").fetchone()[0])
            self.assertEqual(0, db.execute("SELECT COUNT(*) FROM observations WHERE userId='bob'").fetchone()[0])
            self.assertEqual(0, db.execute("DELETE FROM observations WHERE id='n' AND userId='bob'").rowcount)
            self.assertEqual(1, db.execute("SELECT COUNT(*) FROM observations").fetchone()[0])
            self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())


if __name__ == "__main__":
    unittest.main(verbosity=2)
