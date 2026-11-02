"""Draws the charts of the README and of the benchmark pages.

Run from the repository root with Python 3.9 or newer; it needs no package:

    python docs/benchmarks/charts/charts.py

Every figure comes from data.json in this directory, which names the page each figure was taken from.
Each chart is written twice, for a light and for a dark page.
"""
import json
import pathlib
from xml.sax.saxutils import escape

HERE = pathlib.Path(__file__).parent
FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI','Noto Sans',Helvetica,Arial,sans-serif"
WIDTH = 820
THEMES = {
    "light": {"text": "#1f2328", "muted": "#59636e", "grid": "#d1d9e0", "kscope": "#8250df",
              "kscope2": "#c297ff", "strong": "#6e7781", "weak": "#afb8c1"},
    "dark": {"text": "#f0f6fc", "muted": "#9198a1", "grid": "#3d444d", "kscope": "#ab7df8",
             "kscope2": "#6e40c9", "strong": "#9198a1", "weak": "#59636e"},
}
METHODS = {
    "kscope": ("KrosScope", "kscope"),
    "lexical": ("KrosScope, lexical ranking only", "kscope2"),
    "bm25en": ("BM25, English stop words removed", "strong"),
    "bm25raw": ("BM25, raw text", "weak"),
}


class Svg:
    def __init__(self, height, theme, label):
        self.height = height
        self.theme = theme
        self.label = label
        self.parts = []

    def text(self, x, y, value, size=12, fill="text", anchor="start", weight=400):
        self.parts.append(
            f'<text x="{x:.1f}" y="{y:.1f}" font-size="{size}" fill="{self.theme[fill]}" '
            f'text-anchor="{anchor}" font-weight="{weight}">{escape(str(value))}</text>'
        )

    def rect(self, x, y, width, height, fill, radius=2):
        self.parts.append(
            f'<rect x="{x:.1f}" y="{y:.1f}" width="{width:.1f}" height="{max(height, 0):.1f}" rx="{radius}" '
            f'fill="{self.theme[fill]}"/>'
        )

    def outline(self, x, y, width, height):
        self.parts.append(
            f'<rect x="{x:.1f}" y="{y:.1f}" width="{width:.1f}" height="{height:.1f}" rx="6" fill="none" '
            f'stroke="{self.theme["grid"]}" stroke-width="1.5" stroke-dasharray="5 4"/>'
        )

    def line(self, x1, y1, x2, y2, stroke="grid"):
        self.parts.append(
            f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="{self.theme[stroke]}" '
            f'stroke-width="1"/>'
        )

    def legend(self, entries, y=16):
        x = 0
        for label, fill in entries:
            self.rect(x, y - 10, 12, 12, fill)
            self.text(x + 18, y, label, fill="muted")
            x += 18 + 6.6 * len(label) + 24

    def save(self, name, theme_name):
        body = "\n".join(self.parts)
        text = (
            f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {WIDTH} {self.height}" width="{WIDTH}" '
            f'height="{self.height}" font-family="{FONT}" role="img" aria-label="{escape(self.label)}">\n'
            f"<title>{escape(self.label)}</title>\n{body}\n</svg>\n"
        )
        with (HERE / f"{name}.{theme_name}.svg").open("w", encoding="utf-8", newline="\n") as file:
            file.write(text)


def percent(value):
    return f"{value * 100:.1f}"


def bars(svg, box, groups, series, top, bar=24, ticks=4, axis=True, label=percent, size=10.5):
    """Draws grouped vertical bars. A group is (lines of its label, values by series, or None when unmeasured)."""
    x, y, width, height = box
    if axis:
        for step in range(ticks + 1):
            level = y + height - height * step / ticks
            svg.line(x, level, x + width, level)
            svg.text(x - 8, level + 4, f"{top * 100 * step / ticks:.0f}%", size=11, fill="muted", anchor="end")
    else:
        svg.line(x, y + height, x + width, y + height)
    slot = width / len(groups)
    for index, (lines, values) in enumerate(groups):
        centre = x + slot * index + slot / 2
        for row, line in enumerate(lines):
            svg.text(centre, y + height + 18 + 15 * row, line, size=11.5, fill="text" if row == 0 else "muted",
                     anchor="middle", weight=600 if row == 0 and len(lines) > 1 else 400)
        if values is None:
            svg.outline(centre - slot / 2 + 14, y + 12, slot - 28, height - 20)
            svg.text(centre, y + height / 2 + 6, "not measured yet", size=11.5, fill="muted", anchor="middle")
            continue
        left = centre - (bar + 2) * len(series) / 2 + 1
        for position, (key, fill) in enumerate(series):
            value = values[key]
            tall = height * value / top
            bar_x = left + position * (bar + 2)
            svg.rect(bar_x, y + height - tall, bar, tall, fill)
            shown = label(value) if callable(label) else label[key][index]
            svg.text(bar_x + bar / 2, y + height - tall - 5, shown, size=size, anchor="middle",
                     weight=600 if fill == "kscope" else 400, fill="text" if fill == "kscope" else "muted")


def method_series(keys):
    return [(key, METHODS[key][1]) for key in keys]


def method_legend(keys):
    return [METHODS[key] for key in keys]


