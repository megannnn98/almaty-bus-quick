#!/usr/bin/env python3
"""Заполняет локальный справочник ссылками маршрутов из каталога АЭТ.

Python 3.10+, только стандартная библиотека. Сайт не предназначен для
массового опроса: выдерживаем паузы и не повторяем неудавшиеся запросы.
"""

from __future__ import annotations

import argparse
import csv
import html
import re
import sys
import time
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
TSV_PATH = ROOT / "app/src/main/assets/routes.tsv"
SOURCE = "https://aet.kz/ru/routes/"
DIRECT_ROUTE = re.compile(r"^https://2gis\.kz/almaty/route/[0-9]+$")
ROUTE_PAGE = re.compile(r"^/ru/routes/(bus|trolleybus)-([0-9a-zа-я]+)/?$", re.I)
URL_PATTERN = re.compile(r"https?://(?:www\.)?2gis\.(?:kz|ru)/almaty/route/([0-9]+)", re.I)


class AnchorExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.hrefs: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() == "a":
            data = dict(attrs)
            if data.get("href"):
                self.hrefs.append(data["href"] or "")


def index_routes(source_html: str) -> list[tuple[str, str, str]]:
    """(kind, number, URL) из публичного каталога; без дублей."""
    parser = AnchorExtractor()
    parser.feed(source_html)
    entries: dict[tuple[str, str], str] = {}
    for href in parser.hrefs:
        url = urljoin(SOURCE, href)
        parts = urlsplit(url)
        if parts.netloc not in ("aet.kz", "www.aet.kz"):
            continue
        match = ROUTE_PAGE.fullmatch(parts.path)
        if not match:
            continue
        kind, number = match.groups()
        if not number.isdigit():
            # У маршрутов с суффиксами (например, 38а) нужна отдельная клавиша.
            continue
        entries[(kind, number)] = url
    return [(kind, number, url) for (kind, number), url in entries.items()]


def details(source_html: str) -> tuple[str, str] | None:
    """Возвращает (прямой URL 2ГИС, подпись) из страницы маршрута."""
    parser = AnchorExtractor()
    parser.feed(source_html)
    route_url = next((
        f"https://2gis.kz/almaty/route/{match.group(1)}"
        for href in parser.hrefs
        if (match := URL_PATTERN.match(html.unescape(href)))
    ), None)
    if route_url is None:
        return None

    heading = re.search(r"<h1\b[^>]*>(.*?)</h1>", source_html, flags=re.I | re.S)
    description = ""
    if heading:
        description = html.unescape(re.sub(r"<[^>]+>", "", heading.group(1)))
        description = re.sub(r"^(?:Автобус|Троллейбус)\s*№?\s*\w+:\s*", "", description).strip()
    return route_url, description.replace("\t", " ").replace("\n", " ")


def fetch(url: str) -> str:
    req = Request(url, headers={"User-Agent": "BusRouteQuick/0.1 (personal route directory)"})
    with urlopen(req, timeout=20) as response:
        return response.read().decode("utf-8", errors="replace")


def load_existing() -> dict[tuple[str, str], tuple[str, str]]:
    result: dict[tuple[str, str], tuple[str, str]] = {}
    with TSV_PATH.open(encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f, delimiter="\t"):
            if row.get("kind") not in ("bus", "trolleybus"):
                continue
            if not row.get("number", "").isdigit():
                continue
            if not DIRECT_ROUTE.fullmatch(row.get("url", "")):
                continue
            result[(row["kind"], row["number"])] = (row["url"], row["description"])
    return result


def save(entries: dict[tuple[str, str], tuple[str, str]]) -> None:
    rows = sorted(entries.items(), key=lambda pair: (pair[0][0], int(pair[0][1])))
    with TSV_PATH.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, delimiter="\t", lineterminator="\n")
        writer.writerow(("kind", "number", "url", "description"))
        for (kind, number), (url, description) in rows:
            writer.writerow((kind, number, url, description))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--limit", type=int, default=0,
                        help="Ограничить количество страниц (например, --limit 5)")
    parser.add_argument("--delay", type=float, default=0.7,
                        help="Пауза между запросами, секунд (default 0.7)")
    args = parser.parse_args()
    try:
        index = fetch(SOURCE)
    except Exception as exc:
        print(f"Не удалось загрузить {SOURCE}: {exc}", file=sys.stderr)
        return 1

    found = index_routes(index)
    if not found:
        print("Структура каталога изменилась: ссылки на маршруты не найдены.", file=sys.stderr)
        return 1
    if args.limit > 0:
        found = found[:args.limit]
    routes = load_existing()
    imported = 0
    for kind, number, url in found:
        time.sleep(max(0.3, args.delay))
        try:
            result = details(fetch(url))
        except Exception as exc:
            print(f"Пропущен {kind} {number}: {exc}", file=sys.stderr)
            continue
        if result is None:
            print(f"Нет прямой ссылки: {kind} {number}")
            continue
        route_url, description = result
        routes[(kind, number)] = (route_url, description)
        imported += 1
        print(f"{kind} {number}: {route_url}")
    if imported:
        save(routes)
    print(f"Обновлено: {imported}; всего локальных маршрутов: {len(routes)}")
    return 0 if imported else 1


if __name__ == "__main__":
    raise SystemExit(main())
