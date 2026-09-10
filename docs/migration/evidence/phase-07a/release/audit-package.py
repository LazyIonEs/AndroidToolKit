from pathlib import Path
import zipfile,hashlib,json,struct
root=Path('/Users/lazyiones/Desktop/AndroidStudioProjects/AndroidToolKit')
app=root/'composeApp/build/migration/distribution/main-release/app/AndroidToolKit.app'
jar=app/'Contents/app/composeApp-jvm-1.6.11.jar'
proguard=root/'composeApp/build/compose/tmp/main-release/proguard/composeApp-jvm-1.6.11.jar'
cargo=(root/'rust/target/release/libtoolkit_rs.dylib').read_bytes()
sha=lambda data:hashlib.sha256(data).hexdigest()
def sections(data):
 assert struct.unpack_from('<I',data)[0]==0xfeedfacf
 pos=32;found=[]
 for _ in range(struct.unpack_from('<I',data,16)[0]):
  cmd,size=struct.unpack_from('<II',data,pos)
  if cmd==0x19:
   for n in range(struct.unpack_from('<I',data,pos+64)[0]):
    off=pos+72+n*80
    name=data[off:off+16].split(b'\0')[0].decode();segment=data[off+16:off+32].split(b'\0')[0].decode()
    count=struct.unpack_from('<Q',data,off+40)[0];offset=struct.unpack_from('<I',data,off+48)[0]
    flags=struct.unpack_from('<I',data,off+64)[0]&255
    if flags in [1,12,18] or not count:continue
    payload=data[offset:offset+count];assert len(payload)==count
    found.append({'segment':segment,'section':name,'size':count,'sha256':sha(payload)})
  pos+=size
 return found
with zipfile.ZipFile(jar) as z:
 native=[name for name in z.namelist() if name.endswith('libuniffi_toolkit.dylib')];assert len(native)==1,native
 delivered=z.read(native[0]);assets=[name for name in z.namelist() if name.startswith('composeResources/') and ('lottie' in name or '/font/' in name or 'aboutlibraries' in name)]
 libs=json.loads(z.read(next(name for name in z.namelist() if name.endswith('/aboutlibraries.json'))))
with zipfile.ZipFile(proguard) as z:
 pg=z.read(native[0])
 apktool_tools=[n for n in z.namelist() if 'aapt' in n and not n.endswith('.class')]
 assert apktool_tools, 'Bundled Apktool build executables missing'
 template_path=res_template=app/'Contents/app/resources/apktool.apk'
 if not template_path.is_file():
  template_path=next((app/'Contents/app/resources').rglob('apktool.apk'))
 template_original=root/'composeApp/resources/common/apktool.apk'
 assert template_path.read_bytes()==template_original.read_bytes()

original=sections(cargo);actual=sections(delivered);assert original==actual
assert pg==cargo
res=app/'Contents/app/resources'
resources=sorted(p.name for p in res.rglob('*') if p.is_file())
for name in ['oppo.apk','vivo.apk','huawei.apk','xiaomi.apk','qq.apk','honor.apk','apktool.apk','aapt2']:assert name in resources,name
out=root/'docs/migration/evidence/phase-07a/release';out.mkdir(parents=True,exist_ok=True)
(out/'package.json').write_text(json.dumps({'jarSha256':sha(jar.read_bytes()),'proguardJarSha256':sha(proguard.read_bytes()),'nativeResources':native,'cargoSha256':sha(cargo),'proguardNativeSha256':sha(pg),'deliveredNativeSha256':sha(delivered),'proguardNativeMatchesCargo':pg==cargo,'allMachOSectionsEqual':original==actual,'machOSections':actual,'composeAssets':assets,'applicationResources':resources,'libraryCount':len(libs['libraries']),'apktoolEmbeddedTools':apktool_tools,'templateSha256':sha(template_path.read_bytes()),'templateMatchesOriginal':True},indent=2)+'\n')
print('PASS: package resources, one Rust library, unchanged ProGuard native payload and',len(actual),'equal Mach-O sections; libraries',len(libs['libraries']))
