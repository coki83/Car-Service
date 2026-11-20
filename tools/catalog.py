"""Single source for the car picker and service templates.
Run: python3 tools/catalog.py  -> regenerates CarCatalog.kt and web/catalog.js
"""
import json, pathlib

MAKES = {
 "Alfa Romeo": ["Giulia", "Giulietta", "Stelvio", "Tonale", "MiTo", "159", "147", "Junior"],
 "Audi": ["A1", "A3", "A4", "A5", "A6", "A7", "A8", "Q2", "Q3", "Q5", "Q7", "Q8", "Q4 e-tron", "e-tron", "TT"],
 "BMW": ["1 Series", "2 Series", "3 Series", "4 Series", "5 Series", "7 Series", "X1", "X2", "X3", "X4", "X5", "X6", "i3", "i4", "iX", "iX3"],
 "BYD": ["Atto 3", "Dolphin", "Seal", "Seal U", "Han", "Tang"],
 "Chevrolet": ["Aveo", "Cruze", "Spark", "Captiva", "Orlando"],
 "Citroën": ["C1", "C3", "C3 Aircross", "C4", "C4 Picasso", "C5", "C5 Aircross", "Berlingo", "Jumpy", "Jumper", "ë-C4"],
 "Cupra": ["Born", "Formentor", "Leon", "Ateca", "Tavascan"],
 "Dacia": ["Sandero", "Logan", "Duster", "Jogger", "Spring", "Lodgy", "Dokker"],
 "DS": ["DS 3", "DS 4", "DS 7", "DS 9"],
 "Fiat": ["500", "500X", "500L", "Panda", "Punto", "Tipo", "Doblò", "Ducato", "Bravo", "600"],
 "Ford": ["Fiesta", "Focus", "Mondeo", "Puma", "Kuga", "EcoSport", "C-Max", "S-Max", "Galaxy", "Mustang Mach-E", "Ranger", "Transit", "Transit Custom", "Ka"],
 "Honda": ["Jazz", "Civic", "Civic Tourer", "Accord", "FR-V", "Stream", "Insight", "CR-Z", "HR-V", "CR-V", "ZR-V", "Legend", "e"],
 "Hyundai": ["i10", "i20", "i30", "i40", "Bayon", "Kona", "Tucson", "Santa Fe", "Ioniq", "Ioniq 5", "Ioniq 6", "ix35"],
 "Jaguar": ["XE", "XF", "E-Pace", "F-Pace", "I-Pace"],
 "Jeep": ["Renegade", "Compass", "Avenger", "Cherokee", "Wrangler", "Grand Cherokee"],
 "Kia": ["Picanto", "Rio", "Ceed", "XCeed", "Stonic", "Niro", "Sportage", "Sorento", "EV6", "EV9", "Soul", "Optima"],
 "Land Rover": ["Defender", "Discovery", "Discovery Sport", "Range Rover", "Range Rover Evoque", "Range Rover Sport", "Range Rover Velar", "Freelander"],
 "Lexus": ["CT", "IS", "ES", "NX", "RX", "UX", "LBX"],
 "Mazda": ["2", "3", "6", "CX-3", "CX-30", "CX-5", "CX-60", "MX-5", "MX-30"],
 "Mercedes-Benz": ["A-Class", "B-Class", "C-Class", "E-Class", "S-Class", "CLA", "GLA", "GLB", "GLC", "GLE", "EQA", "EQB", "EQC", "EQE", "Vito", "Sprinter"],
 "MG": ["MG3", "MG4", "MG5", "ZS", "HS", "Marvel R"],
 "Mini": ["Hatch", "Clubman", "Countryman", "Cabrio", "Electric"],
 "Mitsubishi": ["Space Star", "Colt", "ASX", "Eclipse Cross", "Outlander", "L200"],
 "Nissan": ["Micra", "Juke", "Qashqai", "X-Trail", "Leaf", "Ariya", "Note", "Navara"],
 "Opel / Vauxhall": ["Corsa", "Astra", "Insignia", "Mokka", "Crossland", "Grandland", "Zafira", "Meriva", "Combo", "Vivaro", "Adam", "Karl"],
 "Peugeot": ["108", "208", "2008", "308", "3008", "408", "508", "5008", "Partner", "Rifter", "Expert", "Boxer", "e-208"],
 "Polestar": ["2", "3", "4"],
 "Porsche": ["911", "Cayenne", "Macan", "Panamera", "Taycan"],
 "Renault": ["Twingo", "Clio", "Captur", "Mégane", "Scénic", "Arkana", "Austral", "Kadjar", "Koleos", "Talisman", "Laguna", "Zoe", "Kangoo", "Trafic", "Master", "Espace"],
 "Seat": ["Ibiza", "Leon", "Arona", "Ateca", "Tarraco", "Alhambra", "Toledo", "Mii"],
 "Škoda": ["Citigo", "Fabia", "Scala", "Rapid", "Octavia", "Superb", "Kamiq", "Karoq", "Kodiaq", "Enyaq", "Yeti", "Roomster"],
 "Smart": ["ForTwo", "ForFour", "#1", "#3"],
 "SsangYong / KGM": ["Tivoli", "Korando", "Rexton", "Torres"],
 "Subaru": ["Impreza", "XV / Crosstrek", "Forester", "Outback", "Legacy", "Solterra"],
 "Suzuki": ["Swift", "Ignis", "Baleno", "Vitara", "S-Cross", "Jimny", "Celerio", "SX4", "Grand Vitara"],
 "Tesla": ["Model 3", "Model Y", "Model S", "Model X"],
 "Toyota": ["Aygo", "Aygo X", "Yaris", "Yaris Cross", "Corolla", "Auris", "Avensis", "C-HR", "RAV4", "Prius", "Land Cruiser", "Hilux", "Proace", "bZ4X"],
 "Volkswagen": ["up!", "Polo", "Golf", "Jetta", "Passat", "Arteon", "T-Cross", "T-Roc", "Taigo", "Tiguan", "Touran", "Touareg", "Sharan", "Caddy", "Transporter", "ID.3", "ID.4", "ID.5", "ID.7", "ID. Buzz"],
 "Volvo": ["V40", "V60", "V90", "S60", "S90", "XC40", "XC60", "XC90", "EX30", "EX90"],
}

