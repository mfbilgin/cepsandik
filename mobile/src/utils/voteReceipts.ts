import * as SecureStore from 'expo-secure-store';

export type VoteReceipt = {
    electionId: string;
    trackingCode: string;
    electionTitle?: string;
    recordedAt: string;
};

const keyForElection = (electionId: string | number) => `vote_receipts_${electionId}`;

export const getVoteReceipts = async (electionId: string | number): Promise<VoteReceipt[]> => {
    const raw = await SecureStore.getItemAsync(keyForElection(electionId));
    if (!raw) return [];
    try {
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
};

export const saveVoteReceipt = async (receipt: VoteReceipt): Promise<void> => {
    if (!receipt.trackingCode?.trim()) return;
    const existing = await getVoteReceipts(receipt.electionId);
    const next = [
        receipt,
        ...existing.filter((item) => item.trackingCode !== receipt.trackingCode),
    ].slice(0, 10);
    await SecureStore.setItemAsync(keyForElection(receipt.electionId), JSON.stringify(next));
};
