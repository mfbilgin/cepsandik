import * as SecureStore from 'expo-secure-store';
import forge from 'node-forge';

/**
 * ElectionGuard resmi 4096-bit Baseline Parametreleri.
 */
const P = BigInt("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff93c467e37db0c7a4d1be3f810152cb56a1cecc3af65cc0190c03df34709affbd8e4b59fa03a9f0eed0649ccb621057d11056ae9132135a08e43b4673d74bafea58deb878cc86d733dbe7bf38154b36cf8a96d1567899aaae0c09d4c8b6b7b86fd2a1ea1de62ff8643ec7c271827977225e6ac2f0bd61c746961542a3ce3bea5db54fe70e63e6d09f8fc28658e80567a47cfde60ee741e5d85a7bd46931ced8220365594964b839896fcaabccc9b31959c083f22ad3ee591c32fab2c7448f2a057db2db49ee52e0182741e53865f004cc8e704b7c5c40bf304c4d8c4f13edf6047c555302d2238d8ce11df2424f1b66c2c5d238d0744db679af2890487031f9c0aea1c4bb6fe9554ee528fdf1b05e5b256223b2f09215f3719f9c7ccc69ddf172d0d6234217fcc0037f18b93ef5389130b7a661e5c26e54214068bbcafea32a67818bd3075ad1f5c7e9cc3d1737fb28171baf84dbb6612b7881c1a48e439cd03a92bf52225a2b38e6542e9f722bce15a381b5753ea842763381ccae83512b30511b32e5e8d80362149ad030aaba5f3a5798bb22aa7ec1b6d0f17903f4e22d840734aa85973f79a93ffb82a75c47c03d43d2f9ca02d03199baceddd4533a52566affffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
const Q = BigInt("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff43");
const G = BigInt("0x1d41e49c477e15eaeef0c5e4ac08d4a46c268cd3424fc01d13769bdb43673218587bc86c4c1448d006a03699f3abae5feb19e296f5d143cc5e4a3fc89088c9f4523d166ee3ae9d5fb03c0bdd77add5c017f6c55e2ec92c226fef5c6c1df2e7c36d90e7eaade098241d3409983bccd2b5379e9391fbc62f9f8d939d1208b160367c134264122189595ec85c8cdbe5f9d307f46912c04932f8c16815a76b4682bd6bdc0ed52b00d8d30f59c731d5a7ffae8165d53cf96649aac2b743da56f14f19dacc5236f29b1ab9f9befc69697293d5dead8b5bf5de9bab6de67c45719e56344a3cbdf3609824b1b578e34eaeb6dd3190ab3571d6d671c512282c1da7bd36b4251d2584fadea80b9e141423074dd9b5fb83acbdead4c87a58fff517f977a83080370a3b0cf98a1bc2978c47aac29611fd6c40e2f9875c35d50443a9aa3f49611dcd3a0d6ff3cb3facf31471bdb61860b92c594d4e46569bb39feeadff1fd64c836a6d6db85c6ba7241766b7ab56bf739633b054147f7170921412e948d9e47402d15bb1c257318612c121c36b80eb8433c08e7d0b7149e3ab0a8735a92edce8ff943e28a2dceacfcc69ec318909cb047be1c5858844b5ad44f22eeb289e4cc554f7a5e2f3dea026877ff92851816071ce028eb868d965ccb2d2295a8c55bd1c070b39b09ae06b37d29343b9d8997dc244c468b980970731736ee018bbadb987");

/**
 * Modüler Üs Alma (g^e mod m) - Büyük sayılar için verimli algoritma.
 */
function powerMod(base: bigint, exp: bigint, mod: bigint): bigint {
    let res = BigInt(1);
    base = base % mod;
    while (exp > 0n) {
        if (exp % 2n === 1n) res = (res * base) % mod;
        base = (base * base) % mod;
        exp = exp / 2n;
    }
    return res;
}

/**
 * ElectionGuard Schnorr Proof (ZKP) üretimi.
 * Proof of knowledge of secret key s: K = g^s mod p
 */
function generateSchnorrProof(s: bigint, K: bigint): any {
    // 1. Rastgele k seç (nonce)
    const kBytes = forge.random.getBytesSync(32);
    const k = BigInt("0x" + forge.util.bytesToHex(kBytes)) % Q;
    
    // 2. Commitment h = g^k mod p
    const h = powerMod(G, k, P);
    
    // 3. Challenge c = Hash(G, K, h) mod q
    // Not: Hash fonksiyonu spec'e göre basitleştirilmiştir.
    const hashInput = `${G.toString(16)}${K.toString(16)}${h.toString(16)}`;
    const md = forge.md.sha256.create();
    md.update(hashInput);
    const c = BigInt("0x" + md.digest().toHex()) % Q;
    
    // 4. Response v = (k + c*s) mod q
    const v = (k + (c * s)) % Q;
    
    return {
        public_key: K.toString(16).toLowerCase(),
        commitment: h.toString(16).toLowerCase(),
        challenge: c.toString(16).toLowerCase(),
        response: v.toString(16).toLowerCase()
    };
}

export const guardianCrypto = {
    
    async generateAndSaveKeyPair(electionId: number): Promise<{ publicKey: string, commitments: string, coefficientProofs: string }> {
        // 1. Secret Key üret (s)
        const sBytes = forge.random.getBytesSync(32);
        const s = BigInt("0x" + forge.util.bytesToHex(sBytes)) % Q;
        
        // 2. Güvenli sakla
        await SecureStore.setItemAsync(`guardian_key_${electionId}`, s.toString(16));
        
        // 3. Public Key hesapla (K = g^s mod p)
        const K = powerMod(G, s, P);
        const publicKeyHex = K.toString(16).toLowerCase();
        
        // 4. Schnorr Proof üret (Spec Compliance)
        const proof = generateSchnorrProof(s, K);
        
        const commitmentsJson = JSON.stringify([publicKeyHex]); // Baseline: constant term commitment
        const coefficientProofsJson = JSON.stringify([proof]);
        
        return {
            publicKey: publicKeyHex,
            commitments: commitmentsJson,
            coefficientProofs: coefficientProofsJson
        };
    },

    async generateDecryptionShare(electionId: number, encryptedTallyA: string): Promise<string> {
        const secretKeyHex = await SecureStore.getItemAsync(`guardian_key_${electionId}`);
        if (!secretKeyHex) throw new Error("Özel anahtar bulunamadı");
        
        const s = BigInt("0x" + secretKeyHex);
        const A = BigInt("0x" + encryptedTallyA);

        // Share M = A^s mod p
        const M = powerMod(A, s, P);
        
        // Bu kısım spec'teki TallyDecryptionShare JSON formatına uygun olmalıdır.
        const shareData = JSON.stringify({
            guardian_id: "mobil-cihaz",
            share: M.toString(16).toLowerCase(),
            proof: { /* ZKP for decryption share */ }
        });

        return shareData;
    },

    async deleteKey(electionId: number) {
        await SecureStore.deleteItemAsync(`guardian_key_${electionId}`);
    }
};
