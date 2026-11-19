"""Check every block of a map against the blocks that exist: modded ids need a blockstate file in the repository
assets (heraliz_decors, heralisz_utils) or in the cfm jar; vanilla ids are checked against a list of 1.20.1 vanilla
ids. Also checks the block-state properties of modded blocks against their blockstate variants.
Usage: python valider_xl.py [map id ...] (the four tournament maps by default)."""
import glob, json, os, sys, zipfile
import nbt_lecture as nl

from chemins_cartes import CARTES, DEPOT, DONNEES

REPO = str(DEPOT)
VANILLE_OK = set("""stone dirt coarse_dirt gravel grass_block podzol moss_block dirt_path rooted_dirt sand packed_mud mud
andesite polished_andesite andesite_wall stone_bricks cracked_stone_bricks chiseled_stone_bricks stone_brick_slab stone_brick_stairs
stone_brick_wall smooth_stone smooth_stone_slab cobblestone cobblestone_stairs andesite_stairs bricks white_terracotta terracotta
lime_terracotta yellow_terracotta orange_terracotta brown_terracotta cyan_terracotta light_blue_terracotta
white_wool yellow_wool light_gray_wool pink_wool orange_wool red_wool brown_wool light_blue_wool gray_wool
light_gray_concrete gray_concrete black_concrete blue_concrete green_concrete yellow_concrete light_blue_concrete
quartz_block quartz_bricks quartz_pillar polished_deepslate netherite_block lapis_block iron_block gold_block diamond_block
coal_block melon barrel crafting_table iron_bars glass_pane gray_stained_glass_pane gray_stained_glass light
lantern chain lightning_rod spruce_planks oak_planks dark_oak_planks spruce_slab dark_oak_slab spruce_stairs spruce_fence
dark_oak_fence spruce_log oak_log dark_oak_log birch_log stripped_oak_log birch_leaves oak_leaves dark_oak_leaves spruce_leaves
grass fern dead_bush red_carpet brown_carpet gray_carpet light_blue_glazed_terracotta white_glazed_terracotta
orange_glazed_terracotta light_gray_terracotta mud_bricks rail red_terracotta stripped_dark_oak_log stripped_spruce_log
smithing_table polished_blackstone_bricks deepslate_tiles cracked_deepslate_tiles polished_deepslate_slab stone_slab
cobblestone_wall mossy_cobblestone mossy_stone_bricks hay_block dark_oak_stairs spruce_trapdoor iron_trapdoor
oak_fence oak_slab oak_stairs birch_planks white_concrete gray_wool black_wool brown_wool cyan_wool lime_wool blue_wool
red_concrete orange_concrete yellow_wool green_wool purple_wool magenta_wool pink_terracotta yellow_terracotta
polished_andesite_slab andesite_slab cobblestone_slab smooth_stone_slab sandstone cut_sandstone sand red_sand clay
mud packed_ice snow_block snow tuff calcite dripstone_block deepslate cobbled_deepslate polished_basalt basalt
quartz_slab quartz_stairs stone_stairs cobbled_deepslate_wall deepslate_brick_wall chain lantern soul_lantern torch
jungle_planks acacia_planks warped_planks crimson_planks mangrove_planks cherry_planks bamboo_planks
oak_trapdoor iron_door glass ladder scaffolding cauldron anvil hopper composter bookshelf loom lectern grindstone
stonecutter blast_furnace furnace smoker jukebox note_block target lodestone gold_block emerald_block redstone_block
copper_block cut_copper exposed_copper weathered_copper oxidized_copper coal_block raw_iron_block bone_block
dried_kelp_block melon pumpkin carved_pumpkin hay_block sponge wet_sponge prismarine prismarine_bricks dark_prismarine
end_stone end_stone_bricks purpur_block nether_bricks red_nether_bricks netherrack soul_sand soul_soil
light_blue_terracotta cyan_terracotta purple_terracotta magenta_terracotta black_terracotta gray_terracotta
white_concrete_powder iron_bars oak_planks spruce_slab spruce_stairs dark_oak_slab dark_oak_planks oak_log
birch_log spruce_log dark_oak_log stripped_oak_log stripped_birch_log oak_leaves birch_leaves spruce_leaves
dark_oak_leaves poppy bell lime_concrete polished_andesite_stairs mangrove_stairs mangrove_slab green_terracotta polished_granite
polished_blackstone""".split())


def blockstates_moddes():
    ok = {}
    for ns in ("heraliz_decors", "heralisz_utils"):
        for f in glob.glob(os.path.join(REPO, "modules", "*", "src", "client", "resources", "assets", ns, "blockstates", "*.json")):
            ok[ns + ":" + os.path.basename(f)[:-5]] = json.load(open(f))
    for j in glob.glob(os.path.join(REPO, "serveur", "mods-tiers", "cfm-*.jar")):
        z = zipfile.ZipFile(j)
        for n in z.namelist():
            if n.startswith("assets/cfm/blockstates/") and n.endswith(".json"):
                ok["cfm:" + n.split("/")[-1][:-5]] = json.loads(z.read(n))
    return ok


def props_connues(bs):
    keys = {}
    for k in bs.get("variants", {}):
        for kv in k.split(","):
            if "=" in kv:
                a, b = kv.split("=")
                keys.setdefault(a, set()).add(b)
    for part in bs.get("multipart", []):
        w = part.get("when", {})
        for c in w.get("OR", [w]):
            for a, b in c.items():
                keys.setdefault(a, set()).update(str(b).split("|"))
    return keys


def main(ident):
    size, palette, blocs, racine = nl.structure(os.path.join(str(DONNEES), "structures", ident + ".nbt"))
    modd = blockstates_moddes()
    pb = 0
    noms = sorted({p.split("[")[0] for p in palette})
    for n in noms:
        ns, b = n.split(":")
        if ns == "minecraft":
            if b not in VANILLE_OK:
                print("vanilla id not in the checked list:", n); pb += 1
        elif n not in modd:
            print("UNKNOWN modded block:", n); pb += 1
    for e in racine["palette"]:
        n = e["Name"]
        if n in modd and e.get("Properties"):
            kp = props_connues(modd[n])
            for k, v in e["Properties"].items():
                autorise = kp.get(k, set())
                if autorise and autorise <= {"true", "false"}:
                    autorise = {"true", "false"}
                if kp and k in kp and v not in autorise:
                    print("bad value", n, k, v, "allowed", sorted(kp[k])); pb += 1
    print(f"{ident}: {len(noms)} block ids, {len(palette)} states, {len(blocs)} blocks, {pb} problem(s)")
    return pb


if __name__ == "__main__":
    sys.exit(1 if sum(main(c) for c in (sys.argv[1:] or CARTES)) else 0)
