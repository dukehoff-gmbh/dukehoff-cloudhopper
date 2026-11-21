#!/usr/bin/env python3
"""Generates the logo variants in brand/logo from the master drawing brand/logo/hopper.svg.

    python3 brand/generate-logos.py

Variants:
  hopper-product.svg       calmer green for cloud-hopper.com and the app
  *-rim.svg                with a thick white rim around the whole figure, for dark
                           backgrounds up to 128 px: legs, antennae and pupil are drawn in
                           the navy outline colour and would disappear on dark ground otherwise
  *-rim-thin.svg           with a thin rim, for dark backgrounds above 128 px
  favicon*.svg             the thick rim, switched on by prefers-color-scheme: dark

The documentation site's dark logo and favicon are copied to docs/static/img.

The rim is plain geometry (each shape stroked in white underneath the drawing), not an SVG
filter, so it renders the same in browsers, design tools and image converters.
"""
import copy
import math
import re
import xml.etree.ElementTree as ET
from pathlib import Path

SVG = "http://www.w3.org/2000/svg"
ET.register_namespace("", SVG)
ET.register_namespace("xlink", "http://www.w3.org/1999/xlink")
ET.register_namespace("serif", "http://www.serif.com/")

HERE = Path(__file__).parent / "logo"
SIZE = 512

FRAMEWORK = {"body": "rgb(13,220,44)", "shade": "rgb(6,182,52)"}
PRODUCT = {"body": "#5cc66b", "shade": "#3c9f4e"}
RIM = "#ffffff"


def scale_of(transform):
    m = re.match(r"matrix\(([^)]*)\)", transform or "")
    if not m:
        return 1.0
    a, b, c, d, _, _ = (float(x) for x in m.group(1).split(","))
    return math.sqrt(abs(a * d - b * c))


def recolor(text, colors):
    return (text.replace("fill:rgb(13,220,44)", "fill:" + colors["body"])
                .replace("fill:rgb(6,182,52)", "fill:" + colors["shade"]))


def rim_group(root, width):
    """A copy of the drawing with every shape filled and stroked in the rim colour."""
    group = ET.Element(f"{{{SVG}}}g", {"class": "rim"})

    def walk(src, dst, scale):
        for child in src:
            clone = ET.SubElement(dst, child.tag, {k: v for k, v in child.attrib.items() if k != "style"})
            s = scale * scale_of(child.get("transform"))
            if child.tag.endswith("path"):
                clone.set("style", f"fill:{RIM};stroke:{RIM};stroke-width:{2 * width / s:.2f};"
                                   "stroke-linejoin:round;stroke-linecap:round")
            walk(child, clone, s)

    walk(root, group, 1.0)
    return group


def write(name, colors, rim=0, favicon=False):
    root = ET.fromstring(recolor((HERE / "hopper.svg").read_text(), colors))
    root.set("width", "100%")
    root.set("height", "100%")
    if rim:
        root.set("viewBox", f"{-rim} {-rim} {SIZE + 2 * rim} {SIZE + 2 * rim}")
        group = rim_group(root, rim)
        if favicon:
            style = ET.Element(f"{{{SVG}}}style")
            style.text = ".rim{display:none}@media (prefers-color-scheme: dark){.rim{display:inline}}"
            root.insert(0, style)
            root.insert(1, group)
        else:
            root.insert(0, group)
    ET.ElementTree(root).write(HERE / name, encoding="unicode", xml_declaration=False)
    print("wrote", HERE / name)


THICK, THIN = 32, 8

write("hopper-rim.svg", FRAMEWORK, rim=THICK)
write("hopper-rim-thin.svg", FRAMEWORK, rim=THIN)
write("hopper-product.svg", PRODUCT)
write("hopper-product-rim.svg", PRODUCT, rim=THICK)
write("hopper-product-rim-thin.svg", PRODUCT, rim=THIN)
write("favicon.svg", FRAMEWORK, rim=THICK, favicon=True)
write("favicon-product.svg", PRODUCT, rim=THICK, favicon=True)

DOCS = Path(__file__).parent.parent / "docs" / "static" / "img"
for source, target in [("hopper-rim.svg", "logo-dark.svg"), ("favicon.svg", "favicon.svg")]:
    (DOCS / target).write_text((HERE / source).read_text())
    print("wrote", DOCS / target)
