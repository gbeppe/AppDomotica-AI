"""Generate TV-only telemetry patch against a verified full flow export."""
import copy,hashlib,json,pathlib,sys
root=pathlib.Path(__file__).resolve().parent
raw=pathlib.Path(sys.argv[1]).read_bytes();by={n['id']:n for n in json.loads(raw)}
f=copy.deepcopy(by['ea8307d48995ca93']);assert f['outputs']==24
assert f['func'].strip().endswith('return outputs;') and 'outputs[23]' in f['func']
original=[copy.deepcopy(f)];pub='aa764cb4ce617210';assert pub not in by
assert by['3ed6302a.5af928']['broker']=='192.168.1.20'
f['func']=f['func'].replace('return outputs;',"""// Carry provenance only along the verified TV path.
if (device === 'tvlamp' && outputs[23]) {
    outputs[routingTable[device][payload]]._semantic_command = JSON.parse(outputs[23][0].payload);
}
return outputs;""")
updates=[f];(root/'function17.js').write_text(f['func'])
for id,value in [('8f921a5e.1766f','on'),('ed8c7e3e.e2c32','off')]:
 n=copy.deepcopy(by[id]);assert n['func'].strip()==f'msg.payload = "{value}";\nreturn msg;'
 assert n['wires']==[['7ef048a0.7bf07']]
 original.append(copy.deepcopy(n));n['outputs']=2;n['wires'].append([pub])
 n['func']=(root/'report-template.js').read_text().replace('EXPECTED_STATE',str(value=='on').lower()).replace('LEGACY_VALUE',value)
 (root/f'report-{value}.js').write_text(n['func']);updates.append(n)
updates.append({'id':pub,'type':'mqtt out','z':by['8f921a5e.1766f']['z'],'name':'TV semantic receipt only','topic':'','qos':'','retain':'','broker':'3ed6302a.5af928','x':1100,'y':1700,'wires':[]})
for name,data in [('update.json',updates),('rollback.json',original),('publisher-only.json',[updates[-1]])]: (root/name).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
(root/'manifest.json').write_text(json.dumps({'base_sha256':hashlib.sha256(raw).hexdigest(),'modified_ids':[n['id'] for n in original],'added_id':pub},indent=2)+'\n')
