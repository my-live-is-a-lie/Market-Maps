from pathlib import Path
p = Path("app/src/main/java/com/marketmaps/app/ui/map/SearchBar.kt")
c = p.read_text(encoding="utf-8")
junk = """            }

                    )
                }
            }

            if (query.isNotBlank()"""
fixed = """            }

            if (query.isNotBlank()"""
if junk not in c:
    if "matchedFilter" in c and c.count("{") == c.count("}"):
        print("already balanced")
        raise SystemExit(0)
    raise SystemExit("junk block not found")
c = c.replace(junk, fixed, 1)
p.write_text(c, encoding="utf-8")
print("fixed braces", c.count("{"), c.count("}"))
