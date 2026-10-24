# Copies the 3D apartment model from an "Apartment" page (the three.js HTML file) into
# web/apartment.js, replacing what's between the MODEL START / MODEL END markers. Everything
# outside the markers (weather, sky backdrop, camera, performance tuning) is kept.
#
# The page's own controls go: orbit controls, its colour-theme background, and its render
# settings, which are swapped for ones that suit a TV.
#
# Usage: python3 web/import_model.py <Apartment.html>   then   web/build.sh
import re
import sys
from pathlib import Path

START = "// ==== MODEL START: generated from the apartment page by web/import_model.py ===="
END = "// ==== MODEL END ===="


def extract(html):
    script = html[html.index('<script type="module">') + len('<script type="module">'):]
    script = script[:script.index("</script>")]
    begin = script.index('import * as THREE from "three";')
    end = script.index("/* ---------- views ---------- */")
    return script[begin:end].rstrip() + "\n"


def must_sub(pattern, repl, text, flags=0):
    new, n = re.subn(pattern, repl, text, flags=flags)
    if n == 0:
        sys.exit("import_model: couldn't find " + pattern)
    return new


def adapt(model):
    model = must_sub(r'import \{ OrbitControls \}[^\n]*\n', "", model)
    model = model.replace('"three/addons/', '"three/examples/jsm/')
    model = must_sub(r'(import \{ RoomEnvironment \}[^\n]*\n)',
                     r'\1import { mergeGeometries } from "three/examples/jsm/utils/BufferGeometryUtils.js";\n', model)
    model = must_sub(r'const renderer = new THREE\.WebGLRenderer\(\{[^}]*\}\);\nrenderer\.setPixelRatio\([^\n]*\);',
                     "const renderer = new THREE.WebGLRenderer({ antialias: true });\n"
                     "// Render resolution, as a width in pixels. It starts a little under 1080p and is adjusted\n"
                     "// while running so the TV keeps up with every screen refresh (see \"smooth motion\").\n"
                     "const MIN_WIDTH = 960, MAX_WIDTH = 1920;\n"
                     "let renderWidth = 1600;\n"
                     "renderer.setPixelRatio(renderWidth / innerWidth);", model)
    model = must_sub(r'THREE\.PCFSoftShadowMap;',
                     "THREE.PCFShadowMap;   // the original's PCFSoft costs far more per pixel", model)
    # The page's light/dark background follows its colour theme; the sky backdrop replaces it.
    model = must_sub(r'function syncBg\(\) \{.*?attributeFilter: \["data-theme"\] \}\);\n',
                     "scene.background = null;   // the sky backdrop (below) fills the background\n",
                     model, flags=re.S)
    model = must_sub(r'const controls = new OrbitControls\([^\n]*\n', "", model)
    model = re.sub(r'controls\.[^\n]*\n', "", model)
    return model


def main():
    html = Path(sys.argv[1]).read_text(encoding="utf-8")
    target = Path(__file__).with_name("apartment.js")
    js = target.read_text(encoding="utf-8")
    before = js[:js.index(START) + len(START)]
    after = js[js.index(END):]
    target.write_text(before + "\n" + adapt(extract(html)) + after, encoding="utf-8")
    print("Imported the model from", sys.argv[1], "into", target)


if __name__ == "__main__":
    main()
