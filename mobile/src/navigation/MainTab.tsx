import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { theme } from '../utils/theme';
import { tw } from '../utils/tailwind';

import { HomeScreen } from '../screens/main/HomeScreen';
import { CommunitiesScreen } from '../screens/main/CommunitiesScreen';
import { ArchiveScreen } from '../screens/main/ArchiveScreen';
import { ProfileScreen } from '../screens/main/ProfileScreen';
import { GuardianScreen } from '../screens/main/GuardianScreen';
import { useI18n } from '../i18n/LanguageContext';

const Tab = createBottomTabNavigator();

export const MainTab = () => {
    const { t } = useI18n();
    const { bottom } = useSafeAreaInsets();

    return (
        <Tab.Navigator
            screenOptions={({ route }) => ({
                headerShown: false,
                tabBarActiveTintColor: theme.colors.primary,
                tabBarInactiveTintColor: theme.colors.secondary,
                tabBarStyle: {
                    ...tw`bg-surface border-t border-slate-200 shadow-sm`,
                    height: 64 + bottom,
                    paddingBottom: bottom > 0 ? bottom : 8,
                    paddingTop: 8,
                },
                tabBarLabelStyle: tw`text-[10px] font-medium`,
                tabBarIcon: ({ focused, color, size }) => {
                    let iconName: keyof typeof Ionicons.glyphMap = 'home';

                    if (route.name === 'Home') {
                        iconName = focused ? 'home' : 'home-outline';
                    } else if (route.name === 'Communities') {
                        iconName = focused ? 'people' : 'people-outline';
                    } else if (route.name === 'Guardian') {
                        iconName = focused ? 'shield-checkmark' : 'shield-checkmark-outline';
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
            <Tab.Screen name="Guardian" component={GuardianScreen} options={{ title: "Emanetçi" }} />
            <Tab.Screen name="Archive" component={ArchiveScreen} options={{ title: t('main.tab.archive') }} />
            <Tab.Screen name="Profile" component={ProfileScreen} options={{ title: t('main.tab.profile') }} />
        </Tab.Navigator>
    );
};
