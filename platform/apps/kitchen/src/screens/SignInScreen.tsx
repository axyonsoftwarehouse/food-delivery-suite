import { useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useSession } from '../auth/session';
import { theme } from '../theme';

const KITCHEN_ROLES = ['kitchen', 'restaurant'];

export default function SignInScreen() {
  const { t } = useTranslation();
  const signIn = useSession((state) => state.signIn);
  const signOut = useSession((state) => state.signOut);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      await signIn(email, password);
      const role = useSession.getState().user?.role;
      if (!role || !KITCHEN_ROLES.includes(role)) {
        await signOut();
        setError(t('signIn.invalidRole'));
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t('signIn.invalidRole'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.card}>
        <Text style={styles.title}>{t('signIn.title')}</Text>
        <Text style={styles.subtitle}>{t('signIn.subtitle')}</Text>

        <Text style={styles.label}>{t('signIn.email')}</Text>
        <TextInput
          value={email}
          onChangeText={setEmail}
          autoCapitalize="none"
          keyboardType="email-address"
          autoComplete="email"
          style={styles.input}
          placeholder="cozinha@exemplo.com"
          placeholderTextColor={theme.textMuted}
        />

        <Text style={styles.label}>{t('signIn.password')}</Text>
        <TextInput
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          style={styles.input}
          placeholder="••••••••"
          placeholderTextColor={theme.textMuted}
        />

        {error && <Text style={styles.error}>{error}</Text>}

        <Pressable disabled={busy || !email || !password} onPress={submit} style={[styles.button, (busy || !email || !password) && styles.buttonDisabled]}>
          {busy ? <ActivityIndicator color="#0F172A" /> : <Text style={styles.buttonText}>{t('signIn.submit')}</Text>}
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: theme.bg, alignItems: 'center', justifyContent: 'center', padding: 24 },
  card: { width: '100%', maxWidth: 420, backgroundColor: theme.surface, borderRadius: 16, padding: 24, gap: 8 },
  title: { color: theme.text, fontSize: 26, fontWeight: '800' },
  subtitle: { color: theme.textMuted, marginBottom: 8 },
  label: { color: theme.textMuted, fontSize: 13, marginTop: 8 },
  input: { backgroundColor: theme.bg, color: theme.text, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 10, borderWidth: 1, borderColor: theme.border },
  error: { color: theme.danger, marginTop: 8 },
  button: { backgroundColor: theme.primary, borderRadius: 10, paddingVertical: 12, alignItems: 'center', marginTop: 16 },
  buttonDisabled: { opacity: 0.5 },
  buttonText: { color: '#0F172A', fontWeight: '800', fontSize: 16 },
});
