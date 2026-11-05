#!/usr/bin/env python3
"""Create a fixed local release identity without committing or printing secrets."""
import os
import pathlib
import secrets
import shutil
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parent.parent
directory = root / 'signing'
directory.mkdir(mode=0o700, exist_ok=True)
config = directory / 'release.properties'
keystore = directory / 'aeroledger-release.jks'
if config.exists() or keystore.exists():
    sys.exit('Release identity already exists in signing/. Keep the existing files to preserve update compatibility.')
keytool = shutil.which('keytool')
if not keytool:
    sys.exit('JDK 17 keytool is required.')
os.umask(0o077)
password = secrets.token_urlsafe(40)
environment = dict(os.environ, AEROLEDGER_KEYTOOL_PASSWORD=password)
result = subprocess.run([keytool, '-genkeypair', '-keystore', str(keystore), '-storetype', 'JKS',
                         '-storepass:env', 'AEROLEDGER_KEYTOOL_PASSWORD', '-keypass:env', 'AEROLEDGER_KEYTOOL_PASSWORD',
                         '-alias', 'aeroledger', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
                         '-dname', 'CN=AeroLedger,OU=Campus Ledger,O=AeroLedger,C=CN'],
                        env=environment, capture_output=True, text=True)
if result.returncode:
    sys.exit('Release identity could not be created. Check JDK keytool and signing/ directory permissions.')
config.write_text('STORE_FILE=signing/aeroledger-release.jks\nSTORE_PASSWORD=' + password +
                  '\nKEY_ALIAS=aeroledger\nKEY_PASSWORD=' + password + '\n')
os.chmod(config, 0o600)
os.chmod(keystore, 0o600)
print('Fixed release identity created in signing/. Back up this directory securely; do not commit it.')