FUELS = [
 ("PETROL", "Petrol"), ("DIESEL", "Diesel"), ("HYBRID", "Hybrid (petrol)"),
 ("PLUGIN_HYBRID", "Plug-in hybrid"), ("ELECTRIC", "Electric"), ("LPG", "Petrol + LPG"),
]

# (id, name, km or None, months or None[, first model year it usually applies to])
# DPF: Euro 5 diesels (2010+). AdBlue/SCR: Euro 6 diesels (2016+). Unknown year -> included.
OIL = ("oil", "Engine oil & filter", 15000, 12)
AIR = ("air", "Air filter", 30000, 24)
CABIN = ("cabin", "Cabin (pollen) filter", 15000, 12)
BRAKE = ("brakefluid", "Brake fluid", None, 24)
COOLANT = ("coolant", "Coolant", None, 60)
INSPECT = ("inspection", "Technical inspection", None, 12)
PLUGS = ("plugs", "Spark plugs", 60000, 48)
BELT = ("timing", "Timing belt / chain check", 120000, 72)
BRAKES = ("brakes", "Brake pads & discs check", 20000, 12)

TEMPLATES = {
 "PETROL": [OIL, AIR, CABIN, PLUGS, BELT, BRAKES, BRAKE, COOLANT, INSPECT],
 "DIESEL": [OIL, ("fuelfilter", "Diesel fuel filter", 30000, 24), AIR, CABIN,
            ("glowplugs", "Glow plugs", 100000, None), ("dpf", "DPF (particle filter) check", 100000, None, 2010),
            ("adblue", "AdBlue top-up", 10000, None, 2016), BELT, BRAKES, BRAKE, COOLANT, INSPECT],
 "HYBRID": [OIL, AIR, CABIN, PLUGS, ("hybridcheck", "Hybrid system check", None, 12), BRAKES, BRAKE, COOLANT, INSPECT],
 "PLUGIN_HYBRID": [OIL, AIR, CABIN, PLUGS, ("hybridcheck", "Hybrid battery & charging check", None, 12), BRAKES, BRAKE, COOLANT, INSPECT],
 "ELECTRIC": [CABIN, ("tyres", "Tyre rotation", 10000, 12), BRAKES, BRAKE,
              ("evcoolant", "Battery coolant", None, 48), ("battery12v", "12 V battery check", None, 12),
              ("evcheck", "High-voltage system check", None, 24), INSPECT],
 "LPG": [OIL, AIR, CABIN, PLUGS, ("lpgfilter", "LPG filters", 15000, 12),
         ("lpgcheck", "LPG system inspection", None, 12), BELT, BRAKES, BRAKE, COOLANT, INSPECT],
}

