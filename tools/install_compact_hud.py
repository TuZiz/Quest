"""Install generated artwork and editable Xerr layouts, outside the Quest JAR.

Usage: python tools/install_compact_hud.py <server plugins/Xerr>
Reads design/quest/hud-redesign-source.png; backs up every replaced file.
Subsequent position/size edits live in design/quest/布局.yml, then rerun this tool.
Does not execute server commands or access quest/player data.
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
args=parser.parse_args()
root=args.xerr
master=root/'design/quest/布局.yml'
default="""schema-version: 1
# 坐标/大小是 Minecraft GUI 单位，随原版 GUI 缩放；不是屏幕物理像素。
right: 6
top: 2
tracker-width: 144
tracker-height: 64
notice-width: 160
notice-height: 44
# 通知顶部自动等于追踪顶部 + 高度 + 间距；隐藏追踪时保持此位置。
gap: 10
background-alpha: 220
"""
settings=yaml.safe_load(master.read_text(encoding='utf-8') if master.exists() else default)
assert settings['schema-version']==1,'Unsupported server HUD design schema'
for key in ['tracker-width','notice-width']:
    assert 128<=settings[key]<=256,f'{master}:{key} must be128..256'
assert 64<=settings['tracker-height']<=120
assert 44<=settings['notice-height']<=85
assert 0<=settings['right']<=40 and 0<=settings['top']<=80
assert 8<=settings['gap']<=60 and 96<=settings['background-alpha']<=255
art=Image.open(root/'design/quest/hud-redesign-source.png').convert('RGBA')
assert art.size==(1024,1024),'Expected original generated1024 sprite sheet'
outputs={}
def put(path,data): outputs[root/path]=data.encode('utf-8') if isinstance(data,str) else data
def png(path,im):
    stream=io.BytesIO();im.save(stream,format='PNG');put(path,stream.getvalue())
class Quoted(yaml.SafeDumper): pass
Quoted.add_representer(str,lambda dumper,value:dumper.represent_scalar('tag:yaml.org,2002:str',value,style="'"))
def dump(path,data):
    put(path,'# Quest 紧凑HUD；仅服务端配置，坐标为GUI单位。\n'+yaml.dump(data,Dumper=Quoted,allow_unicode=True,sort_keys=False,default_flow_style=False))
if not master.exists(): put('design/quest/布局.yml',default)
for name,box,w,h in [('tracker',(48,184,978,368),settings['tracker-width'],settings['tracker-height']),
                     ('notification',(48,645,978,830),settings['notice-width'],settings['notice-height'])]:
    im=art.crop(box).resize((w,h),Image.Resampling.NEAREST)
    im.putalpha(im.getchannel('A').point(lambda a:min(a,settings['background-alpha'])))
    png(f'core/assets/image/quest/panels/textures/{name}.png',im)
dump('core/assets/image/quest/panels/config.yml',{'img':{name:{'path':name,'height':settings[key]} for name,key in [('tracker','tracker-height'),('notification','notice-height')]}})
# Small geometric pixel icons and progress sprites are code-native UI primitives.
for name,color,kind in [('active','#82B7EF','diamond'),('complete','#88D3A0','tick'),('chapter-complete','#E8C477','diamond'),('next','#82B7EF','diamond')]:
    im=Image.new('RGBA',(12,12));d=ImageDraw.Draw(im)
    if kind=='tick':d.line([(1,6),(4,9),(10,2)],fill=color,width=2)
    else:d.polygon([(6,0),(8,4),(11,6),(8,8),(6,11),(4,8),(1,6),(4,4)],fill=color)
    png(f'core/assets/image/quest/icons/textures/{name}.png',im)
dump('core/assets/image/quest/icons/config.yml',{'img':{name:{'path':name,'height':12} for name in ['active','complete','chapter-complete','next']}})
# Keep the left text inset; filled track reaches the two-unit right inner border.
width=settings['tracker-width']-14
for name,color in [('empty','#414C57'),('full','#75D6BB')]:
    im=Image.new('RGBA',(width,3),color);png(f'core/assets/image/quest/bars/textures/{name}.png',im)
dump('core/assets/image/quest/bars/config.yml',{'img':{name:{'path':name,'height':3} for name in ['empty','full']}})
anchor={'x':100,'y':0}
left=-settings['right']-settings['tracker-width'];top=settings['top']
def element(x,y,**content):return {'x':x,'y':-y,'anchor':dict(anchor),**content}
def image(name,group):return {'id':name,'namespace':'quest','basename':group}
tracker={
    'panel':element(left,top,image=image('tracker','panels')),
    'chapter':element(left+12,top+6,layer=10,value='<#DDB66D>{{ get chapter }}',align='left'),
    'task':element(left+12,top+22,layer=10,value='<#EEF3F5>{{ get task }}',align='left'),
    'objective':element(left+12,top+38,layer=10,value='<#A6B2C0>{{ get objective }}',align='left'),
    'progress':element(-settings['right']-12,top+38,layer=10,value='<#75D6BB>{{ get current }}/{{ get target }}',align='right'),
    'bar':element(left+12,top+settings['tracker-height']-8,layer=10,condition="check get show_bar == 'true'",progress={
        'namespace':'quest','basename':'bars','variables':{'units':1,'current':'get current','total':'get target'},
        'image':{'empty':'empty','full':'full'},'fill':{'direction':'left-to-right','steps':'40'}})
}
dump('core/layout/quest_tracker.yml',{'defaults':{'font':'ui'},'quest_tracker':tracker})
compact={
    'icon':element(left,top,layer=10,image=image('chapter-complete','icons')),
    'task':element(left+14,top,layer=10,value='<#EEF3F5>{{ get compact_task }}',align='left'),
    'progress':element(-settings['right'],top,layer=10,value='<#75D6BB>{{ get current }}/{{ get target }}',align='right')}
dump('core/layout/quest_tracker_compact.yml',{'defaults':{'font':'ui'},'quest_tracker_compact':compact})
notice_top=top+settings['tracker-height']+settings['gap']
left=-settings['right']-settings['notice-width']
layouts={'defaults':{'font':'ui'}}
for event,color,icon in [('task-start','#82B7EF','active'),('task-complete','#88D3A0','complete'),('chapter-start','#DDB66D','chapter-complete'),('chapter-complete','#E8C477','chapter-complete')]:
    layouts['quest_notice_'+event]={
        'panel':element(left,notice_top,image=image('notification','panels')),
        'icon':element(left+8,notice_top+15,layer=10,image=image(icon,'icons')),
        'heading':element(left+28,notice_top+8,layer=10,value=f'<{color}>{{{{ get heading }}}}',align='left'),
        'detail':element(left+28,notice_top+25,layer=10,value='<#A6B2C0>{{ get detail }}',align='left')}
dump('core/layout/quest_notifications.yml',layouts)
# Keep the group for compatibility, but retire its layouts: Quest owns a single shared HUD slot.
# This prevents old popup IDs from independently stacking over the new notice surface.
put('core/display/popups/quest.yml','# Quest 通知已由插件统一队列和单HUD槽位管理；保留空文件避免导出默认示例。\n')
changed={p:data for p,data in outputs.items() if not p.exists() or p.read_bytes()!=data}
backup=root/'backups'/('compact-hud-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
for path in changed:
    if path.exists():
        dest=backup/path.relative_to(root);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dest)
for path,data in changed.items():
    path.parent.mkdir(parents=True,exist_ok=True);temp=path.with_name(path.name+'.quest-tmp');temp.write_bytes(data);os.replace(temp,path)
print(f'Installed {len(changed)} server-only HUD files; backup: {backup}')
print(f'GUI bounds: tracker right={settings["right"]}, top={top}, {settings["tracker-width"]}x{settings["tracker-height"]}; notice top={notice_top}, {settings["notice-width"]}x{settings["notice-height"]}')
