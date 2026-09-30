import pathlib,struct,zlib,random,shutil
root=pathlib.Path('test-native/fixtures');root.mkdir(parents=True,exist_ok=True)
rng=random.Random(42);payload=rng.randbytes(32768)*3+b'RA2 payload end';(root/'expected.bin').write_bytes(payload)
files=struct.pack('<IIHHHH',len(payload),0,0,0,0,0)+b'folder\\RA2.MIX\0';blocks=[]
for start in range(0,len(payload),32768):
 raw=payload[start:start+32768];kw={'zdict':payload[max(0,start-32768):start]} if start else {};c=zlib.compressobj(9,zlib.DEFLATED,-15,**kw);packed=b'CK'+c.compress(raw)+c.flush();blocks.append(struct.pack('<IHH',0,len(packed),len(raw))+packed)
data=b''.join(blocks);offset=44+len(files);header=b'MSCF'+struct.pack('<IIIII',0,offset+len(data),0,44,0)+struct.pack('<BBHHHHH',3,1,1,1,0,0,0)
(root/'Game1.CAB').write_bytes(header+struct.pack('<IHH',offset,len(blocks),1)+files+data)
(root/'bad').mkdir(exist_ok=True);(root/'bad'/'Game1.CAB').write_bytes((root/'Game1.CAB').read_bytes()[:-30])
