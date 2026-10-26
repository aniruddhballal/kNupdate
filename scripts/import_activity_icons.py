"""Import the fixed activity icon set as Android vectors; no runtime dependency.

Run from the repository root with Python 3. SVGs and the complete upstream notice
are fetched from the pinned Lucide revision. Natural icon curves are retained;
square corners belong to the surrounding UI. Run uses an original running figure.
"""
from concurrent.futures import ThreadPoolExecutor
from hashlib import sha256
from pathlib import Path
import re
from urllib.request import urlopen
from xml.etree import ElementTree as ET
from xml.sax.saxutils import quoteattr

REVISION = "6657faa96ae10efe1509203c62b60d913006ab82"
BASE = f"https://raw.githubusercontent.com/lucide-icons/lucide/{REVISION}"
ICONS = {
    "location": "map-pin", "waking": "sunrise", "sleeping": "bed-double",
    "resting": "armchair", "eating": "utensils", "drinking": "cup-soda",
    "cooking": "cooking-pot", "showering": "shower-head", "grooming": "brush",
    "getting-ready": "shirt", "working": "briefcase-business", "meeting": "users-round",
    "video-call": "video", "phone-call": "phone", "email": "mail",
    "planning": "calendar-days", "focus": "focus", "presenting": "projector",
    "interview": "contact-round", "work-break": "coffee", "studying": "graduation-cap",
    "reading": "book-open", "class": "school", "lecture": "speech",
    "homework": "notebook-pen", "revision": "list-checks", "exam": "file-check-corner",
    "research": "microscope", "practice": "repeat-2", "course": "laptop-minimal",
    "exercising": "activity", "run": "custom-running", "walk": "footprints", "cycle": "bike",
    "gym": "dumbbell", "yoga": "person-standing", "stretch": "stretch-horizontal",
    "swim": "waves-ladder", "hike": "mountain", "sport": "volleyball",
    "travelling": "luggage", "commute": "route", "drive": "car-front",
    "ride": "car-taxi-front", "bus": "bus-front", "train": "train-front",
    "flight": "plane", "arrived": "map-pin-check", "departed": "map-pin-minus",
    "waiting": "clock-3", "music": "headphones", "watching": "clapperboard",
    "gaming": "gamepad-2", "creating": "palette", "writing": "pencil-line",
    "photography": "camera", "gardening": "sprout", "exploring": "compass",
    "event": "ticket", "relaxing": "sofa", "meeting-people": "handshake",
    "family": "house-heart", "friends": "users", "date": "heart", "visiting": "door-open",
    "chatting": "messages-square", "celebrating": "party-popper", "volunteering": "hand-heart",
    "pet-time": "paw-print", "community": "network", "cleaning": "brush-cleaning",
    "laundry": "washing-machine", "dishes": "utensils-crossed", "tidying": "archive",
    "maintenance": "wrench", "moving-home": "package-open", "childcare": "baby",
    "pet-care": "dog", "meal-prep": "salad", "home-admin": "clipboard-list",
    "shopping": "shopping-bag", "groceries": "shopping-basket", "collecting": "package-check",
    "delivery": "truck", "appointment": "calendar-check", "bank": "landmark",
    "post-office": "mailbox", "fuel-charge": "fuel", "queue": "list-ordered", "haircut": "scissors",
    "meditating": "flower-2", "breathing": "wind", "journaling": "notebook-tabs",
    "praying": "sparkles", "health-visit": "stethoscope", "therapy": "brain",
    "self-care": "bath", "recovering": "battery-charging", "medicine": "pill", "check-in": "circle-check",
}
GROUP_ICONS = {
    "everyday": "location", "work": "working", "learning": "studying", "movement": "run",
    "travel": "travelling", "leisure": "music", "social": "meeting-people", "home": "tidying",
    "errands": "shopping", "wellbeing": "meditating",
}


def fetch(path):
    with urlopen(f"{BASE}/{path}", timeout=30) as response:
        return response.read().decode("utf-8")


