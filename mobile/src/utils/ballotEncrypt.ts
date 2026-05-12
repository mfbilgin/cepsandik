import forge from 'node-forge';

export function randomHex(bytes = 32): string {
    return forge.util.bytesToHex(forge.random.getBytesSync(bytes));
}

export function sha256Hex(value: string): string {
    const md = forge.md.sha256.create();
    md.update(value, 'utf8');
    return md.digest().toHex();
}
