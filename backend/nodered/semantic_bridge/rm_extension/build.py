import json,pathlib,copy,sys,hashlib
root=pathlib.Path(__file__).resolve().parent
raw=pathlib.Path(sys.argv[1]).read_bytes();by={n['id']:n for n in json.loads(raw)}
f=copy.deepcopy(by['ea8307d48995ca93']);old=[copy.deepcopy(f)]
a="device === 'tvlamp' && outputs[23]";assert f['func'].count(a)==1
f['func']=f['func'].replace(a,"['tvlamp','shelflamp','readinglight'].includes(device) && outputs[23]")
updates=[f];(root/'function17.js').write_text(f['func'])
publisher=by['aa764cb4ce617210'];assert publisher['type']=='mqtt out' and publisher['topic']==''
mapping=[('f7bc8ba8.270b6','on','lights.libreria.power'),('18386049.c296a8','off','lights.libreria.power'),('a082b270d39049dc','on','lights.reading.power'),('1d425ade7f5a82ae','off','lights.reading.power')]
for id,value,entity in mapping:
 n=copy.deepcopy(by[id]);assert n['func'].strip()==f'msg.payload = "{value}";\nreturn msg;'
 assert n['z']==publisher['z']
 old.append(copy.deepcopy(n));n['outputs']=2;n['wires'].append([publisher['id']])
 template=(root.parent/'rm_receipt/report-template.js').read_text()
 n['func']=template.replace('EXPECTED_STATE',str(value=='on').lower()).replace('LEGACY_VALUE',value).replace('lights.tv.power',entity)
 (root/(entity+'.'+value+'.js')).write_text(n['func']);updates.append(n)
for file,obj in [('update.json',updates),('rollback.json',old)]: (root/file).write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n')
(root/'manifest.json').write_text(json.dumps({'base_sha256':hashlib.sha256(raw).hexdigest(),'modified_ids':[n['id'] for n in old],'new_nodes':0},indent=2)+'\n')
