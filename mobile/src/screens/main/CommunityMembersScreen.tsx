import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, TouchableOpacity, FlatList, TextInput, ActivityIndicator, Image } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { api } from '../../services/api';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { AppHeader, Badge, EmptyState } from '../../components/ui';

export const CommunityMembersScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { communityId, communityName } = route.params || {};
    const { t } = useI18n();
    const c = theme.colors;

    const [members, setMembers] = useState<any[]>([]);
    const [searchQuery, setSearchQuery] = useState('');
    const [isLoading, setIsLoading] = useState(true);

    const fetchMembers = useCallback(async () => {
        setIsLoading(true);
        try {
            const res = await api.get(`/communities/${communityId}/members?size=500`);
            setMembers(res.data?.data?.content || []);
        } catch (error) {
            console.error("Failed to fetch members", error);
        } finally {
            setIsLoading(false);
        }
    }, [communityId]);

    useEffect(() => {
        fetchMembers();
    }, [fetchMembers]);

    const filteredMembers = members.filter(member => {
        const query = searchQuery.toLowerCase();
        const fullName = `${member.firstName || ''} ${member.lastName || ''}`.toLowerCase();
        const roleMatch = (member.role || '').toLowerCase().includes(query);
        return fullName.includes(query) || roleMatch;
    });

    const roleLabel = (role: string) => {
        switch (role) {
            case 'OWNER': return t('roles.owner') || 'Sahip';
            case 'ADMIN': return t('roles.admin') || 'Yönetici';
            default: return t('roles.member') || 'Üye';
        }
    };

    return (
        <SafeAreaView style={{ flex: 1, backgroundColor: c.background }} edges={['top', 'left', 'right']}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader
                    title={t('communityMembers.title') || 'Tüm Üyeler'}
                    subtitle={communityName || undefined}
                    onBack={() => navigation.goBack()}
                />
            </View>

            {/* Search Bar */}
            <View style={{ paddingHorizontal: theme.spacing.md, paddingBottom: theme.spacing.md }}>
                <View
                    style={{
                        flexDirection: 'row', alignItems: 'center', backgroundColor: c.surface,
                        borderRadius: theme.borderRadius.round, paddingHorizontal: 16, height: 44,
                        borderWidth: 1, borderColor: c.border,
                    }}
                >
                    <Ionicons name="search" size={20} color={c.textTertiary} />
                    <TextInput
                        style={{ flex: 1, height: '100%', paddingHorizontal: 12, color: c.text, fontSize: 15 }}
                        placeholder={t('communityMembers.searchPlaceholder') || 'Üye ara...'}
                        placeholderTextColor={c.textTertiary}
                        value={searchQuery}
                        onChangeText={setSearchQuery}
                    />
                    {searchQuery.length > 0 && (
                        <TouchableOpacity onPress={() => setSearchQuery('')}>
                            <Ionicons name="close-circle" size={20} color={c.textTertiary} />
                        </TouchableOpacity>
                    )}
                </View>
            </View>

            {isLoading ? (
                <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
                    <ActivityIndicator size="large" color={c.primary} />
                </View>
            ) : (
                <FlatList
                    data={filteredMembers}
                    keyExtractor={(item) => item.id.toString()}
                    contentContainerStyle={{ padding: theme.spacing.md, paddingBottom: 96 }}
                    ItemSeparatorComponent={() => <View style={{ height: 1, backgroundColor: c.border, marginVertical: 12 }} />}
                    renderItem={({ item }) => {
                        const privileged = item.role === 'OWNER' || item.role === 'ADMIN';
                        return (
                            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, flex: 1 }}>
                                    {item.profileImage ? (
                                        <Image source={{ uri: item.profileImage }} style={{ width: 48, height: 48, borderRadius: 24, borderWidth: 1, borderColor: c.border }} />
                                    ) : (
                                        <View style={{ width: 48, height: 48, borderRadius: 24, backgroundColor: c.surfaceAlt, alignItems: 'center', justifyContent: 'center' }}>
                                            <MaterialIcons name="person" size={24} color={c.textSecondary} />
                                        </View>
                                    )}
                                    <View style={{ flex: 1 }}>
                                        <Text style={{ fontSize: 15, fontWeight: '700', color: c.text }}>
                                            {item.firstName ? `${item.firstName} ${item.lastName}` : (item.displayName || `#${String(item.userId).slice(-8).toUpperCase()}`)}
                                        </Text>
                                        <Text style={{ fontSize: 12, color: c.textSecondary, fontWeight: '500', marginTop: 2 }}>
                                            {roleLabel(item.role)}
                                        </Text>
                                    </View>
                                </View>
                                {privileged && <Badge label={roleLabel(item.role)} tone="primary" />}
                            </View>
                        );
                    }}
                    ListEmptyComponent={
                        <EmptyState icon="people-outline" title="Üye bulunamadı" />
                    }
                />
            )}
        </SafeAreaView>
    );
};
