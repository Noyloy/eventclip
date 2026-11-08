#!/usr/bin/env python3
"""Future independent FFmpeg decode of SYNTHETIC product-output only. Never a native-test pass."""
import argparse,subprocess,hashlib,json,array,sys,math
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('synthetic_evidence_directory',type=Path);a=p.parse_args();root=a.synthetic_evidence_directory
report=json.loads((root/'metrics.json').read_text());assert report['syntheticOnly'] is True
mp4=root/'product-output.mp4';assert hashlib.sha256(mp4.read_bytes()).hexdigest()==report['outputSha256']
out=root/'independent-host-decoded.f32le'
subprocess.run(['ffmpeg','-nostdin','-y','-loglevel','error','-i',str(mp4),'-vn','-ar','48000','-ac','2','-f','f32le',str(out)],check=True)
samples=array.array('f');samples.frombytes(out.read_bytes());
if sys.byteorder!='little':samples.byteswap()
metrics={'scope':'Independent HOST decode of actual synthetic Android product export; not native or real-speech acceptance','frames':len(samples)//2,'sampleRate':48000,'channels':2,'samplePeak':max(abs(x) for x in samples),'samplesAtOrAboveFullscale':sum(abs(x)>=1 for x in samples),'finite':all(math.isfinite(x) for x in samples),'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'nativeReportedStatus':report['status'],'nativeDecodedSamplePeak':report.get('decodedSamplePeak'),'nativeDecodedFrames':report.get('outputDecode',{}).get('frames'),'AACTruePeakSafetyProven':False}
(root/'independent-host-metrics.json').write_text(json.dumps(metrics,indent=2));print(json.dumps(metrics,indent=2))