def path_data(element):
    tag = element.tag.rsplit("}", 1)[-1]
    a = element.attrib
    if tag == "path":
        return a["d"]
    if tag in ("circle", "ellipse"):
        x, y = float(a["cx"]), float(a["cy"])
        rx, ry = (float(a["r"]),) * 2 if tag == "circle" else (float(a["rx"]), float(a["ry"]))
        return f"M{x-rx:g},{y:g}a{rx:g},{ry:g} 0,1 0,{2*rx:g} 0a{rx:g},{ry:g} 0,1 0,{-2*rx:g} 0"
    if tag == "rect":
        x, y = float(a.get("x", 0)), float(a.get("y", 0))
        w, h = float(a["width"]), float(a["height"])
        rx = min(float(a.get("rx", a.get("ry", 0))), w / 2)
        ry = min(float(a.get("ry", a.get("rx", 0))), h / 2)
        if rx == 0 or ry == 0:
            return f"M{x:g},{y:g}h{w:g}v{h:g}h{-w:g}Z"
        return f"M{x+rx:g},{y:g}H{x+w-rx:g}Q{x+w:g},{y:g} {x+w:g},{y+ry:g}V{y+h-ry:g}Q{x+w:g},{y+h:g} {x+w-rx:g},{y+h:g}H{x+rx:g}Q{x:g},{y+h:g} {x:g},{y+h-ry:g}V{y+ry:g}Q{x:g},{y:g} {x+rx:g},{y:g}Z"
    if tag == "line":
        return f'M{a["x1"]},{a["y1"]}L{a["x2"]},{a["y2"]}'
    if tag in ("polyline", "polygon"):
        points = re.findall(r"[-+]?(?:\d*\.\d+|\d+)(?:[eE][-+]?\d+)?", a["points"])
        return "M" + "L".join(",".join(points[i:i+2]) for i in range(0, len(points), 2)) + ("Z" if tag == "polygon" else "")
    raise ValueError(f"Unsupported SVG element: {tag}")


def convert(entry):
    activity, name = entry
    if name == "custom-running":
        paths = ["M17,4A2,2 0,1 1,13 4A2,2 0,1 1,17 4M4,11L8,7L13,9L17,13H21M13,9L10,15L15,18V22M10,15L7,19H2"]
    else:
        svg = ET.fromstring(fetch(f"icons/{name}.svg"))
        paths = [path_data(child) for child in svg]
    vector = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'
    for data in paths:
        vector += '    <path android:fillColor="@android:color/transparent" android:strokeColor="#000000" android:strokeWidth="1.7" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData=' + quoteattr(data) + ' />\n'
    return activity, vector + '</vector>\n'


def main():
    root = Path(__file__).resolve().parents[1]
    catalog_path = root / "app/src/main/java/com/knupdate/app/ActivityCatalog.kt"
    catalog = catalog_path.read_text(encoding="utf-8")
    assert set(re.findall(r'activity\("([^"]+)"', catalog)) == set(ICONS)
    assert len(ICONS) == len(set(ICONS.values())) == 100
    # Complete all downloads/validation before modifying any source files.
    with ThreadPoolExecutor(max_workers=8) as pool:
        vectors = dict(pool.map(convert, ICONS.items()))
    assert len({sha256(v.encode()).digest() for v in vectors.values()}) == 100
    notice = fetch("LICENSE")
    resources = root / "app/src/main/res/drawable"
    for activity, vector in vectors.items():
        resource = "ic_activity_" + activity.replace("-", "_")
        (resources / f"{resource}.xml").write_text(vector, encoding="utf-8")
        catalog = re.sub(r'(activity\("' + re.escape(activity) + r'", "[^"]+", R.drawable.)\w+',
                         lambda m: m[1] + resource, catalog)
    for group, activity in GROUP_ICONS.items():
        resource = "ic_activity_" + activity.replace("-", "_")
        catalog = re.sub(r'(ActivityGroup\("' + group + r'", "[^"]+", R.drawable.)\w+',
                         lambda m: m[1] + resource, catalog)
    catalog_path.write_text(catalog, encoding="utf-8")
    notice_path = root / "app/src/main/assets/licenses/lucide.txt"
    notice_path.parent.mkdir(parents=True, exist_ok=True)
    notice_path.write_text(notice, encoding="utf-8")
    print("Imported 100 distinct activity icons with the upstream license notice.")


if __name__ == "__main__":
    main()
