#!/usr/bin/env python3
"""Mandatory packaging gate for a future private real-media APK; does not build/install one."""
import os,sys,subprocess,re,xml.etree.ElementTree as ET
from pathlib import Path
blocked={'INTERNET','READ_MEDIA_IMAGES','READ_MEDIA_VIDEO','READ_MEDIA_AUDIO','READ_EXTERNAL_STORAGE','WRITE_EXTERNAL_STORAGE','MANAGE_EXTERNAL_STORAGE'}
path=Path(sys.argv[1]);assert path.is_file()
aapt=str(Path(os.environ.get('ANDROID_HOME', str(Path(__file__).resolve().parents[1]/'.tools/android-sdk')))/'build-tools/37.0.0/aapt')
s='\n'.join(n.attrib.get('{http://schemas.android.com/apk/res/android}name','') for n in ET.parse(path).getroot() if n.tag.startswith('uses-permission')) if path.suffix=='.xml' else subprocess.check_output([aapt,'dump','permissions',str(path)],text=True)
perms=set(re.findall(r"android.permission.([A-Z_]+)",s))
assert not (perms & blocked),f'Forbidden permissions: {perms & blocked}'
print('PASS '+('merged manifest' if path.suffix=='.xml' else 'final APK')+' forbidden-permission audit:',path.name)
