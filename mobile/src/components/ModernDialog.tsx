import React from 'react';
import { View, Text, Modal, TouchableOpacity, Animated, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { tw } from '../utils/tailwind';
import { useUI, DialogType } from '../context/UIContext';

export const ModernDialog = () => {
    const { dialog, hideDialog } = useUI();

    if (!dialog) return null;

    const getIcon = (type?: DialogType) => {
        switch (type) {
            case 'success': return { name: 'checkmark-circle', color: '#10b981' };
            case 'error': return { name: 'close-circle', color: '#ef4444' };
            case 'warning': return { name: 'warning', color: '#f59e0b' };
            case 'confirm': return { name: 'help-circle', color: '#3b82f6' };
            default: return { name: 'information-circle', color: '#3b82f6' };
        }
    };

    const icon = getIcon(dialog.type);

    const handleConfirm = () => {
        dialog.onConfirm?.();
        hideDialog();
    };

    const handleCancel = () => {
        dialog.onCancel?.();
        hideDialog();
    };

    return (
        <Modal
            transparent
            visible={dialog.visible}
            animationType="fade"
            onRequestClose={hideDialog}
        >
            <View style={tw`flex-1 bg-black/40 justify-center items-center px-6`}>
                <View style={tw`bg-surface w-full max-w-sm rounded-3xl p-6 shadow-2xl`}>
                    {/* Icon Header */}
                    <View style={tw`items-center mb-4`}>
                        <View style={[tw`w-16 h-16 rounded-full items-center justify-center bg-slate-50`, { borderWith: 2, borderColor: icon.color + '20' }]}>
                            <Ionicons name={icon.name as any} size={40} color={icon.color} />
                        </View>
                    </View>

                    {/* Content */}
                    <Text style={tw`text-xl font-bold text-slate-900 text-center mb-2 leading-tight`}>
                        {dialog.title}
                    </Text>
                    <Text style={tw`text-base text-slate-500 text-center mb-8 leading-relaxed`}>
                        {dialog.message}
                    </Text>

                    {/* Actions */}
                    <View style={tw`flex-row gap-3`}>
                        {(dialog.type === 'confirm' || dialog.onCancel) && (
                            <TouchableOpacity
                                onPress={handleCancel}
                                style={tw`flex-1 bg-slate-100 py-3.5 rounded-xl border border-slate-200`}
                            >
                                <Text style={tw`text-center font-bold text-slate-600`}>
                                    {dialog.cancelText || 'İptal'}
                                </Text>
                            </TouchableOpacity>
                        )}
                        <TouchableOpacity
                            onPress={handleConfirm}
                            style={[tw`flex-1 py-3.5 rounded-xl shadow-sm`, { backgroundColor: icon.color }]}
                        >
                            <Text style={tw`text-center font-bold text-white`}>
                                {dialog.confirmText || 'Tamam'}
                            </Text>
                        </TouchableOpacity>
                    </View>
                </View>
            </View>
        </Modal>
    );
};
