"""Prepare the locally hosted, illustrative photographs and OFL typefaces.

Run manually with Python + Pillow. This is not needed for npm install/build.
Photo source pages and licensing are recorded in public/images/CREDITS.md.
"""
from io import BytesIO
from pathlib import Path
import re
from urllib.request import Request, urlopen
from PIL import Image, ImageOps

PUBLIC = Path(__file__).resolve().parents[1] / 'public'

def download(url):
    request = Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36'})
    with urlopen(request, timeout=40) as response:
        return response.read()

images = PUBLIC / 'images'
images.mkdir(parents=True, exist_ok=True)
for name, photo in [('golden', '2253275'), ('puppy', '1805164'), ('kittens', '45201'), ('friends', '1108099')]:
    filename = 'kitty-cat-kitten-pet-45201' if photo == '45201' else f'pexels-photo-{photo}'
    original = ImageOps.exif_transpose(Image.open(BytesIO(download(
        f'https://images.pexels.com/photos/{photo}/{filename}.jpeg?auto=compress&cs=tinysrgb&w=1500'
    )))).convert('RGB')
    for width in [640, 1280]:
        resized = original.copy()
        resized.thumbnail((width, width * 2), Image.Resampling.LANCZOS)
        target = images / f'{name}-{width}.webp'
        resized.save(target, 'WEBP', quality=82, method=6)
        print(f'{target.name}: {resized.size}, {target.stat().st_size:,} bytes')

fonts = PUBLIC / 'fonts'
fonts.mkdir(parents=True, exist_ok=True)
css = download('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400..700&family=DM+Serif+Display:ital@0;1&display=swap').decode()
for block in re.findall(r'/\* latin \*/\s*(@font-face\s*\{[^}]+\})', css):
    family = re.search(r"font-family: '([^']+)'", block).group(1)
    style = re.search(r'font-style: ([^;]+)', block).group(1)
    url = re.search(r'url\(([^)]+)\)', block).group(1)
    name = 'dm-sans' if family == 'DM Sans' else f'dm-serif-{style}'
    (fonts / f'{name}.woff2').write_bytes(download(url))
    print(f'{name}.woff2')
for family in ['dmsans', 'dmserifdisplay']:
    (fonts / f'{family}-OFL.txt').write_bytes(download(f'https://raw.githubusercontent.com/google/fonts/main/ofl/{family}/OFL.txt'))
