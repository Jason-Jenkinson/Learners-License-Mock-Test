"""Adds a "category" field to each question in extracted_pdf_questions.json.

Keyword rules, applied in order:
  1. Road signs              - question has an image and its wording mentions a sign/marking/signal.
  2. Controls of the vehicle - wording mentions a vehicle control (clutch, brake, gears, "Number 7", ...).
  3. Road signs / Controls   - image questions whose answer options mention signs, then controls.
  4. Rules of the road       - everything else.

The result is a first pass. Review it and fix individual questions by editing the
"category" value directly; re-running this script only fills questions that have no
category yet (pass --force to reclassify everything).

Usage:  python -I classify_questions.py [--force]
"""
import json
import re
import sys
from collections import Counter
from pathlib import Path

BANK = Path(__file__).with_name("extracted_pdf_questions.json")

RULES = "Rules of the road"
SIGNS = "Road signs"
CONTROLS = "Controls of the vehicle"

CONTROL_WORDS = re.compile(
    r"\b(clutch|accelerator|brake pedal|handbrake|hand brake|parking brake|footbrake|foot brake|"
    r"gear ?lever|gear ?stick|gears?|steering wheel|indicator (?:lever|switch)|hooter|"
    r"dipswitch|dip switch|headlight switch|throttle|kick ?start|pedal|which controls?|vehicle controls?|use control|stall(?:s|ing)?|"
    r"wipers?|windscreen|footbrake|service brake|"
    r"number \d+|nr\.? ?\d+)\b",
    re.I,
)
SIGN_WORDS = re.compile(
    r"\b(signs?|road markings?|markings?|marked|signals?|robots?|traffic lights?|symbols?|"
    r"lines? painted|painted)\b",
    re.I,
)


def classify(q):
    text = " ".join(
        str(q.get(k) or "")
        for k in ("question_text", "answer_text_a", "answer_text_b", "answer_text_c", "answer_text_d")
    )
    stem = q.get("question_text") or ""
    has_image = bool(q.get("reference_image"))
    if has_image and SIGN_WORDS.search(stem):
        return SIGNS
    if CONTROL_WORDS.search(stem):
        return CONTROLS
    if has_image and SIGN_WORDS.search(text):
        return SIGNS
    if has_image and CONTROL_WORDS.search(text):
        return CONTROLS
    return RULES


def main():
    force = "--force" in sys.argv
    questions = json.loads(BANK.read_text(encoding="utf-8"))
    for q in questions:
        if force or not q.get("category"):
            q["category"] = classify(q)
    BANK.write_text(json.dumps(questions, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(Counter(q["category"] for q in questions))


if __name__ == "__main__":
    main()
