"""Refresh wiki screenshots from a completed 26.3 client gametest, locally or in CI."""
import argparse
from pathlib import Path
import shutil


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--screenshots", required=True, type=Path)
    args = parser.parse_args()
    destination = Path(__file__).resolve().parents[1] / "docs" / "images"
    names = {
        "notifymod_phase4_trigger_preview": "phase4-trigger-preview.png",
        "notifymod_phase4_after_respawn": "phase4-after-respawn.png",
    }
    # Validate the complete input before changing either checked-in asset.
    selected = {}
    for source, target in names.items():
        matches = sorted(args.screenshots.glob(f"*_{source}.png"))
        if len(matches) != 1 or not matches[0].is_file():
            parser.error(f"Expected exactly one screenshot for {source}; found {len(matches)}")
        with matches[0].open("rb") as stream:
            if stream.read(8) != b"\x89PNG\r\n\x1a\n":
                parser.error(f"Invalid PNG: {matches[0]}")
        selected[target] = matches[0]
    destination.mkdir(parents=True, exist_ok=True)
    for target, source in selected.items():
        shutil.copyfile(source, destination / target)
        print(f"Refreshed docs/images/{target}")


if __name__ == "__main__":
    main()
