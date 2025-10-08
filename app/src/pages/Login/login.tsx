import { auth } from "../../config/firebase";
import { GoogleAuthProvider, signInWithPopup } from "firebase/auth";

const provider = new GoogleAuthProvider();

export default function login() {

    const handleGoogleLogin = async () => {
    try {
        await signInWithPopup(auth, provider);
        window.location.href = "/iedconfig"; 
    } catch (error) {
        console.error(error);
        alert("Login failed. Please try again.");
    }
  };

    return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-[#0b01b7] to-[#45c0fe]">
      <div className="bg-white rounded-2xl shadow-lg p-8 flex flex-col items-center w-full max-w-sm">
        <h2 className="text-3xl font-bold mb-6 text-[#45c0fe]">ERENO-UI</h2>
        <p className="mb-8 text-gray-600 text-center">
          Welcome to ERENO-UI! Please log in with your Google account to continue.
        </p>
        <button
          onClick={handleGoogleLogin}
          className="flex items-center gap-3 bg-[#0b01b7] hover:bg-[#45c0fe] text-white font-semibold px-6 py-3 rounded-xl transition"
        >
          <img
            src="https://www.svgrepo.com/show/475656/google-color.svg"
            alt="Google"
            className="w-6 h-6"
          />
            Sign in with Google
        </button>
      </div>
    </div>
  );
};