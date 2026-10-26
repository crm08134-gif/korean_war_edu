"""사건 카드 사진을 앱에 실을 크기로 줄인다.

긴 변을 1200px로 줄이고(작으면 그대로) JPEG 품질 82로 저장한다. EXIF 등 메타데이터는 버린다.
원본은 저장소에 넣지 않는다. 결과만 data/photos/에 두고 photos.csv에 한 줄을 적는다.

    python scripts/photos/resize.py <원본 파일> data/photos/<파일 이름>.jpg
"""

import argparse
from pathlib import Path

from PIL import Image

LONG_SIDE = 1200
QUALITY = 82


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", type=Path, help="받은 원본 이미지")
    parser.add_argument("target", type=Path, help="data/photos/<파일 이름>.jpg")
    args = parser.parse_args()

    if args.target.suffix.lower() != ".jpg":
        parser.error("결과 파일은 .jpg로 저장한다")

    with Image.open(args.source) as image:
        original = image.size
        image = image.convert("RGB")
        image.thumbnail((LONG_SIDE, LONG_SIDE), Image.Resampling.LANCZOS)
        image.save(args.target, "JPEG", quality=QUALITY, optimize=True, progressive=True)

    changed = "크기 조정" if image.size != original else "변경 없음(형식만 JPEG)"
    print(f"{args.target}: {original[0]}x{original[1]} → {image.size[0]}x{image.size[1]} ({changed})")


if __name__ == "__main__":
    main()
