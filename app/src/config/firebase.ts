import { initializeApp } from 'firebase/app';
import { getDatabase, type Database } from 'firebase/database';
import { getAuth, type Auth } from 'firebase/auth';

// Firebase is optional: it backs Google login and the personal preset library.
// Dataset generation goes to the ERENO API and works without it. It is on only
// when its config is present and VITE_AUTH_ENABLED is not "false".
const env = import.meta.env;

export const firebaseEnabled =
  env.VITE_AUTH_ENABLED !== 'false' && Boolean(env.VITE_APIKEY) && Boolean(env.VITE_DATABASEURL);

let db: Database | null = null;
let auth: Auth | null = null;

if (firebaseEnabled) {
  const app = initializeApp({
    apiKey: env.VITE_APIKEY,
    authDomain: env.VITE_AUTHDOMAIN,
    databaseURL: env.VITE_DATABASEURL,
    projectId: env.VITE_PROJECTID,
    storageBucket: env.VITE_STORAGEBUCKET,
    messagingSenderId: env.VITE_MESSAGINGSENDERID,
    appId: env.VITE_APPID,
    measurementId: env.VITE_MEASUREMENTID,
  });
  db = getDatabase(app);
  auth = getAuth(app);
}

export { db, auth };
