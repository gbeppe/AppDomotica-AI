#!/usr/bin/env python3
"""Renew Node-RED TLS atomically. Run as root; never restart Node-RED."""
import grp, hashlib, os, pathlib, shutil, subprocess, tempfile
DOMAIN = 'domopi.tailf30ba8.ts.net'
ROOT = pathlib.Path('/etc/nodered/tls')
def run(*args):
    return subprocess.check_output(args, stderr=subprocess.PIPE)
ROOT.mkdir(parents=True, exist_ok=True, mode=0o750)
gid = grp.getgrnam('pi').gr_gid
os.chown(ROOT, 0, gid)
with tempfile.TemporaryDirectory(prefix='.renew-', dir=ROOT) as temporary:
    stage = pathlib.Path(temporary)
    cert, key = stage/'cert.pem', stage/'key.pem'
    run('/usr/bin/tailscale', 'cert', '--cert-file='+str(cert), '--key-file='+str(key), DOMAIN)
    run('openssl', 'x509', '-in', str(cert), '-noout', '-checkhost', DOMAIN)
    # Fail closed if the returned certificate has less than a week remaining.
    run('openssl', 'x509', '-in', str(cert), '-noout', '-checkend', '604800')
    cert_public = run('openssl', 'x509', '-in', str(cert), '-pubkey', '-noout')
    key_public = run('openssl', 'pkey', '-in', str(key), '-pubout')
    if cert_public != key_public:
        raise RuntimeError('Certificate/private key mismatch')
    digest = hashlib.sha256(cert.read_bytes()).hexdigest()
    generation = ROOT/('generation-'+digest)
    if not generation.exists():
        generation.mkdir(mode=0o750)
        os.chown(generation, 0, gid)
        for source in (cert, key):
            target = generation/source.name
            shutil.copyfile(source, target)
            os.chown(target, 0, gid)
            target.chmod(0o640)
    link = ROOT/'current'
    if not link.is_symlink() or link.resolve() != generation:
        candidate = ROOT/'current.new'
        candidate.unlink(missing_ok=True)
        candidate.symlink_to(generation.name)
        candidate.replace(link)
        print('Node-RED TLS certificate updated; runtime refresh within one hour.')
    else:
        print('Node-RED TLS certificate already current.')
