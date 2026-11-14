"""Offline documentation checks. Reads only named project sources; never opens a DB."""
from pathlib import Path
import re
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
JAVA = ROOT / "Backend/src/main/java/com/enicarthage/refugeanimalier"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def normalize(value):
    return re.sub(r"\s+", "", value).upper()


def verify():
    classes = (HERE / "classes.puml").read_text(encoding="utf-8")
    er = (HERE / "relations.puml").read_text(encoding="utf-8")
    use_cases = (HERE / "use-cases.puml").read_text(encoding="utf-8")
    sql = (ROOT / "database/database.sql").read_text(encoding="utf-8")
    models = {}
    for name, body in re.findall(r"class (\w+)\s*\{([^}]+)\}", classes):
        fields = dict(re.findall(r"-\s+(\w+)\s*:\s*(\w+)", body))
        actual = (JAVA / "model" / f"{name}.java").read_text(encoding="utf-8")
        expected = {field: kind for kind, field in
                    re.findall(r"private\s+(\w+)\s+(\w+)\s*;", actual)}
        require(fields == expected, f"Java attributes differ: {name}")
        models[name] = fields
    expected_models = {p.stem for p in (JAVA / "model").glob("*.java")
                       if "public class " in p.read_text(encoding="utf-8")}
    require(set(models) == expected_models, "Domain class set differs")
    require(sum(map(len, models.values())) == 34, "Unexpected attribute count")
    enum_java = (JAVA / "model/StatutDemande.java").read_text(encoding="utf-8")
    values = re.findall(r"\b(?:EN_ATTENTE|ACCEPTEE|REFUSEE)\b", enum_java)
    enum_body = re.search(r"enum StatutDemande[^\{]*\{([^}]+)\}", classes)[1]
    require(re.findall(r"\b[A-Z_]+\b", enum_body) == values, "Enum values differ")
    print("PASS: 5 Java classes, 34 attributes/types and 3 enum values")

    tables = {}
    foreign_keys = set()
    unique_columns = set()
    for table, body in re.findall(r"CREATE TABLE (\w+)\s*\((.*?)\)\s*ENGINE=InnoDB", sql, re.S):
        columns = {}
        declarations = body.split("CONSTRAINT", 1)[0].strip().rstrip(",")
        for declaration in re.split(r",\s*(?![^()]*\))", declarations):
            declaration = " ".join(declaration.split())
            match = re.fullmatch(r"(\w+)\s+(BIGINT|INT|TEXT|DATE|VARCHAR\(\d+\)|ENUM\([^)]*\))(.*)", declaration)
            require(match is not None, f"Cannot parse SQL column: {table}")
            name, kind, flags = match.groups()
            pk = "PRIMARY KEY" in flags
            uq = "UNIQUE" in flags
            if uq:
                unique_columns.add((table, name))
            columns[name] = (normalize(kind), "NOT NULL" in flags or pk,
                             pk, "AUTO_INCREMENT" in flags, uq)
        tables[table] = columns
        for field, parent, target, cascade in re.findall(
                r"FOREIGN KEY\s*\((\w+)\)\s*REFERENCES\s+(\w+)\((\w+)\)(\s+ON DELETE CASCADE)?", body):
            foreign_keys.add((table, field, parent, target, bool(cascade)))
    require(len(tables) == 5, "Expected 5 explicit InnoDB tables")
    diagram_tables = {}
    for table, body in re.findall(r'entity "\w+" as (\w+)\s*\{([^}]+)\}', er):
        columns = {}
        for line in body.splitlines():
            match = re.fullmatch(r"\s*(\*?)\s*(\w+)\s*:\s*(.+?)(?:\s+<<([^>]+)>>)?\s*", line)
            if not match:
                continue
            mandatory, name, kind, tags = match.groups()
            tags = set(re.findall(r"\w+", tags or ""))
            columns[name] = (normalize(kind), bool(mandatory),
                             "PK" in tags, "AI" in tags, "UQ" in tags)
            expected_fk = any(t == table and f == name for t, f, *_ in foreign_keys)
            require(("FK" in tags) == expected_fk, f"ER FK marker differs: {table}.{name}")
        diagram_tables[table] = columns
    require(diagram_tables == tables, "ER columns/types/nullability/keys differ from SQL")
    require(len(foreign_keys) == 6 and len(unique_columns) == 2, "Key counts differ")
    for child, field, parent, target, cascade in foreign_keys:
        cardinality = "o|" if (child, field) in unique_columns else "o{"
        require(f"{parent} ||--{cardinality} {child} : {field}" in er,
                f"Missing/wrong ER cardinality: {parent} -> {child}")
        parent_class = "Admin" if parent == "admin" else "Animal"
        child_class = {"animal": "Animal", "annonce": "Annonce", "demande": "Demande",
                       "dossier_medical": "DossierMedical"}[child]
        domain_cardinality = "1" if child == "dossier_medical" else "0..*"
        require(f'{parent_class} "1" -- "{domain_cardinality}" {child_class}' in classes,
                f"Missing/wrong domain association: {parent_class} -> {child_class}")
        require(target == "id" and tables[child][field][1], "Expected required FK to id")
        require(cascade == (child == "dossier_medical"), "Unexpected cascade")
    require(normalize("DEFAULT 'EN_ATTENTE'") in normalize(sql), "Request default differs")
    require("DEFAULT 'EN_ATTENTE'" in er and "ON DELETE CASCADE" in er, "Missing ER notes")
    require(sql.count("utf8mb4_unicode_ci") == 5, "Collation count differs")
    print("PASS: 5 SQL tables, 34 columns/types/nullability, 5 PK, 6 FK, 2 UQ, defaults/cascade")
    print("PASS: 6 domain/ER relationships; Java 1:1 versus SQL 1:0..1 medical distinction")

    # Evidence existence is checked here; behavior was reviewed manually and tested previously.
    evidence = {
        "BrowseAnimals": "HomePage", "AnimalDetails": "AnimalDetailPage",
        "BrowseAnnouncements": "AnnouncementsPage", "SearchAnnouncements": "AnnouncementsPage",
        "FilterAnnouncements": "AnnouncementsPage", "SubmitRequest": "AdoptionRequestPage",
        "Login": "AdminLoginPage", "Logout": "../layouts/ProtectedAdminArea",
        "Dashboard": "AdminDashboardPage", "ManageAnimals": "AdminAnimalsPage",
        "ManageMedical": "AdminMedicalPage", "PublishAnnouncement": "AdminAnnouncementEditorPage",
        "ManageRequests": "AdminRequestsPage", "AcceptRequest": "AdminRequestDetailPage",
        "RefuseRequest": "AdminRequestDetailPage",
    }
    aliases = set(re.findall(r'usecase "[^"]+" as (\w+)', use_cases))
    require(aliases == set(evidence), "Use-case set differs from evidence mapping")
    for alias, page in evidence.items():
        require((ROOT / "frontend/src/pages" / f"{page}.jsx").is_file(),
                f"Missing UI evidence: {alias}")
    require(set(re.findall(r'actor "[^"]+" as (\w+)', use_cases)) == {"Visitor", "Administrator"},
            "Expected exactly two actors")
    app = (ROOT / "frontend/src/App.jsx").read_text(encoding="utf-8")
    for route in ['path="admin"', 'path="admin/connexion"', 'path="animaux/:id"',
                  'path="animaux/:id/demande-adoption"', 'path="annonces"',
                  'path="animaux/:id/dossier-medical"', 'path="demandes/:id"']:
        require(route in app, "Missing documented route")
    for name in ("Animal", "Annonce", "Demande", "Auth", "Dashboard"):
        require((JAVA / f"controller/{name}Controller.java").is_file(), "Missing HTTP evidence")
    print("PASS: 2 actors, 15 use cases, UI source and route/controller evidence present")
    for name in ("classes", "relations", "use-cases"):
        for extension in ("svg", "png"):
            require((HERE / f"exports/{name}.{extension}").is_file(), "Missing diagram export")
    require((HERE / "output/pdf/refuge-final-diagrams.pdf").is_file(), "Missing PDF atlas")
    print("PASS: 3 SVG + 3 PNG exports and PDF atlas present")
    print("OFFLINE ONLY: no application tests, MySQL access or live behavior executed")


if __name__ == "__main__":
    try:
        verify()
    except (ValueError, OSError, AttributeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
