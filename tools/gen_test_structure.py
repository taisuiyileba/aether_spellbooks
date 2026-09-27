"""
Writes an empty 9x9x9 structure used as the GameTest template (data/aether_spellbooks/structures/empty.nbt).
Only used in development; excluded from the release jar.
"""
import gzip
import os
import struct

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "data", "aether_spellbooks", "structures", "empty.nbt")

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def name(n):
    b = n.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def t_int(n, v):
    return bytes([TAG_INT]) + name(n) + struct.pack(">i", v)


def t_string(n, v):
    return bytes([TAG_STRING]) + name(n) + name(v)


def t_list(n, elem_type, payloads):
    return bytes([TAG_LIST]) + name(n) + bytes([elem_type]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def compound_payload(*tags):
    return b"".join(tags) + bytes([TAG_END])


def main():
    size = [struct.pack(">i", 9)] * 3
    palette = [compound_payload(t_string("Name", "minecraft:air"))]
    root = bytes([TAG_COMPOUND]) + name("") + compound_payload(
        t_int("DataVersion", 3465),  # 1.20.1
        t_list("size", TAG_INT, size),
        t_list("palette", TAG_COMPOUND, palette),
        t_list("blocks", TAG_COMPOUND, []),
        t_list("entities", TAG_COMPOUND, []),
    )
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, "wb") as f:
        f.write(root)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
