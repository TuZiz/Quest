"""Install the approved opaque completion card in server Xerr; never edit quest data.

First run: python tools/install_completion_hud.py <plugins/Xerr> --reference <approved-preview.png>
Later: edit design/quest/完成通知.yml and rerun without --reference.
Only Quest notification assets/layouts are replaced; sidebar resources remain untouched.
"""
from pathlib import Path
import argparse
import datetime
import io
import os
import shutil
from PIL import Image, ImageDraw
import yaml

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('xerr',type=Path)
parser.add_argument('--reference',type=Path)
args=parser.parse_args()
root=args.xerr
master=root/'design/quest/完成通知.yml'
source=root/'design/quest/completion-approved-source.png'
default="""schema-version: 1
# 屏幕右上锚点，坐标为Minecraft GUI单位，随原版GUI缩放。
right: 0
top: 2
width: 220
height: 44
# 左侧图标及文字内距；字体继续使用现有12px中文字体。
icon-size: 28
text-inset: 46
"""
s=yaml.safe_load(master.read_text(encoding='utf-8') if master.exists() else default)
assert s['schema-version']==1,f'{master}: unsupported schema-version'
for key,low,high in [('right',0,40),('top',0,80),('width',220,240),('height',44,64),
                     ('icon-size',24,28),('text-inset',44,46)]:
    assert type(s[key]) is int and low<=s[key]<=high,f'{master}:{key} expected {low}..{high}'
reference=args.reference if args.reference else source
assert reference.is_file(),f'{reference}: approved preview required on first installation'
art=Image.open(reference).convert('RGBA')
# Extract the green check tile from the approved concept, excluding its baked text.
region=art.crop((0,int(art.height*.25),int(art.width/3),int(art.height*.70)))
mask=Image.new('L',region.size)
mask.putdata([255 if g>70 and g>r*1.25 and g>b*1.3 else 0 for r,g,b,a in region.getdata()])
box=mask.getbbox()
assert box and box[2]-box[0]>40 and box[3]-box[1]>40,'Approved preview: check tile not found'
icon=region.crop((max(0,box[0]-2),max(0,box[1]-2),min(region.width,box[2]+2),min(region.height,box[3]+2)))
icon=icon.resize((s['icon-size'],s['icon-size']),Image.Resampling.NEAREST)
icon.putalpha(255)
panel=Image.new('RGBA',(s['width'],s['height']),(15,15,14,255))
draw=ImageDraw.Draw(panel)
# Reconstruct the thin native-size bevel; no preview text is baked into the card.
draw.rectangle((1,1,s['width']-2,s['height']-2),fill=(76,76,73,255))
draw.rectangle((2,2,s['width']-3,s['height']-3),fill=(43,42,41,255))
draw.line((3,3,s['width']-4,3),fill=(91,90,86,255))
draw.line((3,4,3,s['height']-4),fill=(62,61,58,255))
draw.line((3,s['height']-4,s['width']-4,s['height']-4),fill=(28,28,27,255))
outputs={}
def put(path,data):outputs[root/path]=data.encode('utf-8') if isinstance(data,str) else data
def png(path,bitmap):
    stream=io.BytesIO();bitmap.save(stream,format='PNG');put(path,stream.getvalue())
class Quoted(yaml.SafeDumper):pass
Quoted.add_representer(str,lambda d,v:d.represent_scalar('tag:yaml.org,2002:str',v,style="'"))
def dump(path,value):put(path,'# Quest完成卡片；独立右上锚点，正Y向上。\n'+yaml.dump(value,Dumper=Quoted,allow_unicode=True,sort_keys=False,default_flow_style=False))
if not master.exists():put('design/quest/完成通知.yml',default)
if args.reference:put('design/quest/completion-approved-source.png',args.reference.read_bytes())
png('core/assets/image/quest/completion/textures/panel.png',panel)
png('core/assets/image/quest/completion/textures/check.png',icon)
dump('core/assets/image/quest/completion/config.yml',{'img':{
    'panel':{'path':'panel','height':s['height']},'check':{'path':'check','height':s['icon-size']}}})
left=-s['right']-s['width']
def element(x,y,**content):return {'x':x,'y':-y,'anchor':{'x':100,'y':0},**content}
layouts={'defaults':{'font':'ui'}}
for event,color in [('task-complete','#94E88B'),('task-start','#82B7EF'),
                     ('chapter-complete','#E8C477'),('chapter-start','#DDB66D')]:
    completed=event.endswith('complete')
    layouts['quest_notice_'+event]={
        'panel':element(left,s['top'],image={'id':'panel','namespace':'quest','basename':'completion'}),
        'icon':element(left+9,s['top']+(s['height']-s['icon-size'])//2,layer=10,
                       image={'id':'check' if completed else 'active','namespace':'quest','basename':'completion' if completed else 'icons'}),
        'heading':element(left+s['text-inset'],s['top']+6,layer=10,value=f'<{color}>{{{{ get heading }}}}',align='left'),
        'detail':element(left+s['text-inset'],s['top']+26,layer=10,value='<#C9C6C0>{{ get detail }}',align='left')}
dump('core/layout/quest_notifications.yml',layouts)
for path,data in outputs.items():
    if path.suffix=='.yml':yaml.safe_load(data.decode('utf-8'))
changed={p:v for p,v in outputs.items() if not p.exists() or p.read_bytes()!=v}
backup=root/'backups'/('completion-hud-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
for path in changed:
    if path.exists():
        dest=backup/path.relative_to(root);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dest)
for path,data in changed.items():
    path.parent.mkdir(parents=True,exist_ok=True)
    temp=path.with_name(path.name+'.quest-tmp');temp.write_bytes(data);os.replace(temp,path)
print(f'Installed {len(changed)} notification files; backup: {backup}')
print(f'Top-right anchor 100/0; right={s["right"]}, top={s["top"]}, card={s["width"]}x{s["height"]}; sidebar unchanged')
