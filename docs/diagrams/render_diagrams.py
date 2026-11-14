"""Render local PlantUML sources and assemble a vector PDF. No hosted service or DB."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess

HERE = Path(__file__).resolve().parent
PLANTUML_SHA256 = "71972b58fe9208a78bbed614c806042b8404ca5f24022581d986bd9a1cc9463e"
DIAGRAMS = (
    ("classes", "01 / Classes du domaine", "5 classes, 34 attributs, 6 associations et 1 enumeration."),
    ("use-cases", "02 / Cas d'utilisation", "Deux acteurs : visiteur public et administrateur authentifie."),
    ("relations", "03 / Modele relationnel MySQL", "5 tables InnoDB, 5 PK, 6 FK et 2 contraintes UNIQUE hors PK."),
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plantuml-jar", type=Path, required=True)
    args = parser.parse_args()
    jar = args.plantuml_jar.resolve(strict=True)
    if hashlib.sha256(jar.read_bytes()).hexdigest() != PLANTUML_SHA256:
        parser.error("Expected the documented PlantUML 1.2026.10 binary (SHA-256 mismatch)")
    java = shutil.which("java")
    if not java:
        parser.error("Java must be available on PATH")
    exports = HERE / "exports"
    exports.mkdir(exist_ok=True)
    for filetype in ("svg", "png"):
        subprocess.run([java, "-Djava.awt.headless=true", "-jar", str(jar),
                        "-charset", "UTF-8", "-nometadata", f"-t{filetype}", "-o", "exports",
                        *(f"{name}.puml" for name, *_ in DIAGRAMS)], cwd=HERE, check=True,
                       capture_output=True)

    from reportlab.graphics import renderPDF
    from reportlab.lib.colors import HexColor
    from reportlab.lib.pagesizes import A3
    from reportlab.pdfgen.canvas import Canvas
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from svglib.svglib import svg2rlg

    font_directory = Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts"
    font_files = {"Arial": "arial.ttf", "Arial-Bold": "arialbd.ttf",
                  "Arial-Italic": "ariali.ttf", "Arial-BoldItalic": "arialbi.ttf"}
    if all((font_directory / file).is_file() for file in font_files.values()):
        for name, file in font_files.items():
            pdfmetrics.registerFont(TTFont(name, str(font_directory / file)))
        pdfmetrics.registerFontFamily("Arial", normal="Arial", bold="Arial-Bold",
                                      italic="Arial-Italic", boldItalic="Arial-BoldItalic")

    output = HERE / "output/pdf/refuge-final-diagrams.pdf"
    output.parent.mkdir(parents=True, exist_ok=True)
    canvas = Canvas(str(output), pagesize=A3, invariant=1, pageCompression=1)
    canvas.setTitle("Gestion d'un refuge animalier - Diagrammes finaux")
    canvas.setAuthor("Projet universitaire - Gestion d'un refuge animalier")
    canvas.setSubject("UML et modele relationnel verifies sur les sources du 14 novembre 2026")
    width, height = A3
    margin = 34
    for page, (name, title, subtitle) in enumerate(DIAGRAMS, 1):
        canvas.setFillColor(HexColor("#173B4D"))
        canvas.rect(0, height - 78, width, 78, fill=1, stroke=0)
        canvas.setFillColor(HexColor("#FFFFFF"))
        canvas.setFont("Helvetica-Bold", 21)
        canvas.drawString(margin, height - 34, title)
        canvas.setFont("Helvetica", 11)
        canvas.drawString(margin, height - 55, subtitle)
        drawing = svg2rlg(str(exports / f"{name}.svg"))
        if drawing is None:
            raise RuntimeError(f"SVG conversion failed: {name}")
        available_width, available_height = width - 2 * margin, height - 150
        factor = min(available_width / drawing.width, available_height / drawing.height)
        draw_width, draw_height = drawing.width * factor, drawing.height * factor
        canvas.saveState()
        canvas.translate((width - draw_width) / 2, 61 + (available_height - draw_height) / 2)
        canvas.scale(factor, factor)
        renderPDF.draw(drawing, canvas, 0, 0)
        canvas.restoreState()
        canvas.setStrokeColor(HexColor("#CBD8DF"))
        canvas.line(margin, 45, width - margin, 45)
        canvas.setFillColor(HexColor("#526575"))
        canvas.setFont("Helvetica", 9)
        canvas.drawString(margin, 29, "14 novembre 2026 | Sources : code Java, React et database/database.sql | Aucun acces MySQL")
        canvas.drawRightString(width - margin, 29, f"{page} / 3")
        canvas.showPage()
    canvas.save()
    print("Rendered 3 SVG, 3 PNG and the 3-page A3 vector PDF atlas.")


if __name__ == "__main__":
    main()
