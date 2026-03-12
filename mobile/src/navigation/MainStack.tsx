import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { MainTab } from './MainTab';
import { ElectionDetailScreen } from '../screens/main/ElectionDetailScreen';
import { VotingBallotScreen } from '../screens/main/VotingBallotScreen';
import { CreateCommunityScreen } from '../screens/main/CreateCommunityScreen';
import { EditProfileScreen } from '../screens/main/EditProfileScreen';
import { SecurityScreen } from '../screens/main/SecurityScreen';
import { NotificationsScreen } from '../screens/main/NotificationsScreen';
import { NotificationInboxScreen } from '../screens/main/NotificationInboxScreen';
import { AboutScreen } from '../screens/main/AboutScreen';
import { HelpScreen } from '../screens/main/HelpScreen';
import { TwoFactorSetupSelectionScreen } from '../screens/main/TwoFactorSetupSelectionScreen';
import { TwoFactorAuthenticatorSetupScreen } from '../screens/main/TwoFactorAuthenticatorSetupScreen';
import { TwoFactorVerificationScreen } from '../screens/main/TwoFactorVerificationScreen';
import { CommunityDetailScreen } from '../screens/main/CommunityDetailScreen';
import { CommunityManagementScreen } from '../screens/main/CommunityManagementScreen';
import { CreateElectionScreen } from '../screens/main/CreateElectionScreen';
import { RecoveryCodesScreen } from '../screens/main/RecoveryCodesScreen';

const Stack = createNativeStackNavigator();

export const MainStack = () => {
    return (
        <Stack.Navigator>
            <Stack.Screen
                name="MainTab"
                component={MainTab}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="CommunityDetail"
                component={CommunityDetailScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="CommunityManagement"
                component={CommunityManagementScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="ElectionDetail"
                component={ElectionDetailScreen}
                options={{ title: 'Seçim Detayı' }}
            />
            <Stack.Screen
                name="VotingBallot"
                component={VotingBallotScreen}
                options={{ title: 'Oy Pusulası' }}
            />
            <Stack.Screen
                name="CreateCommunity"
                component={CreateCommunityScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="CreateElection"
                component={CreateElectionScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="EditProfile"
                component={EditProfileScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="SecuritySettings"
                component={SecurityScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="Notifications"
                component={NotificationsScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="NotificationInbox"
                component={NotificationInboxScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="About"
                component={AboutScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="Help"
                component={HelpScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="TwoFactorSetupSelection"
                component={TwoFactorSetupSelectionScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="TwoFactorAuthenticatorSetup"
                component={TwoFactorAuthenticatorSetupScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="TwoFactorVerification"
                component={TwoFactorVerificationScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="RecoveryCodes"
                component={RecoveryCodesScreen}
                options={{ headerShown: false }}
            />
        </Stack.Navigator>
    );
};