root = pathlib.Path(__file__).resolve().parent.parent

def kt_str(s): return json.dumps(s, ensure_ascii=False)
def kt_opt(v): return "null" if v is None else f"{v:_}"

lines = ["package io.github.coki83.carservice.data", "",
         "// GENERATED by tools/catalog.py. Edit that file and re-run it.", "",
         "enum class FuelType(val label: String) {"]
lines += [f"    {k}({kt_str(v)})," for k, v in FUELS]
lines += ["}", "", "data class Template(val id: String, val name: String, val km: Int?, val months: Int?, val minYear: Int? = null)", "",
          "object CarCatalog {", "    val makes: Map<String, List<String>> = linkedMapOf("]
for m, models in MAKES.items():
    lines.append(f"        {kt_str(m)} to listOf({', '.join(kt_str(x) for x in models)}),")
lines += ["    )", "", "    fun template(fuel: FuelType): List<Template> = when (fuel) {"]
for f, items in TEMPLATES.items():
    lines.append(f"        FuelType.{f} -> listOf(")
    for i in items:
        my = f", minYear = {i[4]}" if len(i) > 4 else ""
        lines.append(f"            Template({kt_str(i[0])}, {kt_str(i[1])}, {kt_opt(i[2])}, {kt_opt(i[3])}{my}),")
    lines.append("        )")
lines += ["    }", "",
          "    /** Template items that fit this car's model year (all of them when the year is unknown). */",
          "    fun templateFor(fuel: FuelType, year: Int?): List<Template> =",
          "        template(fuel).filter { t -> year == null || t.minYear == null || year >= t.minYear }",
          "}", ""]
(root / "app/src/main/java/io/github/coki83/carservice/data/CarCatalog.kt").write_text("\n".join(lines))

js = {"makes": MAKES, "fuels": FUELS,
      "templates": {f: [{"id": i[0], "name": i[1], "km": i[2], "months": i[3], "minYear": i[4] if len(i) > 4 else None} for i in items] for f, items in TEMPLATES.items()}}
(root / "web").mkdir(exist_ok=True)
(root / "web/catalog.js").write_text("// GENERATED by tools/catalog.py\nconst CATALOG = " + json.dumps(js, ensure_ascii=False) + ";\n")
print("ok", sum(len(v) for v in MAKES.values()), "models")

# Browser version: inline the catalog into the page (artifact pages can't load local scripts).
tpl = (root / "web/index.template.html").read_text()
(root / "web/car-service.html").write_text(tpl.replace("/*CATALOG*/", (root / "web/catalog.js").read_text().replace("// GENERATED by tools/catalog.py\n", "")))
print("web page written")
