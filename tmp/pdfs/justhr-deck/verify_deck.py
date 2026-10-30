from pathlib import Path
import hashlib
import pdfplumber
from pypdf import PdfReader
root=Path('C:/workspace/JustHumanResource')
a=PdfReader(root/'tmp/pdfs/justhr-deck/original.pdf');b=PdfReader(root/'doc/JUSTHR_Buyer_Sales_Deck.pdf')
def hashes(reader):
    return {hashlib.sha256(img.data).hexdigest() for p in reader.pages for img in p.images}
assert hashes(a)==hashes(b),'An original screenshot was changed or lost'
with pdfplumber.open(root/'doc/JUSTHR_Buyer_Sales_Deck.pdf') as pdf:
    for i,p in enumerate(pdf.pages):
        assert p.chars, f'No searchable text on page {i+1}'
        # Ignore tiny original sub-point boundary differences at the page edge.
        bad=[c for c in p.chars if c['x0'] < -1 or c['x1'] > p.width+1 or c['top'] < -1 or c['bottom'] > p.height+1]
        assert not bad,(i+1,[(c['text'],c['x0'],c['top']) for c in bad[:5]])
print(f'Checked {len(b.pages)} pages: searchable text, page bounds, and all {len(hashes(a))} original screenshot/image assets preserved.')
print('Revised PDF bytes:',(root/'doc/JUSTHR_Buyer_Sales_Deck.pdf').stat().st_size)
