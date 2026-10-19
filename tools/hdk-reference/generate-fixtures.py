#!/usr/bin/env python3
"""Generate independent synthetic vectors for Chimali's local HDK profile."""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import os
import platform
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric import ec, ed25519
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
from dilithium_py.ml_dsa import ML_DSA_65
from mnemonic import Mnemonic


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "core/security/src/androidHostTest/resources/hdk/local-profile-v1.json"
MLDSA_CROSS_ENGINE_OUT = ROOT / "feature/fido2/src/androidHostTest/resources/hdk/mldsa65-cross-engine-v1.json"
N = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551
P = 0xFFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF
B = 0x5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B
G = (
    0x6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296,
    0x4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5,
)
ROOT_SALT = b"chimali-local-key-derivation-v1"
P256_PARENT_INFO = b"chimali-local-hdk-p256-v1/parent"
PROFILE_LABELS = {
    "es256": ("chimali-local-hdk-p256-v1", "chimali-local-hdk-p256-v1/credential-secret"),
    "ed25519": ("chimali-local-ed25519-v1", "chimali-local-ed25519-v1/credential-seed"),
    "ml-dsa-65": ("chimali-local-mldsa65-v1", "chimali-local-mldsa65-v1/credential-seed"),
}


def hx(data: bytes) -> str:
    return data.hex()


def i2osp(value: int, length: int) -> bytes:
    return value.to_bytes(length, "big")


def hkdf_extract(salt: bytes, ikm: bytes) -> bytes:
    return hmac.new(salt or bytes(32), ikm, hashlib.sha256).digest()


