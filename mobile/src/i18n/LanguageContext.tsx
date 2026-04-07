import React, { createContext, useContext, useMemo, useState } from 'react';
import * as Localization from 'expo-localization';
import { Language, translate, TranslationKey } from './translations';

type TranslateParams = Record<string, string | number>;

type LanguageContextValue = {
    language: Language;
    setLanguage: (language: Language) => void;
    t: (key: TranslationKey, params?: TranslateParams) => string;
};

const getDefaultLanguage = (): Language => {
    const locale = Localization.getLocales?.()[0];
    const tag = locale?.languageTag?.toLowerCase() ?? locale?.languageCode?.toLowerCase() ?? '';
    return tag.startsWith('en') ? 'en' : 'tr';
};

const LanguageContext = createContext<LanguageContextValue | undefined>(undefined);

export const LanguageProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
    const [language, setLanguage] = useState<Language>(getDefaultLanguage);

    const value = useMemo<LanguageContextValue>(() => ({
        language,
        setLanguage,
        t: (key, params) => translate(language, key, params),
    }), [language]);

    return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>;
};

export const useI18n = (): LanguageContextValue => {
    const context = useContext(LanguageContext);
    if (!context) {
        throw new Error('useI18n must be used within LanguageProvider');
    }
    return context;
};
