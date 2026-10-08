#!/usr/bin/env bash
# Source this file from the existing isolated checkout; no separate worktree is needed.
export JAVA_HOME=/workspace/toolchains/jdk/jdk-21.0.12.1+1
export ANDROID_HOME=/workspace/toolchains/android
export ANDROID_USER_HOME=/workspace/toolchains/android-user
export GRADLE_USER_HOME=/workspace/toolchains/gradle
export NPM_CONFIG_CACHE=/workspace/toolchains/npm-cache
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
python3 - <<'PY'
import os,pathlib,urllib.parse
root=pathlib.Path('/workspace/toolchains/gradle');root.mkdir(parents=True,exist_ok=True)
path=root/'gradle.properties'
keys=['systemProp.https.proxyHost','systemProp.https.proxyPort','systemProp.http.proxyHost','systemProp.http.proxyPort','systemProp.javax.net.ssl.trustStore']
lines=path.read_text().splitlines() if path.exists() else []
lines=[line for line in lines if not any(line.startswith(key+'=') for key in keys)]
proxy=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
if proxy.hostname:
 if proxy.username or proxy.password: raise SystemExit('Authenticated proxy requires supported runtime configuration; credentials will not be copied.')
 for protocol in ['http','https']:
  lines += [f'systemProp.{protocol}.proxyHost={proxy.hostname}',f'systemProp.{protocol}.proxyPort={proxy.port or 80}']
trust=pathlib.Path('/etc/ssl/certs/java/cacerts')
if trust.exists(): lines.append('systemProp.javax.net.ssl.trustStore='+str(trust))
path.write_text('\n'.join(lines)+'\n')
PY
