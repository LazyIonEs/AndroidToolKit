from pathlib import Path
import subprocess,zipfile,io,json,hashlib,shutil
from PIL import Image
root=Path('/Users/lazyiones/Desktop/AndroidStudioProjects/AndroidToolKit')
fixture=root/'shared/build/migration/fixtures/phase6-native'
evidence=root/'docs/migration/evidence/phase-06/release'
evidence.mkdir(exist_ok=True,parents=True)
aapt=root/'composeApp/build/migration/distribution/main-release/app/AndroidToolKit.app/Contents/app/resources/aapt2'
aapt_copy=fixture/'packaged-aapt2'
shutil.copyfile(aapt,aapt_copy)
aapt_copy.chmod(0o755)
assert hashlib.sha256(aapt_copy.read_bytes()).digest()==hashlib.sha256(aapt.read_bytes()).digest()
aapt=aapt_copy
jdk=Path('/Users/lazyiones/.gradle/jdks/jetbrains_s_r_o_-21-aarch64-os_x.2/jbrsdk_jcef-21.0.6-osx-aarch64-b895.109/Contents/Home')
cp=(root/'shared/build/migration/phase6-verifier/classpath.txt').read_text().strip()
source=Image.open(fixture/'中文 icon.png').convert('RGBA');results=[]
for filename,policy,label in [('Phase6 空包.apk','UNSIGNED','Phase6 空包'),('Phase6 签名.apk','UNSIGNED','Phase6 签名'),('Phase6 签名-sign.apk','V3','Phase6 签名')]:
 apk=fixture/'output'/filename
 badging=subprocess.check_output([str(aapt),'dump','badging',str(apk)],text=True)
 manifest=subprocess.check_output([str(aapt),'dump','xmltree',str(apk),'--file','AndroidManifest.xml'],text=True)
 for text in ["name='org.fixture.phase6'", "versionCode='12'", "versionName='2.3'", "sdkVersion:'23'", "targetSdkVersion:'32'", "application-label:'"+label+"'"]:
  assert text in badging,(filename,text)
 icons=[]
 with zipfile.ZipFile(apk) as z:
  for name in z.namelist():
   if name.startswith('res/mipmap-') and name.endswith('/ic_launcher.png'):
    payload=z.read(name);icon=Image.open(io.BytesIO(payload)).convert('RGBA')
    assert icon.size==(37,29) and icon.tobytes()==source.tobytes(),name
    icons.append({'entry':name,'dimensions':list(icon.size),'sha256':hashlib.sha256(payload).hexdigest()})
 assert len(icons)==5,icons
 verified=subprocess.check_output([str(jdk/'bin/java'),'-cp',cp,'migration.smoke.VerifyApkSignature',policy,str(apk)],text=True)
 (evidence/(filename+'.badging.txt')).write_text(badging)
 (evidence/(filename+'.manifest.txt')).write_text(manifest)
 results.append({'file':filename,'policy':policy,'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'icons':icons,'verification':verified.strip()})
 print(verified.strip())
(evidence/'apk-artifacts.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
