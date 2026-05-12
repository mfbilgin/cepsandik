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
import { CommunityMembersScreen } from '../screens/main/CommunityMembersScreen';
import { CreateElectionScreen } from '../screens/main/CreateElectionScreen';
import { VoteVerificationScreen } from '../screens/main/VoteVerificationScreen';
import { ElectionResultsScreen } from '../screens/main/ElectionResultsScreen';
import { RecoveryCodesScreen } from '../screens/main/RecoveryCodesScreen';
import { useI18n } from '../i18n/LanguageContext';

const Stack = createNativeStackNavigator();

export const MainStack = () => {
    const { t } = useI18n();

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
                name="CommunityMembers"
                component={CommunityMembersScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="ElectionDetail"
                component={ElectionDetailScreen}
                options={{ title: t('main.stack.electionDetail') }}
            />
            <Stack.Screen
                name="VotingBallot"
                component={VotingBallotScreen}
                options={{ title: t('main.stack.votingBallot') }}
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
                name="NotificationSettings"
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
            <Stack.Screen
                name="VoteVerification"
                component={VoteVerificationScreen}
                options={{ headerShown: false }}
            />
            <Stack.Screen
                name="ElectionResults"
                component={ElectionResultsScreen}
                options={{ headerShown: false }}
            />
        </Stack.Navigator>
    );
};

