"""Compare an authenticated ledger receipt without the business database.

python scripts/verify-ledger-receipt.py receipt.json --file original.pdf
python scripts/verify-ledger-receipt.py receipt.json --business-hash HEX_SHA256
The original file remains on this computer; no upload is performed.
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("receipt", type=Path)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--file", type=Path)
    group.add_argument("--business-hash")
    args = parser.parse_args()
    try:
        receipt = json.loads(args.receipt.read_text(encoding="utf-8-sig"))
        if not receipt:
            print(json.dumps({"outcome": "NOT_FOUND"}))
            return 2
        if not isinstance(receipt, dict):
            raise TypeError("Expected a ledger evidence object")
        if args.file:
            digest = hashlib.sha256()
            with args.file.open("rb") as original:
                for chunk in iter(lambda: original.read(1024 * 1024), b""):
                    digest.update(chunk)
            provided, stored = digest.hexdigest(), receipt.get("fileHash")
        else:
            provided, stored = args.business_hash.lower(), receipt.get("dataHash")
        if not stored or not re.fullmatch(r"[0-9a-fA-F]{64}", stored):
            print(json.dumps({"outcome": "NOT_FOUND", "message": "Receipt has no requested digest"}))
            return 2
        if not re.fullmatch(r"[0-9a-f]{64}", provided):
            raise ValueError("Expected 64 hexadecimal SHA-256 characters")
        match = provided == stored.lower()
        print(json.dumps({"outcome": "MATCH" if match else "MISMATCH", "txId": receipt.get("txId"), "providedHash": provided, "storedHash": stored}))
        return 0 if match else 1
    except (OSError, ValueError, TypeError) as error:
        print(json.dumps({"outcome": "UNAVAILABLE", "errorType": type(error).__name__}))
        return 3


if __name__ == "__main__":
    sys.exit(main())
