import React, { createContext, useContext, useState, useCallback } from 'react';

export type DialogType = 'success' | 'error' | 'info' | 'warning' | 'confirm';

interface DialogOptions {
    title: string;
    message: string;
    type?: DialogType;
    confirmText?: string;
    cancelText?: string;
    onConfirm?: () => void;
    onCancel?: () => void;
}

interface UIContextType {
    showDialog: (options: DialogOptions) => void;
    hideDialog: () => void;
    dialog: (DialogOptions & { visible: boolean }) | null;
}

const UIContext = createContext<UIContextType | undefined>(undefined);

export const UIProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [dialog, setDialog] = useState<(DialogOptions & { visible: boolean }) | null>(null);

    const showDialog = useCallback((options: DialogOptions) => {
        setDialog({ ...options, visible: true });
    }, []);

    const hideDialog = useCallback(() => {
        setDialog(prev => prev ? { ...prev, visible: false } : null);
    }, []);

    return (
        <UIContext.Provider value={{ showDialog, hideDialog, dialog }}>
            {children}
        </UIContext.Provider>
    );
};

export const useUI = () => {
    const context = useContext(UIContext);
    if (!context) {
        throw new Error('useUI must be used within a UIProvider');
    }
    return context;
};