def budget_groups(data, metric):
    return [([f"{budget:,}"], {key: values[index] for key, values in data[metric].items()})
            for index, budget in enumerate(data["budgets"])]


def context_chart(data, theme, tasks):
    keys = ["kscope", "bm25en", "bm25raw"]
    svg = Svg(318, theme, f"Context packages against BM25 at four token budgets, {tasks}")
    svg.legend(method_legend(keys))
    panels = [(44, "Changed files that are in the package", "recall", data.get("recallTop", 0.8)),
              (474, "Share of the package that is their source", "precision", data.get("precisionTop", 0.2))]
    for x, title, metric, top in panels:
        svg.text(x, 50, title, size=13, weight=600)
        bars(svg, (x, 70, 346, 190), budget_groups(data, metric), method_series(keys), top)
        svg.text(x + 173, 302, "token budget of the package", size=11.5, fill="muted", anchor="middle")
    return svg


def localization_chart(data, theme, tasks):
    keys = ["kscope", "lexical", "bm25en", "bm25raw"]
    svg = Svg(290, theme, f"File localization against BM25, {tasks}")
    svg.legend(method_legend(keys))
    groups = [([title], data[metric]) for metric, title in
              (("recall5", "in the first 5 results"), ("recall10", "in the first 10 results"),
               ("ndcg10", "ranking quality (NDCG@10)"))]
    svg.text(44, 50, "Changed files found by the ranking", size=13, weight=600)
    bars(svg, (44, 70, 766, 180), groups, method_series(keys), data.get("top", 0.6), bar=40,
         ticks=data.get("ticks", 3), size=11.5)
    return svg


def levels_chart(data, theme):
    keys = ["kscope", "bm25en", "bm25raw"]
    svg = Svg(470, theme, "Results by the level of the task: how far the change reaches")
    svg.legend(method_legend(keys))
    rows = [(50, 68, "recall10", "Changed files found in the first ten results", False),
            (238, 256, "recall4000", "Changed files that are in a 4,000-token package", True)]
    for title_y, y, metric, title, labelled in rows:
        groups = []
        for level in data["levels"]:
            lines = [level["name"], level["reach"], level["tasks"]] if labelled else []
            groups.append((lines, level.get(metric)))
        svg.text(44, title_y, title, size=13, weight=600)
        bars(svg, (44, y, 766, 140), groups, method_series(keys), 0.8, bar=32, size=11.5)
    return svg


def speed_chart(data, theme):
    svg = Svg(292, theme, "Time of a scan, of an update and of two queries on two workspaces")
    svg.legend([("before API digests · as a one-off command", "weak"),
                ("now · through kscope serve", "kscope")])
    for column, panel in enumerate(data["workspaces"]):
        x = 0 if column == 0 else 430
        svg.text(x, 52, panel["name"], size=13, weight=600)
        scale = 300 / panel["top"]
        for row, operation in enumerate(panel["operations"]):
            y = 78 + row * 54
            svg.text(x, y, operation["name"], size=12)
            pairs = [(operation.get("before"), "weak"), (operation["now"], "kscope")]
            offset = 8
            for value, fill in pairs:
                if value is None:
                    continue
                svg.rect(x, y + offset, max(value * scale, 2), 11, fill)
                svg.text(x + value * scale + 7, y + offset + 10, f"{value:g} s", size=11.5,
                         weight=600 if fill == "kscope" else 400, fill="text" if fill == "kscope" else "muted")
                offset += 14
    return svg


def agent_chart(data, theme):
    arms = [("a", "weak"), ("b", "strong"), ("c", "kscope")]
    svg = Svg(236, theme, "Coding-agent pilot: 18 runs for each of three ways to brief the agent")
    svg.legend([("Task statement only", "weak"), ("Statement and BM25 context", "strong"),
                ("Statement and KrosScope context", "kscope")])
    slot = WIDTH / len(data["measures"])
    for index, measure in enumerate(data["measures"]):
        x = slot * index
        svg.text(x + slot / 2, 52, measure["name"], size=13, weight=600, anchor="middle")
        values = dict(zip("abc", measure["values"]))
        labels = {key: [shown] for key, shown in zip("abc", measure["shown"])}
        bars(svg, (x + 14, 86, slot - 28, 118), [([measure["note"]], values)], arms, max(measure["values"]) * 1.02,
             bar=36, axis=False, label=labels, size=12)
    return svg


def main():
    data = json.loads((HERE / "data.json").read_text(encoding="utf-8"))
    for theme_name, theme in THEMES.items():
        context_chart(data["context"], theme, data["context"]["tasks"]).save("context-budget", theme_name)
        localization_chart(data["localization"], theme, data["localization"]["tasks"]).save("localization", theme_name)
        levels_chart(data["levels"], theme).save("levels", theme_name)
        speed_chart(data["speed"], theme).save("speed", theme_name)
        agent_chart(data["agent"], theme).save("agent-pilot", theme_name)
        if "self" in data:
            own = data["self"]
            context_chart(own["context"], theme, own["tasks"]).save("self-context-budget", theme_name)
            localization_chart(own["localization"], theme, own["tasks"]).save("self-localization", theme_name)


main()
