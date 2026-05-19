/**
 * Password Strength Validation Utility
 * Implements entropy-based scoring with pattern detection
 * and optional breach database validation
 */
import * as Crypto from 'expo-crypto';

export interface PasswordStrengthResult {
  score: number; // 0-4 (0=very weak, 4=very strong)
  strength: 'very_weak' | 'weak' | 'fair' | 'good' | 'strong';
  feedback: string[];
  isBreached: boolean;
  entropyBits: number;
  passedChecks: {
    minimumLength: boolean;
    characterVariety: boolean;
    noCommonPatterns: boolean;
    sufficientEntropy: boolean;
    notBreached: boolean;
  };
}

interface CharacterSets {
  lowercase: boolean;
  uppercase: boolean;
  numbers: boolean;
  special: boolean;
}

/**
 * Calculate Shannon entropy of a password
 * Higher entropy = more randomness = stronger password
 */
function calculateEntropy(password: string): number {
  const frequencies: { [key: string]: number } = {};

  for (const char of password) {
    frequencies[char] = (frequencies[char] || 0) + 1;
  }

  let entropy = 0;
  const length = password.length;

  for (const count of Object.values(frequencies)) {
    const probability = count / length;
    entropy -= probability * Math.log2(probability);
  }

  return entropy * length;
}

/**
 * Calculate pool size entropy (based on character set used)
 * Larger pool = stronger password
 */
function calculatePoolEntropy(password: string): number {
  let poolSize = 0;

  if (/[a-z]/.test(password)) poolSize += 26;
  if (/[A-Z]/.test(password)) poolSize += 26;
  if (/[0-9]/.test(password)) poolSize += 10;
  if (/[^a-zA-Z0-9]/.test(password)) poolSize += 32; // Special characters estimate

  return password.length * Math.log2(poolSize);
}

/**
 * Detect sequential patterns (e.g., "123", "abc", "xyz")
 */
function hasSequentialPattern(password: string): boolean {
  const patterns = [
    // Numbers
    '012', '123', '234', '345', '456', '567', '678', '789',
    '210', '321', '432', '543', '654', '765', '876', '987',
    // Lowercase letters
    'abc', 'bcd', 'cde', 'def', 'efg', 'fgh', 'ghi', 'hij', 'ijk', 'jkl',
    'klm', 'lmn', 'mno', 'nop', 'opq', 'pqr', 'qrs', 'rst', 'stu', 'tuv',
    'uvw', 'vwx', 'wxy', 'xyz',
    'cba', 'dcb', 'edc', 'fed', 'gfe', 'hgf', 'ihg', 'jih', 'kji', 'lkj',
    'mlk', 'nml', 'onm', 'pon', 'qpo', 'rqp', 'srq', 'trs', 'uts', 'vut',
    'wvu', 'xwv', 'yxw', 'zyx',
    // Keyboard patterns
    'qwe', 'wer', 'ert', 'rty', 'tyu', 'yui', 'uio', 'iop',
    'asd', 'sdf', 'dfg', 'fgh', 'ghj', 'hjk', 'jkl',
    'zxc', 'xcv', 'cvb', 'vbn', 'bnm',
  ];

  const lowerPassword = password.toLowerCase();
  return patterns.some(pattern => lowerPassword.includes(pattern));
}

/**
 * Detect repeated character patterns (e.g., "aaa", "1111")
 */
function hasRepeatedCharacters(password: string): boolean {
  // Check for 3+ consecutive identical characters
  return /(.)\1{2,}/.test(password);
}

/**
 * Detect common weak patterns
 */
function hasCommonPatterns(password: string): boolean {
  const commonPatterns = [
    /^password/i,
    /^123456/,
    /^qwerty/i,
    /^admin/i,
    /^letmein/i,
    /^welcome/i,
    /^monkey/i,
    /^dragon/i,
    /^master/i,
    /^shadow/i,
    /^batman/i,
    /^superman/i,
  ];

  return commonPatterns.some(pattern => pattern.test(password));
}

/**
 * Check character variety in password
 */
function getCharacterSets(password: string): CharacterSets {
  return {
    lowercase: /[a-z]/.test(password),
    uppercase: /[A-Z]/.test(password),
    numbers: /[0-9]/.test(password),
    special: /[^a-zA-Z0-9]/.test(password),
  };
}

/**
 * Count unique characters - more diversity = stronger password
 */
function getUniquenessRatio(password: string): number {
  const uniqueChars = new Set(password).size;
  return uniqueChars / password.length;
}

/**
 * Check if password appears in Have I Been Pwned dataset
 * Uses SHA-1 hash with k-anonymity model (first 5 chars sent)
 * Returns false if check fails (to not block user on network error)
 */
