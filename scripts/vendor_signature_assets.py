# MIT License
# Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio

#!/usr/bin/env python3
"""
Vendor osu-stats-signature "full" template + pendukungnya ke resource app
( templates/ikon SVG → app/src/main/res/raw/, font → app/src/main/res/font/ )
untuk widget profile besar.

Transformasi template:
  1. CSS classes (.cls-N) dibake menjadi atribut inline (AndroidSVG render
     tanpa dukungan CSS penuh) — fill/stroke/opacity/dst jadi atribut,
     clip-path/mask jadi atribut.
  2. Blok animasi (@keyframes) dibuang, class "animated*" dihapus.
  3. Radar skills (g id="skills") + {{no-skill-data-text}} dibuang.
  4. <image> cover/avatar dibuang (digambar via Canvas di SignatureRenderer.kt).
  5. Placeholder teks dinamis (nama, angka, bendera, ikon mode) dibuang —
     digambar via Canvas di koordinat yang sama dengan render.js asli.
  6. Placeholder warna {{hsl-*}} + {{level-bar-fg}} DIJAGA (diisi di Kotlin).
  7. Gradient ber-xlink:href di-inline (AndroidSVG tidak dukung referensi).
  8. Template dipecah jadi template_bg.svg (rects + band) dan
     template_fg.svg (semua dekorasi) — dua lapis untuk sisip cover di
     tengah z-order via Canvas.

Selain template, script ini menyalin:
  - assets/modes/*.svg  -> res/raw/mode_*.svg
  - fonts Comfortaa (Regular/Bold) -> res/font/comfortaa_*.ttf

Catatan: bendera TIDAK disalin — renderer (SignatureRenderer.kt) dan UI
memakai SATU sumber: `assets/icon/Flags/*.png`.

Jalankan dari root proyek:  python3 scripts/vendor_signature_assets.py
"""
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/assets/stat-sign"
OUT_TEMPLATES = ROOT / "app/src/main/res/raw"
OUT_MODES = ROOT / "app/src/main/res/raw"
OUT_FONTS = ROOT / "app/src/main/res/font"

# Placeholder teks dinamis yang digambar via Canvas (dibuang dari SVG).
TEXT_PLACEHOLDERS = [
    "{{name}}", "{{supporter-tag}}", "{{country}}", "{{playmode}}",
    "{{flag}}", "{{playmode-icon}}",
    "{{level}}", "{{level-percent}}",
    "{{ssh-count}}", "{{ss-count}}", "{{sh-count}}", "{{s-count}}", "{{a-count}}",
    "{{pp}}", "{{medals}}", "{{playtime}}",
    "{{global-ranking}}", "{{country-ranking}}",
    "{{ranked-score}}", "{{play-count}}", "{{total-score}}", "{{total-hits}}",
    "{{replays-watched-by-others}}",
    "{{acc}}", "{{max-combo}}", "{{bp}}", "{{first-place}}",
]

# Properti CSS yang jadi atribut SVG (AndroidSVG pasti memahaminya);
# sisanya disimpan sebagai style inline.
ATTR_PROPS = {
    "fill", "fill-opacity", "fill-rule", "opacity",
    "stroke", "stroke-width", "stroke-miterlimit", "stroke-dasharray",
    "clip-path", "mask",
}


def extract_class_rules(svg: str):
    """Kumpulkan semua aturan .cls-N{...} sesuai urutan stylesheet."""
    rules: dict[str, list[tuple[str, str]]] = {}
    order: list[str] = []
    for m in re.finditer(r"\.cls-(\d+)\s*\{([^}]*)\}", svg):
        name = "cls-" + m.group(1)
        props: list[tuple[str, str]] = []
        for pm in re.finditer(r"([a-zA-Z-]+)\s*:\s*([^;]+);?", m.group(2)):
            prop, val = pm.group(1).strip(), pm.group(2).strip()
            if prop == "outline":
                continue  # tidak relevan di Android
            props.append((prop, val))
        if name not in rules:
            rules[name] = []
            order.append(name)
        rules[name] = props
    return rules, order


def bake_classes(svg: str, rules: dict, order: list[str]) -> str:
    """Ubah class="cls-N ..." menjadi atribut/style inline."""
    tag_re = re.compile(r"<([a-zA-Z][a-zA-Z0-9]*)([^>]*?)\s+class=\"([^\"]*)\"([^>]*)>")

    def repl(m: re.Match) -> str:
        tag = m.group(1)
        attrs = m.group(2)
        classes = [c for c in m.group(3).split() if c.startswith("cls-")]
        rest = m.group(4)
        applied: dict[str, str] = {}
        for name in order:
            if name in classes:
                for prop, val in rules[name]:
                    applied[prop] = val
        attr_parts: list[str] = []
        style_parts: list[str] = []
        for prop, val in applied.items():
            if prop in ATTR_PROPS:
                attr_parts.append(f'{prop}="{val}"')
            else:
                style_parts.append(f"{prop}: {val};")
        # Hapus style (hanya animation-delay) & class asli dari atribut lama.
        attrs = re.sub(r'\s*style="[^"]*"', "", attrs)
        attrs = re.sub(r"\s*class=\"[^\"]*\"", "", attrs)
        out = f"<{tag}{attrs}"
        for a in attr_parts:
            out += " " + a
        if style_parts:
            out += f' style="{"".join(style_parts)}"'
        out += rest + ">"
        return out

    return tag_re.sub(repl, svg)


