import { useContext } from 'react';
import { NavLink } from 'react-router-dom';
import { Shield as IconShield, LogOut as IconLogOut } from 'lucide-react';
import AuthContext from '../../context/authContext';

const navClass = ({ isActive }: { isActive: boolean }) =>
  `px-3 py-1.5 rounded-md text-sm font-medium ${isActive ? 'bg-white text-[#0051A2] shadow-sm' : 'text-[#0051A2] hover:bg-white/60'}`;

export default function Header() {
  const auth = useContext(AuthContext);
  return (
    <header className="bg-[#ECF0FF] px-4 sm:px-6 pt-6 pb-2">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex flex-col items-start">
          <span className="flex items-center gap-2 text-[#0051A2] font-bold py-2 rounded-md text-lg">
            <IconShield className="w-5 h-5" />
            ERENO-UI
          </span>
          <h2 className="text-[#007AF0] font-bold mt-1">
            Dataset Generation System with cyberattacks in GOOSE protocol
          </h2>
        </div>
        <nav className="flex items-center gap-2">
          <NavLink to="/ied" className={navClass}>New dataset</NavLink>
          <NavLink to="/runs" className={navClass}>Runs</NavLink>
          {auth?.authEnabled && auth.user && (
            <button
              type="button"
              onClick={() => auth.signOut()}
              className="flex items-center gap-1 px-3 py-1.5 rounded-md text-sm text-gray-600 hover:bg-white/60"
              title={auth.user.email ?? auth.user.name}
            >
              <IconLogOut className="w-4 h-4" /> Sign out
            </button>
          )}
        </nav>
      </div>
    </header>
  );
}
