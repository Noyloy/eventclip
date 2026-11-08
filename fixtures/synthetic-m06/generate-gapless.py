from pathlib import Path
import wave,struct,subprocess,json,hashlib
r=Path(__file__).parent
rate=44100;n=rate
with wave.open(str(r/'impulses-reference.wav'),'wb') as w:
 w.setparams((1,2,rate,n,'NONE','not compressed'));w.writeframes(b''.join(struct.pack('<h',26000 if i in [4410,39690] else 0) for i in range(n)))
entries=[]
for name,codec,extra in [('impulses-lame.mp3','libmp3lame',['-b:a','192k']),('impulses-aac.m4a','aac',['-b:a','192k'])]:
 cmd=['ffmpeg','-nostdin','-y','-loglevel','error','-i',str(r/'impulses-reference.wav'),'-c:a',codec,*extra,str(r/name)];subprocess.run(cmd,check=True)
 probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_packets','-of','json',str(r/name)],text=True))
 entries.append({'file':name,'codec':codec,'sha256':hashlib.sha256((r/name).read_bytes()).hexdigest(),'hostEncoderPacketSideDataNotAndroidProof':[{'pts':p.get('pts'),'duration':p.get('duration'),'side_data_list':p.get('side_data_list')} for p in probe['packets'] if p.get('side_data_list')],'command':cmd})
(r/'gapless-provenance.json').write_text(json.dumps({'input':'analytic single-sample impulses; no media/speech','sampleRate':rate,'channels':1,'frames':n,'impulseFrames':[4410,39690],'referenceSha256':hashlib.sha256((r/'impulses-reference.wav').read_bytes()).hexdigest(),'interpretation':'Host encoder metadata only. Android decoder trimming/PTS not established; guard unchanged. Native raw probe test must report frame counts/first-last samples and peaks; no automatic product trim.','encodings':entries},indent=2))