# Placeholder teks dinamis template MINI (400x120).
MINI_TEXT_PLACEHOLDERS = [
    "{{name}}", "{{flag}}", "{{country-ranking}}", "{{playmode-icon}}",
    "{{level}}", "{{global-ranking}}", "{{pp}}", "{{acc}}", "{{play-count}}",
]


def strip_dynamic_content(svg: str) -> str:
    """Buang <style>, <image>, skills, dan placeholder teks dinamis."""
    svg = re.sub(r"<style>.*?</style>", "", svg, flags=re.S)
    svg = re.sub(r"<image[^>]*>", "", svg)
    return svg


def bake_and_finalize(svg: str, rules: dict, order: list[str], width: int, height: int) -> str:
    svg = svg.replace("{{width}}", str(width)).replace("{{height}}", str(height))
    svg = svg.replace("{{fg-extra-class}}", "")
    svg = bake_classes(svg, rules, order)
    grads: dict[str, str] = {}
    for m in re.finditer(r"<linearGradient\b[^>]*\bid=\"(gradient_[0-9-]+)\"[^>]*>(.*?)</linearGradient>", svg, re.S):
        grads[m.group(1)] = m.group(2)
    for ref_id in ("gradient_2", "gradient_3"):
        if ref_id in grads:
            svg = svg.replace(f'xlink:href="#{ref_id}" />', f">{grads[ref_id]}</linearGradient>")
    svg = re.sub(r'\s*(style|class)=""', "", svg)
    svg = re.sub(r'\s*style="animation-delay:[^"]*"', "", svg)
    svg = svg.replace('class="animated"', "").replace('class="animated-fade"', "")
    return svg


def vendor_full(out: Path) -> None:
    template = (SRC / "assets/svg_template/full/template_en.svg").read_text(encoding="utf-8")
    rules, order = extract_class_rules(template)

    svg = strip_dynamic_content(template)

    # Buang radar skills + {{no-skill-data-text}}.
    skills_start = svg.index('<g id="skills">')
    no_skill_end = svg.index("{{no-skill-data-text}}") + len("{{no-skill-data-text}}")
    svg = svg[:skills_start] + svg[no_skill_end:]

    for ph in TEXT_PLACEHOLDERS:
        svg = svg.replace(ph, "")

    svg = bake_and_finalize(svg, rules, order, 550, 320)

    bg_start = svg.index('<g id="bg">')
    fg_start = svg.index('<g id="fg"')
    svg_end = svg.rindex("</svg>")
    head = svg[:bg_start]
    bg_svg = head + svg[bg_start:fg_start] + "</svg>\n"
    fg_svg = head + svg[fg_start:svg_end] + "</svg>\n"

    (out / "template_bg.svg").write_text(bg_svg, encoding="utf-8")
    (out / "template_fg.svg").write_text(fg_svg, encoding="utf-8")
    return svg, bg_svg, fg_svg


def strip_group(svg: str, group_id: str) -> str:
    """Hapus satu grup SVG (beserta isinya) dengan penutup </g> yang cocok."""
    start = svg.index(f'<g id="{group_id}">')
    i = start + len(f'<g id="{group_id}">')
    depth = 0
    while i < len(svg):
        open_g = svg.find("<g", i)
        close_g = svg.find("</g>", i)
        if close_g == -1:
            raise ValueError(f"grup '{group_id}' tidak tertutup")
        if open_g != -1 and open_g < close_g:
            depth += 1
            i = open_g + 2
        else:
            if depth == 0:
                return svg[:start] + svg[close_g + 4:]
            depth -= 1
            i = close_g + 4
    raise ValueError(f"grup '{group_id}' tidak tertutup")


