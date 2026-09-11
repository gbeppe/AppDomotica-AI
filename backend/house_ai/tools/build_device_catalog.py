"""Static extraction of the supplied Router; never executes its JavaScript."""
import argparse
import hashlib
import json
import re
from pathlib import Path

TOKEN = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')

def mask(source, strings=False):
    return TOKEN.sub(lambda m: ''.join('\n' if c == '\n' else ' ' for c in m[0])
                     if strings or m[0].startswith('/') else m[0], source)


def objects(source, name):
    clean = mask(source)
    structure = mask(source, True)
    start = re.search(r'const\s+' + name + r'\s*=\s*\[', structure).end()
    depth = 0
    begin = None
    for index in range(start, len(structure)):
        c = structure[index]
        if c == ']' and depth == 0:
            return
        if c == '{':
            if depth == 0:
                begin = index
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                raw = clean[begin:index+1]
                fields = {m[1]: json.loads(m[2]) for m in re.finditer(
                    r'\b(id|int_stat|int_cmd|leg_stat|leg_cmd|broker|json_path)\s*:\s*("(?:\\.|[^"\\])*")', raw)}
                headings = re.findall(r'//\s*---\s*(.*?)\s*---', source[:begin])
                yield fields, source.count('\n', 0, begin)+1, headings[-1] if headings else name, raw.strip()
    raise ValueError('Unterminated registry')

DOMAIN_SCREENS = {
    'lights': ['LightsScreen', 'MainDashboard'], 'pool': ['PoolScreen', 'MainDashboard'],
    'settings': ['DomoticaSettingsScreen', 'MainDashboard'],
    'ai': ['AiManagedScreen', 'MainDashboard'], 'predictive_reserve': ['AiManagedScreen', 'MainDashboard'],
    'ai_climate': ['MainDashboard'], 'stato_condizionatore': ['AiManagedScreen', 'MainDashboard', 'HvacScreen'],
    'logica_controllo': ['AiManagedScreen', 'MainDashboard'], 'garage': ['GarageControlScreen'],
    'energy': ['MainDashboard', 'AiManagedScreen', 'HvacScreen'],
    'heating': ['HvacScreen', 'MainDashboard'], 'fireplace': ['HvacScreen', 'MainDashboard'],
    'climate': ['HvacScreen', 'AiManagedScreen', 'MainDashboard'],
    'env': ['AmbientiScreen', 'AiManagedScreen', 'MainDashboard'],
    'ventilation': ['HvacScreen', 'AiManagedScreen', 'MainDashboard'],
}


