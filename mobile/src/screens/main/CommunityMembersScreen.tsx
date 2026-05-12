import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, TouchableOpacity, FlatList, TextInput, ActivityIndicator, Image, StatusBar, Platform } from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { api } from '../../services/api';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';

export const CommunityMembersScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { communityId, communityName } = route.params || {};
    const { t } = useI18n();

    const [members, setMembers] = useState<any[]>([]);
    const [searchQuery, setSearchQuery] = useState('');
    const [isLoading, setIsLoading] = useState(true);

    const fetchMembers = useCallback(async () => {
        setIsLoading(true);
        try {
            // Getting up to 500 members to do local search
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
        <View style={tw`flex-1 bg-background`}>
            <StatusBar barStyle="dark-content" />

            {/* Header */}
            <View style={[tw`px-4 bg-surface/90 border-b border-borderDefault flex-row items-center justify-between pb-2 z-30`, { paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight ?? 44 : 48 }]}>
                <View style={tw`flex-row items-center gap-3`}>
                    <TouchableOpacity onPress={() => navigation.goBack()} style={tw`items-center justify-center rounded-full active:opacity-60`}>
                        <MaterialIcons name="arrow-back" size={24} color={tw.color('primary')} />
                    </TouchableOpacity>
                    <View>
                        <Text style={tw`text-lg font-bold text-textDefault tracking-tight`}>{t('communityMembers.title') || 'Tüm Üyeler'}</Text>
                        {!!communityName && <Text style={tw`text-xs text-textSecondary`}>{communityName}</Text>}
                    </View>
                </View>
            </View>

            {/* Search Bar */}
            <View style={tw`px-4 py-3 bg-surface border-b border-borderDefault`}>
                <View style={tw`flex-row items-center bg-background rounded-full px-4 h-11 border border-borderDefault`}>
                    <Ionicons name="search" size={20} color={tw.color('textSecondary')} />
                    <TextInput
                        style={tw`flex-1 h-full px-3 text-textDefault text-base`}
                        placeholder={t('communityMembers.searchPlaceholder') || 'Üye ara...'}
                        placeholderTextColor={tw.color('textSecondary')}
                        value={searchQuery}
                        onChangeText={setSearchQuery}
                    />
                    {searchQuery.length > 0 && (
                        <TouchableOpacity onPress={() => setSearchQuery('')}>
                            <Ionicons name="close-circle" size={20} color={tw.color('textSecondary')} />
                        </TouchableOpacity>
                    )}
                </View>
            </View>

            {/* Member List */}
            {isLoading ? (
                <View style={tw`flex-1 justify-center items-center`}>
                    <ActivityIndicator size="large" color={tw.color('primary')} />
                </View>
            ) : (
                <FlatList
                    data={filteredMembers}
                    keyExtractor={(item) => item.id.toString()}
                    contentContainerStyle={tw`p-4 pb-24`}
                    ItemSeparatorComponent={() => <View style={tw`h-[1px] bg-borderDefault my-3`} />}
                    renderItem={({ item }) => (
                        <View style={tw`flex-row items-center justify-between`}>
                            <View style={tw`flex-row items-center gap-3`}>
                                {item.profileImage ? (
                                    <Image source={{ uri: item.profileImage }} style={tw`w-12 h-12 rounded-full border border-borderDefault`} />
                                ) : (
                                    <View style={tw`w-12 h-12 bg-primary/10 rounded-full items-center justify-center border border-primary/20`}>
                                        <MaterialIcons name="person" size={24} color={tw.color('primary')} />
                                    </View>
                                )}
                                <View>
                                    <Text style={tw`text-base font-bold text-textDefault`}>
                                        {item.firstName ? `${item.firstName} ${item.lastName}` : (item.displayName || `#${String(item.userId).slice(-8).toUpperCase()}`)}
                                    </Text>
                                    <View style={tw`flex-row items-center gap-1 mt-0.5`}>
                                        <Text style={tw`text-xs text-textSecondary font-medium`}>{roleLabel(item.role)}</Text>
                                        {(item.role === 'OWNER' || item.role === 'ADMIN') && (
                                            <MaterialIcons name="verified" size={12} color={tw.color('primary')} />
                                        )}
                                    </View>
                                </View>
                            </View>
                        </View>
                    )}
                    ListEmptyComponent={
                        <View style={tw`items-center justify-center mt-12`}>
                            <Ionicons name="people-outline" size={48} color={tw.color('borderDefault')} />
                            <Text style={tw`text-textSecondary font-medium mt-4`}>Üye bulunamadı</Text>
                        </View>
                    }
                />
            )}
        </View>
    );
};