def vendor_full_skills(out: Path) -> None:
    """
    Template FULL mode "with skills" — sama seperti template_fg (header +
    level + ranking + acc/combo/bp/fp + playtime/medals/pp), TAPI:
      - grup `skills` (7 ikon/nama skill) DIPERTAHANKAN,
      - grup `count` (5 kolom statistik) DIBUANG (digantikan radar),
      - {{skills-plot}} dibuang (radar digambar via Canvas di Kotlin).
    Layar referensi: osu-stats-signature full dengan ?skills=true.
    """
    template = (SRC / "assets/svg_template/full/template_en.svg").read_text(encoding="utf-8")
    rules, order = extract_class_rules(template)

    svg = strip_dynamic_content(template)

    # Radar & fallback "No skills data" digambar via Canvas → buang placeholder.
    svg = svg.replace("{{skills-plot}}", "").replace("{{no-skill-data-text}}", "")
    # Kolom statistik tidak ada di view skills.
    svg = strip_group(svg, "count")
    # Beri id pada dua ikon alternatif (reaction/memory) — Kotlin memilih
    # yang ditampilkan (default: reaction, memory disembunyikan).
    svg = svg.replace('class="cls-46 skill-reaction"', 'id="skill-reaction"')
    svg = svg.replace('class="cls-46 skill-memory"', 'id="skill-memory"')

    for ph in TEXT_PLACEHOLDERS:
        svg = svg.replace(ph, "")

    svg = bake_and_finalize(svg, rules, order, 550, 320)

    bg_start = svg.index('<g id="bg">')
    fg_start = svg.index('<g id="fg"')
    svg_end = svg.rindex("</svg>")
    head = svg[:bg_start]
    fg_svg = head + svg[fg_start:svg_end] + "</svg>\n"

    (out / "template_skills_fg.svg").write_text(fg_svg, encoding="utf-8")


def vendor_mini(out: Path) -> None:
    """
    Template MINI (400x120): hanya 1 file SVG (fg — dekorasi + label + divider).
    Basis kartu (rect), cover, dan dua overlay gelap digambar via Canvas karena
    harus berurutan di antara rect dan fg (mirip cover di template full).
    """
    template = (SRC / "assets/svg_template/mini/template_en.svg").read_text(encoding="utf-8")
    rules, order = extract_class_rules(template)

    svg = strip_dynamic_content(template)
    for ph in MINI_TEXT_PLACEHOLDERS:
        svg = svg.replace(ph, "")
    svg = bake_and_finalize(svg, rules, order, 400, 120)

    fg_start = svg.index('<g id="fg"')
    svg_end = svg.rindex("</svg>")
    bg_start = svg.index('<g id="bg">')
    head = svg[:bg_start]
    fg_svg = head + svg[fg_start:svg_end] + "</svg>\n"

    (out / "template_mini_fg.svg").write_text(fg_svg, encoding="utf-8")


def main() -> None:
    OUT_TEMPLATES.mkdir(parents=True, exist_ok=True)
    OUT_MODES.mkdir(parents=True, exist_ok=True)
    OUT_FONTS.mkdir(parents=True, exist_ok=True)

    _, bg_svg, fg_svg = vendor_full(OUT_TEMPLATES)
    vendor_full_skills(OUT_TEMPLATES)
    vendor_mini(OUT_TEMPLATES)

    # Salin modes -> res/raw/mode_*.svg dan font -> res/font/comfortaa_*.ttf.
    for name, out_name in (
        ("std", "mode_std"), ("catch", "mode_catch"),
        ("taiko", "mode_taiko"), ("mania", "mode_mania"),
    ):
        shutil.copy2(SRC / "assets/modes" / f"{name}.svg", OUT_MODES / f"{out_name}.svg")
    shutil.copy2(SRC / "assets/fonts/Comfortaa/Comfortaa-Regular.ttf", OUT_FONTS / "comfortaa_regular.ttf")
    shutil.copy2(SRC / "assets/fonts/Comfortaa/Comfortaa-Bold.ttf", OUT_FONTS / "comfortaa_bold.ttf")

    # Sanity check: tidak boleh ada placeholder teks tersisa.
    mini_fg = (OUT_TEMPLATES / "template_mini_fg.svg").read_text(encoding="utf-8")
    skills_fg = (OUT_TEMPLATES / "template_skills_fg.svg").read_text(encoding="utf-8")
    leftovers = [ph for ph in TEXT_PLACEHOLDERS if ph in bg_svg + fg_svg]
    leftovers += [ph for ph in MINI_TEXT_PLACEHOLDERS if ph in mini_fg]
    leftovers += [ph for ph in TEXT_PLACEHOLDERS if ph in skills_fg]
    if leftovers:
        raise SystemExit(f"ERROR: placeholder tersisa: {leftovers}")

    print(f"OK — full: template_bg.svg ({len(bg_svg)}B), template_fg.svg ({len(fg_svg)}B)")
    print(f"OK — skills: template_skills_fg.svg ({len(skills_fg)}B)")
    print(f"OK — mini: template_mini_fg.svg ({len(mini_fg)}B)")
    print(f"OK — modes: 4 (res/raw/mode_*.svg) | fonts: 2 (res/font/comfortaa_*.ttf)")


if __name__ == "__main__":
    main()
