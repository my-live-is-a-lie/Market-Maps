import base64
from pathlib import Path
data = base64.b64decode(open(__file__).read().split('B64_START\n',1)[1].split('\nB64_END',1)[0].replace('\n',''))
Path('app/src/main/java/com/marketmaps/app/ui/map/GoogleMapContent.kt').write_bytes(data)
print('Wrote', len(data))
# B64_START
# B64_END
