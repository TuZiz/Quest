"""Install text-first sidebar layouts in server Xerr only; preserve unrelated assets.

python tools/install_sidebar_hud.py <plugins/Xerr>
Edit design/quest/任务栏.yml, then rerun. No server commands or player data access.
"""
from pathlib import Path
import argparse
import datetime
import io
import os
import shutil
from PIL import Image
import yaml

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('xerr',type=Path)
root=parser.parse_args().xerr
master=root/'design/quest/任务栏.yml'
default="""schema-version: 1
# Minecraft GUI单位，随GUI缩放；相对于屏幕右侧中线，非物理像素。
right: 6
# 顶部在中线上方72单位，接近原版计分板位置。
top: -72
width: 180
height: 118
# 通知位置=任务栏顶部+高度+间距，避免两个HUD互相遮挡。
gap: 12
notice-height: 40
# 纯色背景透明度0..255，不使用旧装饰面板；追踪背景可在Quest/显示.yml关闭。
background-alpha: 100
"""
s=yaml.safe_load(master.read_text(encoding='utf-8') if master.exists() else default)
assert s['schema-version']==1, f'{master}: unsupported schema-version'
for key,low,high in [('right',0,40),('top',-100,0),('width',180,220),('height',118,150),
                     ('gap',12,40),('notice-height',40,60),('background-alpha',0,255)]:
    assert type(s[key]) is int and low<=s[key]<=high, f'{master}:{key} expected {low}..{high}'
outputs={}
def put(path,value): outputs[root/path]=value.encode('utf-8') if isinstance(value,str) else value
class Quoted(yaml.SafeDumper): pass
Quoted.add_representer(str,lambda d,v:d.represent_scalar('tag:yaml.org,2002:str',v,style="'"))
def dump(path,value):
    put(path,'# Quest文字任务栏；锚点在右侧中线，正Y向上。\n'+yaml.dump(value,Dumper=Quoted,allow_unicode=True,sort_keys=False,default_flow_style=False))
if not master.exists(): put('design/quest/任务栏.yml',default)
for name,height in [('background',s['height']),('notice',s['notice-height'])]:
    bitmap=Image.new('RGBA',(s['width'],height),(20,24,29,s['background-alpha']))
    stream=io.BytesIO();bitmap.save(stream,format='PNG')
    put(f'core/assets/image/quest/sidebar/textures/{name}.png',stream.getvalue())
dump('core/assets/image/quest/sidebar/config.yml',{'img':{
    'background':{'path':'background','height':s['height']},'notice':{'path':'notice','height':s['notice-height']}}})
left=-s['right']-s['width']
top=s['top']
def element(x,y,**content): return {'x':x,'y':-y,'anchor':{'x':100,'y':50},**content}
def label(y,value,**extra):return element(left+10,top+y,layer=10,value=value,align='left',**extra)
sidebar={
    'background':element(left,top,condition="check get show_background == 'true'",
                         image={'id':'background','namespace':'quest','basename':'sidebar'}),
    'heading':label(6,'<#DDB66D>主线任务 <#66707A>────────'),
    'chapter':label(23,'<#DDB66D>{{ get chapter }}'),
    'goal-label':label(45,'<#A6B2C0>▸ 当前目标'),
    'goal':label(62,'<#EEF3F5>{{ get goal }}'),
    'goal-more':label(76,'<#EEF3F5>{{ get goal_more }}'),
    'count':label(92,'<#A6B2C0>获取进度：<#75D6BB>{{ get current }} / {{ get target }}'),
    'bar':label(106,'<#75D6BB>{{ get bar_full }}<#414C57>{{ get bar_empty }} <#A6B2C0>{{ get percent }}%',
                condition="check get show_bar == 'true'")}
dump('core/layout/quest_sidebar.yml',{'defaults':{'font':'ui'},'quest_sidebar':sidebar})
notices={'defaults':{'font':'ui'}}
notice_top=top+s['height']+s['gap']
for event,color,icon in [('task-start','#82B7EF','active'),('task-complete','#88D3A0','complete'),
                         ('chapter-start','#DDB66D','chapter-complete'),('chapter-complete','#E8C477','chapter-complete')]:
    notices['quest_notice_'+event]={
        'panel':element(left,notice_top,image={'id':'notice','namespace':'quest','basename':'sidebar'}),
        'icon':element(left+7,notice_top+6,layer=10,image={'id':icon,'namespace':'quest','basename':'icons'}),
        'heading':element(left+25,notice_top+6,layer=10,value=f'<{color}>{{{{ get heading }}}}',align='left'),
        'detail':element(left+10,notice_top+23,layer=10,value='<#A6B2C0>{{ get detail }}',align='left')}
dump('core/layout/quest_notifications.yml',notices)
# Validate all YAML before modifying anything; replacements are individually atomic and backed up.
for path,data in outputs.items():
    if path.suffix=='.yml':yaml.safe_load(data.decode('utf-8'))
changed={p:v for p,v in outputs.items() if not p.exists() or p.read_bytes()!=v}
backup=root/'backups'/('sidebar-hud-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
for path in changed:
    if path.exists():
        dest=backup/path.relative_to(root);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dest)
for path,data in changed.items():
    path.parent.mkdir(parents=True,exist_ok=True)
    temp=path.with_name(path.name+'.quest-tmp');temp.write_bytes(data);os.replace(temp,path)
print(f'Installed {len(changed)} server-only files; backup: {backup}')
print(f'Sidebar {s["width"]}x{s["height"]}, anchor 100/50, right {s["right"]}, top {top}; notice top {notice_top}')
