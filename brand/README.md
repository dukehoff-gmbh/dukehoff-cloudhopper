# Brand assets

Logo files and design tokens shared by the documentation site and other Cloudhopper web pages.

## Logo

The mascot drawing in `logo/hopper.svg` is the master. All other files are generated from it:

```sh
python3 brand/generate-logos.py
```

| File | Use |
|---|---|
| `logo/hopper.svg` | documentation, light backgrounds |
| `logo/hopper-rim.svg` | documentation, dark backgrounds up to 128 px (navbar) |
| `logo/hopper-rim-thin.svg` | documentation, dark backgrounds above 128 px |
| `logo/hopper-product.svg` | product pages, light backgrounds |
| `logo/hopper-product-rim.svg` | product pages, dark backgrounds up to 128 px |
| `logo/hopper-product-rim-thin.svg` | product pages, dark backgrounds above 128 px |
| `logo/favicon.svg`, `logo/favicon-product.svg` | favicons; rim only in dark mode |

The generator also copies the documentation's dark logo and favicon to `docs/static/img`.

Legs, antennae and pupil are drawn in the navy outline colour only, so on dark backgrounds the
figure needs a white rim. Lightening the outline does not work: the pupil would vanish on the
white eye. The thick rim closes the gaps between the legs and stays visible down to 16 px; at
large sizes it looks heavy, so the `*-rim-thin` files have a thin one. The favicons switch the rim on by
`prefers-color-scheme: dark`, so one file serves light and dark browser tabs.

Rules:

- Keep the navy outline. Change only the body colours (bright green for the documentation,
  calm green for product pages).
- On dark backgrounds always use a rim variant.
- Leave at least a quarter of the logo's width as free space around it.

## Tokens

`tokens.css` defines primitives (`--ch-navy`, `--ch-green` ...) and semantic tokens
(`--ch-bg`, `--ch-text`, `--ch-primary` ...) for light and dark. Dark applies with
`prefers-color-scheme: dark` unless the root element has `data-theme="light"`, and always with
`data-theme="dark"`.

Fonts: IBM Plex Sans for text, JetBrains Mono for code and figures.

All text colour pairs meet WCAG AA (4.5:1); the values are listed at the top of `tokens.css`.
