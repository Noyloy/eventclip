"""Locally generated non-personal fixtures. Host encode only; no Android execution/proof."""
from pathlib import Path
import subprocess,hashlib,json
root=Path(__file__).parent
commands=[]
def run(name,args):
 cmd=['ffmpeg','-nostdin','-y','-loglevel','error',*args,str(root/name)]
 subprocess.run(cmd,check=True);commands.append({'file':name,'sha256':hashlib.sha256((root/name).read_bytes()).hexdigest(),'command':cmd[:-1]+[name]})
run('photo.png',['-f','lavfi','-i','color=c=navy:s=320x180','-frames:v','1'])
# No edit list: intentional codec priming remains part of this synthetic source. Whether Android
# exposes encoder delay/padding is a TARGET inspection, not inferred from FFmpeg metadata.
run('original-a-44100-mono.mp4',['-f','lavfi','-i','color=c=yellow:s=320x180:r=30:d=2','-f','lavfi','-i','aevalsrc=0.28*sin(2*PI*440*t)*(0.7+0.3*sin(2*PI*4*t)):s=44100:d=2','-c:v','libx264','-pix_fmt','yuv420p','-bf','0','-c:a','aac','-b:a','128k','-use_editlist','0','-shortest'])
run('silent.mp4',['-f','lavfi','-i','color=c=gray:s=320x180:r=30:d=1','-c:v','libx264','-pix_fmt','yuv420p','-bf','0','-an'])
run('original-b-48000-stereo.mp4',['-f','lavfi','-i','color=c=teal:s=180x320:r=30:d=2','-f','lavfi','-i','aevalsrc=0.28*sin(2*PI*660*t)|0.28*sin(2*PI*660*t):s=48000:d=2','-c:v','libx264','-pix_fmt','yuv420p','-bf','0','-c:a','aac','-b:a','192k','-use_editlist','0','-shortest'])
run('music-48000-stereo.wav',['-f','lavfi','-i','aevalsrc=0.28*sin(2*PI*1000*t)|0.28*sin(2*PI*1000*t):s=48000:d=12','-c:a','pcm_s16le'])
# Timestamp fixture kept separate from first positive gate; remux shifts streams by2s without trimming.
run('original-a-sourcepts-plus2s.mp4',['-itsoffset','2','-i',str(root/'original-a-44100-mono.mp4'),'-map','0','-c','copy','-copyts','-use_editlist','0'])
(root/'provenance.json').write_text(json.dumps({'origin':'100% local FFmpeg lavfi color/analytic tones; NO speech/personal media/recordings','proof':'HOST fixture generation only; NOT native decoder/render/audio acceptance','ffmpegVersion':subprocess.check_output(['ffmpeg','-version'],text=True).splitlines()[0],'fixtures':commands},indent=2))
