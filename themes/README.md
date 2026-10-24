# Community themes

Cadence loads the themes in `index.json` into **More → Themes & layout → Community themes**.

## Add your own

1. In Cadence, set up a theme and tap **Share theme**. You get a small block of JSON.
2. Add that object to the `themes` array in `index.json`, fill in `author` and a short `description`.
3. Open a pull request.

## Fields

| Field          | Values                                      |
|----------------|---------------------------------------------|
| `name`         | Theme name                                  |
| `author`       | Your name or handle                         |
| `backdrop`     | `AMOLED` (true black) or `DARK` (dark grey) |
| `accentSource` | `FIXED`, `ARTWORK` (album art) or `WALLPAPER` (Android 12+) |
| `accent`       | `#AARRGGBB` colour, used when `accentSource` is `FIXED` |
| `description`  | One short line                              |
