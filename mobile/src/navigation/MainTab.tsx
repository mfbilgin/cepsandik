import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '../utils/theme';
import tw from 'twrnc';

import { HomeScreen } from '../screens/main/HomeScreen';
import { CommunitiesScreen } from '../screens/main/CommunitiesScreen';
import { ArchiveScreen } from '../screens/main/ArchiveScreen';
import { ProfileScreen } from '../screens/main/ProfileScreen';
import { useI18n } from '../i18n/LanguageContext';

const Tab = createBottomTabNavigator();

export const MainTab = () => {
    const { t } = useI18n();

    return (
        <Tab.Navigator
            screenOptions={({ route }) => ({
                headerShown: false,
                tabBarActiveTintColor: '#1162d4',
                tabBarInactiveTintColor: '#94a3b8',
                tabBarStyle: tw`bg-white border-t border-slate-200 h-16 pb-2 pt-2 shadow-sm`,
                tabBarLabelStyle: tw`text-[10px] font-medium`,
                tabBarIcon: ({ focused, color, size }) => {
                    let iconName: keyof typeof Ionicons.glyphMap = 'home';

                    if (route.name === 'Home') {
                        iconName = focused ? 'home' : 'home-outline';
                    } else if (route.name === 'Communities') {
                        iconName = focused ? 'people' : 'people-outline';
                    } else if (route.name === 'Archive') {
                        iconName = focused ? 'archive' : 'archive-outline';
                    } else if (route.name === 'Profile') {
                        iconName = focused ? 'person' : 'person-outline';
                    }

                    return <Ionicons name={iconName} size={!focused ? 24 : 26} color={color} />;
                },
            })}
        >
            <Tab.Screen name="Home" component={HomeScreen} options={{ title: t('main.tab.home') }} />
            <Tab.Screen name="Communities" component={CommunitiesScreen} options={{ title: t('main.tab.communities') }} />
            <Tab.Screen name="Archive" component={ArchiveScreen} options={{ title: t('main.tab.archive') }} />
            <Tab.Screen name="Profile" component={ProfileScreen} options={{ title: t('main.tab.profile') }} />
        </Tab.Navigator>
    );
};