def build(root, export):
    nodes = json.loads(export.read_text())
    routers = [n for n in nodes if 'const registry = [' in n.get('func', '')]
    assert len(routers) == 1
    source = routers[0]['func']
    composite_topic = re.search(r'const COMPOSITE_TOPIC\s*=\s*"([^"]+)"', mask(source))[1]
    entries = {}
    extracted = {}
    for name in ['registry', 'compositeRegistry']:
        extracted[name] = list(objects(source, name))
        for fields, line, group, raw in extracted[name]:
            key = fields.get('int_stat') or fields['int_cmd']
            entry = entries.setdefault(key, {'id': fields.get('id', key.removeprefix('zara/interface/').removesuffix('/stat').replace('/', '.')),
                'domain': key.split('/')[2], 'state_topic': fields.get('int_stat'),
                'command_topic': None, 'sources': []})
            if fields.get('int_cmd'):
                entry['command_topic'] = fields['int_cmd']
            entry['sources'].append({'registry': name, 'group': group if name == 'registry' else 'TOPIC COMPOSITO',
                'router_function_line': line, 'legacy_state_topic': fields.get('leg_stat'),
                'legacy_command_topic': fields.get('leg_cmd'), 'broker_alias_explicit': fields.get('broker'),
                'composite_topic': composite_topic if name == 'compositeRegistry' else None,
                'json_path': fields.get('json_path'), 'declaration': raw})
    scene = 'zara/interface/lights/scene/cmd'
    assert f'if (inTopic === "{scene}")' in source
    entries[scene] = {'id': 'lights.scene', 'domain': 'lights', 'state_topic': None,
        'command_topic': scene, 'sources': [{'registry':'special_scene_bridge',
            'group':'LIGHT SCENES', 'router_function_line': source[:source.index(f'if (inTopic === "{scene}")')].count('\n')+1,
            'allowed_ui_payloads':['tv','sleep','all_on','all_off']} ]}
    screens=[]
    command_refs={}
    for path in sorted((root/'app/src/main/java/com/domopi/app/ui/screens').glob('*.kt')):
        text=path.read_text()
        refs=[]
        for m in re.finditer(r'mqttManager\.publish\("([^"]+)"\s*,\s*([^\n]+)',text):
            topics=[m[1]]
            if '$deviceId' in m[1]:
                topics=[m[1].replace('$deviceId',d) for d in re.findall(r'deviceId\s*=\s*"([^"]+)"',text)]
            for topic in topics:
                ref={'topic':topic,'file':str(path.relative_to(root)),'line':text[:m.start()].count('\n')+1,'payload_expression':m[2].strip()}
                refs.append(ref);command_refs.setdefault(topic,[]).append(ref)
        screens.append({'screen':path.stem,'file':str(path.relative_to(root)),
            'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
            'mqtt_state_flows':sorted(set(re.findall(r'mqttManager\.(\w+)\.collectAsState',text))),
            'direct_command_references':refs,
            'helper_calls':sorted(set(re.findall(r'mqttManager\.(toggleLight|sendLightScene)\(',text)))})
    aliases={'sala':('lights','living'),'libreria':('lights','libreria'),'televisione':('lights','tv'),
        'tavolinolettura':('lights','reading'),'lucecamera':('lights','bedroom'),'lampadahifi':('lights','hifi'),
        'lucipiscina':('pool','water'),'lucipedanapiscina':('pool','deck'),
        'pompapiscina':('pool','pump'),'skimmerpiscina':('pool','skimmer')}
    lights=root/'app/src/main/java/com/domopi/app/ui/screens/LightsScreen.kt'
    for m in re.finditer(r'LightDevice\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)"\)',lights.read_text()):
        domain,device=aliases.get(m[3],('lights',m[3]))
        command_refs.setdefault(f'zara/interface/{domain}/{device}/power/cmd',[]).append({'file':str(lights.relative_to(root)),'helper':'toggleLight','android_id':m[3],'label':m[2]})
    for android,(domain,device) in aliases.items():
        if domain=='pool':
            command_refs.setdefault(f'zara/interface/{domain}/{device}/power/cmd',[]).append({'file':'app/src/main/java/com/domopi/app/ui/screens/PoolScreen.kt','helper':'toggleLight','android_id':android})
    command_refs[scene]=[{'file':str(lights.relative_to(root)),'helper':'sendLightScene','payloads':['tv','sleep','all_on','all_off']}]
    for e in entries.values():
        e['access_in_router']='read_write' if e['command_topic'] and e['state_topic'] else 'command_only' if e['command_topic'] else 'read_only'
        e['lifecycle']='obsolete_per_runtime_contract' if e['id'].startswith('heating.heat_pump.') else 'declared_in_export'
        e['android_domain_screens']=DOMAIN_SCREENS.get(e['domain'],[])
        e['android_command_references']=command_refs.get(e['command_topic'],[])
        e['physical_confirmation']='not_verified'
        e['live_validation']='not_performed_in_this_catalog_audit'
    known={e['command_topic'] for e in entries.values() if e['command_topic']}
    gaps=[{'topic':t,'references':r,'status':'android_command_without_exported_router_route'} for t,r in command_refs.items() if t not in known]
    return {'schema':'house_ai.device_catalog.v1','scope':'static_app_and_supplied_router_export',
        'router_export':export.name,'router_sha256':hashlib.sha256(export.read_bytes()).hexdigest(),
        'router_node_id':routers[0]['id'],'composite_topic':composite_topic,
        'extraction_counts':{k:len(v) for k,v in extracted.items()},
        'screens':screens,'entries':list(entries.values()),'gaps':gaps,
        'non_mqtt_resources':[
            {'screen':'CamerasScreen','access':'read_only','transport':'HTTP MJPEG TinyCam',
             'resources':[{'label':'Ingresso','camera_id':'936942165'},{'label':'Soggiorno','camera_id':'1708386743'}],
             'endpoint_template':'/axis-cgi/mjpg/video.cgi?cameraId={id}'},
            {'screen':'EnergyDetailScreen','access':'read_only','transport':'HTTP EmonCMS',
             'feeds':{'solar':307,'consumption':303,'grid':305,'battery':306,'soc':304},
             'source':'app/src/main/java/com/domopi/app/data/EnergyRepository.kt'},
            {'screen':'HouseAiScreen','access':'read_only','transport':'HTTP house_ai',
             'endpoint_template':'/v1/report?day={day}','feeds':[353,304,306]},
            {'screen':'ConfigurationScreen','access':'local_settings_write','transport':'Android DataStore',
             'note':'Connection settings, theme and expert PIN; no MQTT device command.'},
            {'screen':'DiagnosisScreen','access':'read_only','transport':'local_connection_diagnostics',
             'note':'Connection mode, message rate and traffic log, not a physical device.'}],
        'notes':['Entries are capabilities, not a count of physical devices.',
                 'android_domain_screens identifies domain consumers, not proof that every field is displayed.',
                 'Router declarations preserve exact transforms as inert text, not executable rules.',
                 'Command availability is not authorization to execute it.']}


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--router',type=Path,required=True)
    parser.add_argument('--root',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    data=build(args.root,args.router)
    args.output.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'entries':len(data['entries']),'screens':len(data['screens']),'extraction':data['extraction_counts'],'gaps':[g['topic'] for g in data['gaps']]}))

if __name__=='__main__':main()
