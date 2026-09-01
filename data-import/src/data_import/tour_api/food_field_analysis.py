"""Aggregate TourAPI restaurant free-text fields without assigning attributes.

This module deliberately records source wording and structural signals only.  It
does not normalize a value into an AttributeKey vocabulary; that is P2-E work.
"""

from __future__ import annotations

import json
import re
from collections import Counter
from pathlib import Path
from typing import Any

from data_import.tour_api.collector import TourApiError
from data_import.tour_api.detail_collector import parse_detail_item

FIELDS = ("parkingfood", "opentimefood", "restdatefood")
TIME_PATTERN = re.compile(r"\b(?:[01]?\d|2[0-3]):[0-5]\d\b")
HTML_PATTERN = re.compile(r"<[^>]+>")
WEEKDAY_PATTERN = re.compile(r"[월화수목금토일](?:요일)?")
KOREAN_UNIT_PATTERN = re.compile(r"\d+(?:[,.]\d+)?\s*(대|원|분|시간|일|층|석|명|%|㎡|평|km|m)\b")


def _empty_field_report() -> dict[str, Any]:
    return {
        "field_present": 0,
        "nonblank": 0,
        "blank": 0,
        "missing_key": 0,
        "distinct_trimmed_values": 0,
        "top_values": [],
        "format_signals": {
            "contains_html": 0,
            "contains_time": 0,
            "contains_weekday": 0,
            "contains_korean_unit": 0,
        },
        "observed_korean_units": [],
    }


def analyze_detail_directory(input_dir: Path, *, top_n: int = 30) -> dict[str, Any]:
    """Return a reproducible aggregate for detailIntro2 JSON files in ``input_dir``.

    One file is one detail response.  Source-empty responses, parse failures and
    missing field keys stay separate so P2-E does not mistake them for blank
    source values.  Values are trimmed only; no semantic transformation occurs.
    """
    if top_n < 1:
        raise ValueError("top_n must be at least 1")

    fields = {name: _empty_field_report() for name in FIELDS}
    value_counts = {name: Counter() for name in FIELDS}
    unit_counts = {name: Counter() for name in FIELDS}
    file_count = source_empty = parse_failed = answered = 0

    for path in sorted(input_dir.glob("*.json")):
        file_count += 1
        try:
            item = parse_detail_item(path.read_bytes())
        except (OSError, json.JSONDecodeError, KeyError, TourApiError):
            parse_failed += 1
            continue
        if item is None:
            source_empty += 1
            continue

        answered += 1
        for name in FIELDS:
            field = fields[name]
            if name not in item:
                field["missing_key"] += 1
                continue
            field["field_present"] += 1
            value = str(item[name]).strip()
            if not value:
                field["blank"] += 1
                continue

            field["nonblank"] += 1
            value_counts[name][value] += 1
            signals = field["format_signals"]
            if HTML_PATTERN.search(value):
                signals["contains_html"] += 1
            if TIME_PATTERN.search(value):
                signals["contains_time"] += 1
            if WEEKDAY_PATTERN.search(value):
                signals["contains_weekday"] += 1
            units = KOREAN_UNIT_PATTERN.findall(value)
            if units:
                signals["contains_korean_unit"] += 1
                unit_counts[name].update(units)

    for name in FIELDS:
        field = fields[name]
        field["distinct_trimmed_values"] = len(value_counts[name])
        field["top_values"] = [
            {"value": value, "count": count}
            for value, count in value_counts[name].most_common(top_n)
        ]
        field["observed_korean_units"] = [
            {"unit": unit, "count": count}
            for unit, count in unit_counts[name].most_common()
        ]

    return {
        "analysis": "TourAPI restaurant(39) detailIntro2 source-value distribution; no P2-E conversion rules applied",
        "input_directory": str(input_dir),
        "input_files": file_count,
        "detail_responses": {
            "answered": answered,
            "source_empty": source_empty,
            "parse_failed": parse_failed,
        },
        "fields": fields,
        "notes": [
            "missing_key means the detail item had no such field; blank means the key existed but its trimmed value was empty.",
            "top_values retain source text after edge whitespace trimming only; they are evidence, not conversion categories.",
            "format signals may overlap and do not imply a parsing or attribute rule.",
        ],
    }