def hkdf_expand(prk: bytes, info: bytes, length: int) -> bytes:
    if length > 255 * 32:
        raise ValueError("HKDF output too long")
    out = bytearray()
    previous = b""
    for counter in range(1, (length + 31) // 32 + 1):
        previous = hmac.new(prk, previous + info + bytes([counter]), hashlib.sha256).digest()
        out.extend(previous)
    return bytes(out[:length])


def labeled_extract(salt: bytes, suite_id: bytes, label: bytes, ikm: bytes) -> bytes:
    return hkdf_extract(salt, b"HPKE-v1" + suite_id + label + ikm)


def labeled_expand(prk: bytes, suite_id: bytes, label: bytes, info: bytes, length: int) -> bytes:
    return hkdf_expand(prk, i2osp(length, 2) + b"HPKE-v1" + suite_id + label + info, length)


def rfc9180_p256(ikm: bytes) -> tuple[int, bytes, int]:
    suite_id = b"KEM" + b"\x00\x10"
    dkp_prk = labeled_extract(b"", suite_id, b"dkp_prk", ikm)
    for counter in range(256):
        candidate = labeled_expand(dkp_prk, suite_id, b"candidate", bytes([counter]), 32)
        scalar = int.from_bytes(candidate, "big")
        if 0 < scalar < N:
            pub = ec.derive_private_key(scalar, ec.SECP256R1()).public_key().public_bytes(Encoding.X962, PublicFormat.UncompressedPoint)
            return scalar, pub, counter
    raise ValueError("RFC 9180 P-256 sampler exhausted")


def point_add(left: tuple[int, int] | None, right: tuple[int, int] | None) -> tuple[int, int] | None:
    if left is None:
        return right
    if right is None:
        return left
    x1, y1 = left
    x2, y2 = right
    if x1 == x2 and (y1 + y2) % P == 0:
        return None
    if left == right:
        slope = ((3 * x1 * x1 - 3) * pow(2 * y1, -1, P)) % P
    else:
        slope = ((y2 - y1) * pow((x2 - x1) % P, -1, P)) % P
    x3 = (slope * slope - x1 - x2) % P
    y3 = (slope * (x1 - x3) - y1) % P
    return x3, y3


def point_mul(scalar: int, point: tuple[int, int] | None = G) -> tuple[int, int] | None:
    result = None
    addend = point
    while scalar:
        if scalar & 1:
            result = point_add(result, addend)
        addend = point_add(addend, addend)
        scalar >>= 1
    return result


def sec1(point: tuple[int, int]) -> bytes:
    return b"\x04" + i2osp(point[0], 32) + i2osp(point[1], 32)


def credential_info(label: str, credential_id: bytes) -> bytes:
    return label.encode("ascii") + b"\x00" + i2osp(len(credential_id), 2) + credential_id


def check_reference_vectors() -> dict[str, str]:
    # RFC 5869, Appendix A.1.
    prk = hkdf_extract(bytes.fromhex("000102030405060708090a0b0c"), bytes.fromhex("0b" * 22))
    okm = hkdf_expand(prk, bytes.fromhex("f0f1f2f3f4f5f6f7f8f9"), 42)
    assert hx(prk) == "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5"
    assert hx(okm) == "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"

    # RFC 9180 Appendix A.3.1, DHKEM(P-256, HKDF-SHA256) DeriveKeyPair.
    rfc_sk, rfc_pk, rfc_counter = rfc9180_p256(bytes.fromhex("4270e54ffd08d79d5928020af4686d8f6b7d35dbe470265f1f5aa22816ce860e"))
    assert hx(i2osp(rfc_sk, 32)) == "4995788ef4b9d6132b249ce59a77281493eb39af373d236a1fe415cb0c2d7beb"
    assert hx(rfc_pk) == "04a92719c6195d5085104f469a8b9814d5838ff72b60501e2c4466e5e67b325ac98536d7b61a1af4b78e5b7f951c0900be863c403ce65c9bfcb9382657222d18c4"

    # RFC 8032, test vector 1 (empty message).
    ed_seed = bytes.fromhex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
    ed_pk = ed25519.Ed25519PrivateKey.from_private_bytes(ed_seed).public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    ed_sig = ed25519.Ed25519PrivateKey.from_private_bytes(ed_seed).sign(b"")
    assert hx(ed_pk) == "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
    assert hx(ed_sig) == "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"

    # NIST ACVP ML-DSA-65 internal key-generation vector, seed and output hashes.
    mldsa_seed = bytes.fromhex("A991FD42B071D49C48AE3E75C647459E0DAAD1E1BA356A04801912D3294BCFF8")
    mldsa_pk, mldsa_sk = ML_DSA_65._keygen_internal(mldsa_seed)
    assert len(mldsa_pk) == 1952 and len(mldsa_sk) == 4032
    assert hashlib.sha256(mldsa_pk).hexdigest() == "b1a7d0d2f0d7a04b9d5ffccd9bd578864dab4a01cdd7f70a05cd1f4f0672e43a"
    assert hashlib.sha256(mldsa_sk).hexdigest() == "56c53ac82fbff7d81b7a8cfbbc73011ceccad677e16dc53f2ece66d49aa11edd"
    mldsa_message = b"Chimali synthetic ML-DSA-65 cross-engine verification fixture v1"
    mldsa_signature = ML_DSA_65.sign(mldsa_sk, mldsa_message, deterministic=True)
    assert ML_DSA_65.verify(mldsa_pk, mldsa_message, mldsa_signature)

    return {
        "rfc5869_case": "A.1",
        "rfc9180_case": "A.3.1",
        "rfc9180_candidate_counter": str(rfc_counter),
        "rfc8032_case": "test-1",
        "nist_acvp_mldsa65_seed": hx(mldsa_seed),
        "nist_acvp_mldsa65_public_sha256": hashlib.sha256(mldsa_pk).hexdigest(),
        "nist_acvp_mldsa65_secret_sha256": hashlib.sha256(mldsa_sk).hexdigest(),
        "nist_acvp_mldsa65_signature_sha256": hashlib.sha256(mldsa_signature).hexdigest(),
    }


def find_short_id_collision() -> tuple[bytes, bytes, str]:
    seen: dict[bytes, bytes] = {}
    for number in range(2_000_000):
        candidate = number.to_bytes(8, "big") + b"collision-fixture"
        prefix = hashlib.sha256(candidate).digest()[:4]
        if prefix in seen and seen[prefix] != candidate:
            return seen[prefix], candidate, hx(prefix)
        seen[prefix] = candidate
    raise RuntimeError("Could not find retired shortened-ID collision")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate or verify independent synthetic HDK fixtures.")
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify the frozen fixture matches without writing it",
    )
    args = parser.parse_args()

    vectors = check_reference_vectors()
    mldsa_seed = bytes.fromhex(vectors["nist_acvp_mldsa65_seed"])
    mldsa_public_key, mldsa_secret_key = ML_DSA_65._keygen_internal(mldsa_seed)
    mldsa_message = b"Chimali synthetic ML-DSA-65 cross-engine verification fixture v1"
    mldsa_signature = ML_DSA_65.sign(mldsa_secret_key, mldsa_message, deterministic=True)
    assert ML_DSA_65.verify(mldsa_public_key, mldsa_message, mldsa_signature)
    local_wordlist = (ROOT / "core/security/src/commonMain/resources/bip39_english.txt").read_text(encoding="utf-8").splitlines()
    official_words = Mnemonic("english").wordlist
    assert local_wordlist == official_words, "Project BIP39 wordlist differs from mnemonic 0.21 English list"
    mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    bip39_seed = Mnemonic("english").to_seed(mnemonic, passphrase="TREZOR")
    assert hx(bip39_seed) == "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04"

    synthetic_s = bytes(range(64))
    prk = hkdf_extract(ROOT_SALT, synthetic_s)
    parent_ikm = hkdf_expand(prk, P256_PARENT_INFO, 32)
    parent_scalar, parent_public, parent_counter = rfc9180_p256(parent_ikm)
    assert parent_public == ec.derive_private_key(parent_scalar, ec.SECP256R1()).public_key().public_bytes(Encoding.X962, PublicFormat.UncompressedPoint)

    local_credentials = []
    for length in (16, 32, 1023):
        credential_id = bytes((index * 37 + 11) & 0xFF for index in range(length))
        local_credentials.append({"credential_id": hx(credential_id), "length": length, "branches": {}})
        for name, (label, expand_label) in PROFILE_LABELS.items():
            secret = hkdf_expand(prk, credential_info(expand_label, credential_id), 32)
            branch: dict[str, str] = {"profile": label, "secret_or_seed": hx(secret)}
            if name == "es256":
                digest = hashlib.sha256(secret + parent_public + i2osp(0, 4)).digest()
                factor = int.from_bytes(digest, "big") % (N - 1) + 1
                child_scalar = parent_scalar * factor % N
                child_point = point_mul(factor, (int.from_bytes(parent_public[1:33], "big"), int.from_bytes(parent_public[33:], "big")))
                assert child_point is not None and child_scalar != 0
                openssl_point = ec.derive_private_key(child_scalar, ec.SECP256R1()).public_key().public_bytes(Encoding.X962, PublicFormat.UncompressedPoint)
                assert sec1(child_point) == openssl_point
                branch.update({"parent_public": hx(parent_public), "digest": hx(digest), "factor": hx(i2osp(factor, 32)), "child_private": hx(i2osp(child_scalar, 32)), "child_public": hx(openssl_point)})
            elif name == "ed25519":
                branch["public_key"] = hx(ed25519.Ed25519PrivateKey.from_private_bytes(secret).public_key().public_bytes(Encoding.Raw, PublicFormat.Raw))
            else:
                public_key, secret_key = ML_DSA_65._keygen_internal(secret)
                branch.update({"public_key": hx(public_key), "public_key_sha256": hashlib.sha256(public_key).hexdigest(), "secret_key_sha256": hashlib.sha256(secret_key).hexdigest()})
            local_credentials[-1]["branches"][name] = branch

    direct_children = []
    parent_scalar_one = 1
    for index in (0, 1, 0x80000000, 0xFFFFFFFF):
        secret = bytes(range(32))
        context = sec1(G) + i2osp(index, 4)
        digest = hashlib.sha256(secret + context).digest()
        factor = int.from_bytes(digest, "big") % (N - 1) + 1
        child_scalar = parent_scalar_one * factor % N
        point = point_mul(child_scalar)
        assert point is not None
        assert sec1(point) == ec.derive_private_key(child_scalar, ec.SECP256R1()).public_key().public_bytes(Encoding.X962, PublicFormat.UncompressedPoint)
        direct_children.append({"index": index, "secret": hx(secret), "parent_scalar": hx(i2osp(parent_scalar_one, 32)), "context": hx(context), "digest": hx(digest), "factor": hx(i2osp(factor, 32)), "child_private": hx(i2osp(child_scalar, 32)), "child_public": hx(sec1(point))})

    collision_a, collision_b, short_prefix = find_short_id_collision()
    collision_prks = []
    for credential_id in (collision_a, collision_b):
        secret = hkdf_expand(prk, credential_info(PROFILE_LABELS["es256"][1], credential_id), 32)
        collision_prks.append({"credential_id": hx(credential_id), "retired_sha256_prefix": short_prefix, "full_secret": hx(secret)})
    assert collision_prks[0]["full_secret"] != collision_prks[1]["full_secret"]

    payload = {
        "schema": "chimali-local-profile-v1-reference-fixtures",
        "provenance": {"generator": "tools/hdk-reference/generate-fixtures.py", "python": platform.python_version(), "openssl": __import__("ssl").OPENSSL_VERSION, "cryptography": "50.0.2", "mnemonic": "0.21", "dilithium_py": "1.5.1", "reference_checks": vectors},
        "bip39": {"mnemonic": mnemonic, "passphrase": "TREZOR", "seed": hx(bip39_seed)},
        "local_root": {"synthetic_s": hx(synthetic_s), "salt": hx(ROOT_SALT), "prk": hx(prk), "parent_info": hx(P256_PARENT_INFO), "parent_ikm": hx(parent_ikm), "parent_scalar": hx(i2osp(parent_scalar, 32)), "parent_public": hx(parent_public), "parent_candidate_counter": parent_counter},
        "credentials": local_credentials,
        "direct_child_vectors": direct_children,
        "retired_short_id_collision": {"hash_prefix": short_prefix, "ids": collision_prks},
    }
    mldsa_cross_engine_payload = {
        "schema": "chimali-mldsa65-cross-engine-v1",
        "provenance": {
            "generator": "tools/hdk-reference/generate-fixtures.py",
            "dilithium_py": "1.5.1",
            "nist_acvp_source_commit": "975de31eb83d87039ec88934fdc47d8c312b892d",
            "seed_source": "NIST ACVP ML-DSA-65 internal key-generation vector",
            "signature_mode": "FIPS 204 deterministic signing, empty context",
        },
        "seed": hx(mldsa_seed),
        "public_key_sha256": hashlib.sha256(mldsa_public_key).hexdigest(),
        "message": hx(mldsa_message),
        "signature": hx(mldsa_signature),
    }
    if args.check:
        if not OUT.is_file():
            raise SystemExit(f"Frozen fixture is missing: {OUT.relative_to(ROOT)}")
        frozen_payload = json.loads(OUT.read_text(encoding="utf-8"))
        if frozen_payload != payload:
            raise SystemExit(
                f"Frozen fixture differs from regenerated values: {OUT.relative_to(ROOT)}. "
                "Inspect the change, then run without --check to update it."
            )
        if not MLDSA_CROSS_ENGINE_OUT.is_file():
            raise SystemExit(f"Frozen ML-DSA cross-engine fixture is missing: {MLDSA_CROSS_ENGINE_OUT.relative_to(ROOT)}")
        frozen_cross_engine_payload = json.loads(MLDSA_CROSS_ENGINE_OUT.read_text(encoding="utf-8"))
        if frozen_cross_engine_payload != mldsa_cross_engine_payload:
            raise SystemExit(
                "Frozen ML-DSA cross-engine fixture differs from regenerated values: "
                f"{MLDSA_CROSS_ENGINE_OUT.relative_to(ROOT)}. Inspect the change, then run without --check to update it."
            )
        print(f"Verified {OUT.relative_to(ROOT)}")
        print(f"Verified {MLDSA_CROSS_ENGINE_OUT.relative_to(ROOT)}")
    else:
        OUT.parent.mkdir(parents=True, exist_ok=True)
        OUT.write_text(json.dumps(payload, indent=2) + os.linesep, encoding="utf-8")
        MLDSA_CROSS_ENGINE_OUT.parent.mkdir(parents=True, exist_ok=True)
        MLDSA_CROSS_ENGINE_OUT.write_text(json.dumps(mldsa_cross_engine_payload, indent=2) + os.linesep, encoding="utf-8")
        print(f"Wrote {OUT.relative_to(ROOT)}")
        print(f"Wrote {MLDSA_CROSS_ENGINE_OUT.relative_to(ROOT)}")
    print(json.dumps(vectors, indent=2))


if __name__ == "__main__":
    main()
