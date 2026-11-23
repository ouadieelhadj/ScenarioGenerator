"""Short Apple diagnostic: bounded positive login POSTs, credentials in stdin only."""
from pathlib import Path
import argparse, datetime, json, os, platform, subprocess, sys

def main():
    if sys.platform != 'darwin':
        raise SystemExit('This diagnostic needs macOS and the existing test secret')
    parser = argparse.ArgumentParser()
    parser.add_argument('--simulator', action='store_true')
    args = parser.parse_args()
    password = os.environ.pop('FP_IOS_TEST_PASSWORD', '')
    if not password:
        raise SystemExit('FP_IOS_TEST_PASSWORD missing; no authentication attempted')
    root = Path(__file__).resolve().parents[2]
    out = Path(os.environ['FP_IOS_OUTPUT']); out.mkdir(parents=True, exist_ok=True)
    binary = out / ('probe-auth-simulator' if args.simulator else 'probe-auth-macos')
    command = ['xcrun', 'swiftc', '-swift-version', '5']
    if args.simulator:
        sdk = subprocess.check_output(['xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path'], text=True).strip()
        arch = platform.machine(); assert arch in ('arm64', 'x86_64')
        command += ['-target', arch + '-apple-ios16.0-simulator', '-sdk', sdk]
    command += [str(root / 'tools/ios/probe-authenticated-https.swift'), '-o', str(binary)]
    build = subprocess.run(command, capture_output=True, text=True, timeout=90)
    if build.returncode:
        # Compilation happens before credentials are supplied; no environment dump.
        print(build.stderr[:8000], file=sys.stderr)
        raise SystemExit('Authenticated probe compilation failed before any POST')
    launch = [str(binary)]
    if args.simulator:
        device = json.loads((out / 'simulator.json').read_text())
        subprocess.run(['xcrun', 'simctl', 'bootstatus', device['udid'], '-b'], check=True, capture_output=True, timeout=180)
        subprocess.run(['codesign', '--force', '--sign', '-', str(binary)], check=True, capture_output=True, timeout=30)
        launch = ['xcrun', 'simctl', 'spawn', device['udid'], str(binary)]
    payload = json.dumps({'login': 'bim.amina', 'password': password})
    result = subprocess.run(launch, input=payload, capture_output=True, text=True, timeout=210)
    if result.returncode:
        raise SystemExit('Authenticated diagnostic process failed; raw output withheld')
    data = json.loads(result.stdout)
    assert password not in result.stdout and 'eyJ' not in result.stdout
    assert data['postRequests'] == 4 and data['systemTrustUnchanged'] and not data['responseBodiesOrTokensRetained']
    assert data['nativeIPhoneSimulatorURLSession'] == args.simulator
    assert [x['variant'] for x in data['observations']] == ['dedicated-default', 'ephemeral', 'default-prewarmed', 'diagnostic-tls12']
    data['utc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    data['successfulLogins'] = sum(x['login'].get('authenticated') is True for x in data['observations'])
    data['intermittenceResolved'] = False
    name = 'diagnostic-auth-apple-simulateur.json' if args.simulator else 'diagnostic-auth-apple-macos.json'
    (out / name).write_text(json.dumps(data, indent=2) + '\n')
    print(json.dumps(data))

if __name__ == '__main__':
    main()
