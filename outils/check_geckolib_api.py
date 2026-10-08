"""Vérifie qu'un mod compilé contre GeckoLib N tourne avec une autre version de GeckoLib (ex. geckolib222 4.4.7 du modpack).

Lit les tables de constantes de toutes les classes du mod, relève chaque classe, méthode et champ GeckoLib utilisé,
puis cherche chacun (en remontant les classes mères et les interfaces) dans le jar GeckoLib visé.
Usage : python check_geckolib_api.py <mod.jar> <geckolib.jar>
"""
import struct
import sys
import zipfile

PREFIX = "software/bernie/geckolib/"


def parse(data):
    """-> (nom, super, interfaces, {(nom, descripteur)} méthodes + champs, [(genre, classe, nom, desc)] références)."""
    n = struct.unpack(">H", data[8:10])[0]
    cp, i, k = [None] * n, 10, 1
    while k < n:
        tag = data[i]
        if tag == 1:
            ln = struct.unpack(">H", data[i + 1:i + 3])[0]
            cp[k] = ("utf8", data[i + 3:i + 3 + ln].decode("utf-8", "replace"))
            i += 3 + ln
        elif tag in (3, 4):
            i += 5
        elif tag in (5, 6):
            i += 9
            k += 1
        elif tag in (7, 8, 16, 19, 20):
            cp[k] = (tag, struct.unpack(">H", data[i + 1:i + 3])[0])
            i += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            cp[k] = (tag,) + struct.unpack(">HH", data[i + 1:i + 5])
            i += 5
        elif tag == 15:
            i += 4
        else:
            raise ValueError(f"tag {tag}")
        k += 1
    utf = lambda x: cp[x][1]
    cls = lambda x: utf(cp[x][1])
    refs = []
    for e in cp:
        if e and e[0] in (9, 10, 11):
            nt = cp[e[2]]
            refs.append(({9: "champ", 10: "méthode", 11: "méthode"}[e[0]], cls(e[1]), utf(nt[1]), utf(nt[2])))
        elif e and e[0] == 7:
            refs.append(("classe", utf(e[1]), "", ""))
    acc, this, sup, ni = struct.unpack(">HHHH", data[i:i + 8])
    i += 8
    itf = [cls(struct.unpack(">H", data[i + 2 * j:i + 2 * j + 2])[0]) for j in range(ni)]
    i += 2 * ni
    members = set()
    for _ in range(2):  # champs puis méthodes
        cnt = struct.unpack(">H", data[i:i + 2])[0]
        i += 2
        for _ in range(cnt):
            _, nm, ds, ac = struct.unpack(">HHHH", data[i:i + 8])
            members.add((utf(nm), utf(ds)))
            i += 8
            for _ in range(ac):
                ln = struct.unpack(">I", data[i + 2:i + 6])[0]
                i += 6 + ln
    return cls(this), (cls(sup) if sup else None), itf, members, refs


def main():
    mod, gl = sys.argv[1], sys.argv[2]
    lib = {}
    with zipfile.ZipFile(gl) as z:
        for n in z.namelist():
            if n.startswith(PREFIX) and n.endswith(".class"):
                name, sup, itf, mem, _ = parse(z.read(n))
                lib[name] = (sup, itf, mem)
    used = set()
    with zipfile.ZipFile(mod) as z:
        for n in z.namelist():
            if n.endswith(".class") and not n.startswith("META-INF/"):
                for r in parse(z.read(n))[4]:
                    if r[1].lstrip("[L").startswith(PREFIX):
                        used.add(r)

    def has(c, nm, ds, seen=None):
        seen = seen or set()
        if c not in lib or c in seen:
            return False
        seen.add(c)
        sup, itf, mem = lib[c]
        if any(m == nm and (ds == "" or d == ds or (ds.startswith("(") is False and d == ds)) for m, d in mem):
            return True
        return any(has(x, nm, ds, seen) for x in [sup] + itf if x)

    missing = []
    for kind, c, nm, ds in sorted(used):
        c = c.lstrip("[").removeprefix("L").removesuffix(";")
        if not c.startswith(PREFIX):
            continue
        if kind == "classe":
            if c not in lib:
                missing.append(f"classe {c}")
        elif c in lib and not has(c, nm, ds):
            # méthode héritée d'une classe hors GeckoLib (Object, Minecraft...) : acceptée si la classe existe
            missing.append(f"{kind} {c}.{nm}{ds}")
        elif c not in lib:
            missing.append(f"{kind} {c}.{nm}{ds} (classe absente)")
    print(f"{len(used)} références GeckoLib vérifiées dans {mod}")
    print(f"{len(missing)} absentes de {gl}")
    for m in missing:
        print("  ", m)


if __name__ == "__main__":
    main()
