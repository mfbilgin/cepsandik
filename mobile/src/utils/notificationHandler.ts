import Constants from 'expo-constants';
import { Platform } from 'react-native';

/**
 * Check if running inside Expo Go (push notifications removed since SDK 53)
 */
const isExpoGo = Constants.appOwnership === 'expo';

export async function registerForPushNotificationsAsync(): Promise<string | null> {
  // Push notifications are not supported in Expo Go (SDK 53+)
  if (isExpoGo) {
    console.log('[Notifications] Push notifications are not available in Expo Go. Use a development build for full notification support.');
    return null;
  }

  try {
    const Notifications = await import('expo-notifications');
    const Device = await import('expo-device');

    Notifications.setNotificationHandler({
      handleNotification: async () => ({
        shouldShowAlert: true,
        shouldShowBanner: true,
        shouldShowList: true,
        shouldPlaySound: true,
        shouldSetBadge: false,
      }),
    });

    if (Platform.OS === 'android') {
      await Notifications.setNotificationChannelAsync('default', {
        name: 'default',
        importance: Notifications.AndroidImportance.MAX,
        vibrationPattern: [0, 250, 250, 250],
        lightColor: '#FF231F7C',
      });
    }

    if (Device.default?.isDevice ?? Device.isDevice) {
      const { status: existingStatus } = await Notifications.getPermissionsAsync();
      let finalStatus = existingStatus;
      if (existingStatus !== 'granted') {
        const { status } = await Notifications.requestPermissionsAsync();
        finalStatus = status;
      }
      if (finalStatus !== 'granted') {
        return null;
      }

      const projectId = Constants.expoConfig?.extra?.eas?.projectId || Constants.easConfig?.projectId;
      const token = (await Notifications.getExpoPushTokenAsync({ projectId })).data;
      return token;
    } else {
      console.log('[Notifications] Physical device required for push notifications');
    }
  } catch (error) {
    console.log('[Notifications] Push notifications unavailable:', error);
  }

  return null;
}
