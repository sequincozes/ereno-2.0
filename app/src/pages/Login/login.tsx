import { useContext, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { GoogleAuthProvider, signInWithPopup } from 'firebase/auth';
import { auth } from '../../config/firebase';
import AuthContext from '../../context/authContext';

export default function Login() {
  const ctx = useContext(AuthContext);
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  // Without Firebase (local mode) or once signed in, go straight to the wizard.
  if (!ctx?.authEnabled || ctx.user) return <Navigate to="/ied" replace />;

  const handleGoogleLogin = async () => {
    if (!auth) return;
    try {
      await signInWithPopup(auth, new GoogleAuthProvider());
      navigate('/ied');
    } catch (e) {
      console.error(e);
      setError('Login failed. Please try again.');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-[#0b01b7] to-[#45c0fe] px-4">
      <div className="bg-white rounded-2xl shadow-lg p-8 flex flex-col items-center w-full max-w-sm">
        <h2 className="text-3xl font-bold mb-6 text-[#45c0fe]">ERENO-UI</h2>
        <p className="mb-8 text-gray-600 text-center">
          Welcome to ERENO-UI! Please log in with your Google account to continue.
        </p>
        <button
          onClick={handleGoogleLogin}
          className="flex items-center gap-3 bg-[#0b01b7] hover:bg-[#45c0fe] text-white font-semibold px-6 py-3 rounded-xl transition"
        >
          <img src="https://www.svgrepo.com/show/475656/google-color.svg" alt="" className="w-6 h-6" />
          Sign in with Google
        </button>
        {error && <p className="mt-4 text-sm text-red-600">{error}</p>}
      </div>
    </div>
  );
}
