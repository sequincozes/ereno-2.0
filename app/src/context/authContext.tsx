import React, { createContext, useEffect, useState } from 'react';
import { auth, db } from '../config/firebase';
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
  updateProfile: (data: Partial<Omit<AppUserBase, 'uid'>>) => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AppUser>(() => {
    try {
      const raw = localStorage.getItem('app_user');
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, (firebaseUser: FirebaseUser | null) => {
      if (!firebaseUser) {
        setUser(null);
        localStorage.removeItem('app_user');
        setLoading(false);
        return;
      }

      const userRef = ref(db, `users/${firebaseUser.uid}`);
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
        localStorage.setItem('app_user', JSON.stringify(appUser));
        setLoading(false);
      }, () => {
        const appUser: AppUser = {
          uid: firebaseUser.uid,
          name: firebaseUser.displayName || '',
          email: firebaseUser.email,
          avatarUrl: firebaseUser.photoURL || null,
          course: null,
        };
        setUser(appUser);
        setLoading(false);
      });
    });

    return () => unsub();
  }, []);

  const updateProfile = async (data: Partial<Omit<AppUserBase, 'uid'>>) => {
    if (!user) throw new Error('Not authenticated');
    const userRef = ref(db, `users/${user.uid}`);
    const newData = {
      name: data.name ?? user.name,
      avatarUrl: data.avatarUrl ?? user.avatarUrl ?? null,
      course: data.course ?? user.course ?? null,
      email: data.email ?? user.email ?? null,
    } as Partial<AppUserBase>;
    await set(userRef, newData);
    const updated: AppUser = {
      uid: user.uid,
      name: (newData as AppUserBase).name,
      email: (newData as AppUserBase).email,
      avatarUrl: (newData as AppUserBase).avatarUrl,
      course: (newData as AppUserBase).course,
    } as AppUser;
    setUser(updated);
  };

  const signOut = async () => {
    await firebaseSignOut(auth);
    setUser(null);
    localStorage.removeItem('app_user');
  };

  return (
    <AuthContext.Provider value={{ user, loading, updateProfile, signOut }}>
      {children}
    </AuthContext.Provider>
  );
};

export default AuthContext;