async function checkBreachDatabase(password: string): Promise<boolean> {
  try {
    // expo-crypto native SHA-1 (crypto.subtle Hermes/RN'de yok — APK'da çalışmaz).
    // K-anonymity: parolanın yalnız ilk 5 hex hanesi sunucuya gider, kalan
    // suffix yerelde karşılaştırılır → parola asla ağa çıkmaz.
    const hashHex = (
      await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA1, password, {
        encoding: Crypto.CryptoEncoding.HEX,
      })
    ).toUpperCase();

    const prefix = hashHex.substring(0, 5);
    const suffix = hashHex.substring(5);

    // HIBP range API + Add-Padding (zaman saldırılarına karşı boyut maskelemesi)
    const response = await fetch(`https://api.pwnedpasswords.com/range/${prefix}`, {
      method: 'GET',
      headers: { 'Add-Padding': 'true' },
    });

    if (!response.ok) {
      console.warn('HIBP check failed, allowing password (lenient on network failure)');
      return false;
    }

    const text = await response.text();
    // Her satır "SUFFIX:COUNT" formatında. Padding girdileri count=0, gerçek match'ler >0.
    return text.split(/\r?\n/).some((line) => {
      const [hashSuffix, countStr] = line.split(':');
      return (
        hashSuffix &&
        hashSuffix.toUpperCase() === suffix &&
        parseInt(countStr ?? '0', 10) > 0
      );
    });
  } catch (error) {
    // Ağ hatası vb. — bloklama (kullanıcı offline da kayıt yapabilmeli; backend yetkili).
    console.warn('Password breach check error:', error);
    return false;
  }
}

/**
 * Main password strength validation function
 * Implements comprehensive scoring based on multiple factors
 */
export async function validatePasswordStrength(
  password: string,
  checkBreach: boolean = true
): Promise<PasswordStrengthResult> {
  const feedback: string[] = [];
  let score = 0;

  // Check 1: Minimum length (12 characters recommended)
  const minimumLength = password.length >= 12;
  if (minimumLength) {
    score += 1;
  } else {
    feedback.push(
      password.length < 8
        ? 'En az 8 karakter gerekli'
        : 'Daha güçlü şifre için en az 12 karakter kullanın'
    );
  }

  // Check 2: Character variety
  const charSets = getCharacterSets(password);
  const varietyCount = Object.values(charSets).filter(Boolean).length;
  const characterVariety = varietyCount >= 3;

  if (characterVariety) {
    score += 1;
  } else {
    const missing = [];
    if (!charSets.lowercase) missing.push('küçük harf');
    if (!charSets.uppercase) missing.push('BÜYÜK harf');
    if (!charSets.numbers) missing.push('sayı');
    if (!charSets.special) missing.push('özel karakter');
    feedback.push(`Lütfen ekleyin: ${missing.join(', ')}`);
  }

  // Check 3: Pattern detection
  const hasSequential = hasSequentialPattern(password);
  const hasRepeated = hasRepeatedCharacters(password);
  const hasCommon = hasCommonPatterns(password);

  let noCommonPatterns = !hasSequential && !hasRepeated && !hasCommon;
  if (noCommonPatterns) {
    score += 1;
  } else {
    if (hasCommon) feedback.push('Çok yaygın bir şifre modelini değiştirin');
    if (hasSequential) feedback.push('Sıralı karakterleri (abc, 123) kullanmayın');
    if (hasRepeated) feedback.push('Aynı karakteri tekrarlı kullanmayın (aaa, 1111)');
  }

  // Check 4: Entropy-based scoring
  const shannnonEntropy = calculateEntropy(password);
  const poolEntropy = calculatePoolEntropy(password);
  const avgEntropy = (shannnonEntropy + poolEntropy) / 2;
  const uniqueness = getUniquenessRatio(password);

  // Stronger entropy requirement: >50 bits
  const sufficientEntropy = avgEntropy > 50 || (password.length >= 14 && uniqueness > 0.7);

  if (sufficientEntropy) {
    score += 1;
  } else if (password.length >= 12 && varietyCount >= 3) {
    // If user has good length and variety, give them credit even if entropy is marginal
    if (avgEntropy > 40) {
      score += 1;
    } else {
      feedback.push('Daha karmaşık bir şifre kullanmayı düşünün');
    }
  }

  // Check 5: Breach database (Have I Been Pwned)
  let isBreached = false;
  if (checkBreach && password.length > 0) {
    try {
      isBreached = await checkBreachDatabase(password);
      if (isBreached) {
        feedback.push('Bu şifre geçmişte veri ihlallerinde tespit edilmiştir. Lütfen farklı bir şifre seçin.');
        score = 0; // Breached passwords get zero score
      }
    } catch (error) {
      console.warn('Breach check failed:', error);
      // Don't block on error, user can proceed
    }
  }

  // Determine strength level
  const strengthMap: Array<'very_weak' | 'weak' | 'fair' | 'good' | 'strong'> = [
    'very_weak',
    'weak',
    'fair',
    'good',
    'strong',
  ];

  // Apply final adjustments
  if (isBreached) {
    score = 0;
  } else if (password.length < 8) {
    score = Math.min(score, 1); // Very weak if under 8 chars
  }

  const strength = strengthMap[Math.min(score, 4)];

  // Add positive feedback for strong passwords
  if (score === 4 && !isBreached) {
    feedback.push('✓ Çok güçlü bir şifre! Bunu kullanabilirsiniz.');
  } else if (score === 3 && !isBreached) {
    feedback.push('✓ İyi bir şifre. Daha da güçlendirmek isterseniz daha fazla karakter ekleyin.');
  }

  return {
    score: Math.max(0, Math.min(score, 4)),
    strength,
    feedback,
    isBreached,
    entropyBits: avgEntropy,
    passedChecks: {
      minimumLength,
      characterVariety,
      noCommonPatterns,
      sufficientEntropy: sufficientEntropy && !isBreached,
      notBreached: !isBreached,
    },
  };
}

