import React, { createContext, useEffect, useState } from 'react';
import { auth, db, firebaseEnabled } from '../config/firebase';
import { onAuthStateChanged, signOut as firebaseSignOut } from 'firebase/auth';
import type { User as FirebaseUser } from 'firebase/auth';
import { ref, onValue, set } from 'firebase/database';

type AppUserBase = {
  uid: string;
  name: string;
  email?: string | null;
  avatarUrl?: string | null;
  course?: string | null;
};

type AppUser = AppUserBase | null;

type AuthContextValue = {
  user: AppUser;
  loading: boolean;
  /** False when Firebase is off: the app runs locally without login. */
  authEnabled: boolean;
  updateProfile: (data: Partial<Omit<AppUserBase, 'uid'>>) => Promise<void>;
  signOut: () => Promise<void>;
};

const LOCAL_USER: AppUserBase = { uid: 'local', name: 'Local user' };

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AppUser>(() => {
    if (!firebaseEnabled) return LOCAL_USER;
    try {
      const raw = localStorage.getItem('app_user');
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  });
  const [loading, setLoading] = useState(firebaseEnabled);

  useEffect(() => {
    if (!firebaseEnabled || !auth || !db) return;
    const database = db;
    const unsub = onAuthStateChanged(auth, (firebaseUser: FirebaseUser | null) => {
      if (!firebaseUser) {
        setUser(null);
        try { localStorage.removeItem('app_user'); } catch { /* storage unavailable */ }
        setLoading(false);
        return;
      }

      const userRef = ref(database, `users/${firebaseUser.uid}`);
      onValue(userRef, (snapshot) => {
        const data = snapshot.val();
        const appUser: AppUser = {
          uid: firebaseUser.uid,
          name: data?.name || firebaseUser.displayName || '',
          email: firebaseUser.email,
          avatarUrl: data?.avatarUrl || firebaseUser.photoURL || null,
          course: data?.course || null,
        };
        setUser(appUser);
        try { localStorage.setItem('app_user', JSON.stringify(appUser)); } catch { /* storage unavailable */ }
        setLoading(false);
      }, () => {
        setUser({
          uid: firebaseUser.uid,
          name: firebaseUser.displayName || '',
          email: firebaseUser.email,
          avatarUrl: firebaseUser.photoURL || null,
          course: null,
        });
        setLoading(false);
      });
    });

    return () => unsub();
  }, []);

  const updateProfile = async (data: Partial<Omit<AppUserBase, 'uid'>>) => {
    if (!user) throw new Error('Not authenticated');
    if (!db) return;
    const stored = {
      name: data.name ?? user.name,
      avatarUrl: data.avatarUrl ?? user.avatarUrl ?? null,
      course: data.course ?? user.course ?? null,
      email: data.email ?? user.email ?? null,
    };
    await set(ref(db, `users/${user.uid}`), stored);
    setUser({ uid: user.uid, ...stored });
  };

  const signOut = async () => {
    if (!auth) return;
    await firebaseSignOut(auth);
    setUser(null);
    try { localStorage.removeItem('app_user'); } catch { /* storage unavailable */ }
  };

  return (
    <AuthContext.Provider value={{ user, loading, authEnabled: firebaseEnabled, updateProfile, signOut }}>
      {children}
    </AuthContext.Provider>
  );
};

export default AuthContext;