/**
 * Synchronous version without breach checking (for initial UI feedback)
 */
export function validatePasswordStrengthSync(password: string): PasswordStrengthResult {
  const feedback: string[] = [];
  let score = 0;

  const minimumLength = password.length >= 12;
  if (minimumLength) {
    score += 1;
  } else {
    feedback.push(
      password.length < 8
        ? 'En az 8 karakter gerekli'
        : 'Daha güçlü şifre için en az 12 karakter kullanın'
    );
  }

  const charSets = getCharacterSets(password);
  const varietyCount = Object.values(charSets).filter(Boolean).length;
  const characterVariety = varietyCount >= 3;

  if (characterVariety) {
    score += 1;
  } else {
    const missing = [];
    if (!charSets.lowercase) missing.push('küçük harf');
    if (!charSets.uppercase) missing.push('BÜYÜK harf');
    if (!charSets.numbers) missing.push('sayı');
    if (!charSets.special) missing.push('özel karakter');
    feedback.push(`Lütfen ekleyin: ${missing.join(', ')}`);
  }

  const hasSequential = hasSequentialPattern(password);
  const hasRepeated = hasRepeatedCharacters(password);
  const hasCommon = hasCommonPatterns(password);

  const noCommonPatterns = !hasSequential && !hasRepeated && !hasCommon;
  if (noCommonPatterns) {
    score += 1;
  } else {
    if (hasCommon) feedback.push('Çok yaygın bir şifre modelini değiştirin');
    if (hasSequential) feedback.push('Sıralı karakterleri (abc, 123) kullanmayın');
    if (hasRepeated) feedback.push('Aynı karakteri tekrarlı kullanmayın (aaa, 1111)');
  }

  const shannnonEntropy = calculateEntropy(password);
  const poolEntropy = calculatePoolEntropy(password);
  const avgEntropy = (shannnonEntropy + poolEntropy) / 2;
  const uniqueness = getUniquenessRatio(password);

  const sufficientEntropy = avgEntropy > 50 || (password.length >= 14 && uniqueness > 0.7);

  if (sufficientEntropy) {
    score += 1;
  } else if (password.length >= 12 && varietyCount >= 3) {
    if (avgEntropy > 40) {
      score += 1;
    } else {
      feedback.push('Daha karmaşık bir şifre kullanmayı düşünün');
    }
  }

  const strengthMap: Array<'very_weak' | 'weak' | 'fair' | 'good' | 'strong'> = [
    'very_weak',
    'weak',
    'fair',
    'good',
    'strong',
  ];

  if (password.length < 8) {
    score = Math.min(score, 1);
  }

  const strength = strengthMap[Math.min(score, 4)];

  if (score === 4) {
    feedback.push('✓ Çok güçlü bir şifre! Bunu kullanabilirsiniz.');
  } else if (score === 3) {
    feedback.push('✓ İyi bir şifre. Daha da güçlendirmek isterseniz daha fazla karakter ekleyin.');
  }

  return {
    score: Math.max(0, Math.min(score, 4)),
    strength,
    feedback,
    isBreached: false,
    entropyBits: avgEntropy,
    passedChecks: {
      minimumLength,
      characterVariety,
      noCommonPatterns,
      sufficientEntropy,
      notBreached: true,
    },
  };
}
