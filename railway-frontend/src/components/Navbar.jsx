import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';
import { Train, Shield, LogOut, Menu, X } from 'lucide-react';
import { useState } from 'react';

export default function Navbar() {
  const { isAuthenticated, isAdmin, user, logout } = useAuth();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  const navLinks = [
    { to: '/', label: 'Search Trains' },
    { to: '/pnr', label: 'PNR Status' },
  ];

  if (isAuthenticated) {
    navLinks.push({ to: '/my-bookings', label: 'My Bookings' });
  }

  return (
    <nav className="bg-primary text-primary-foreground shadow-lg sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4">
        <div className="flex items-center justify-between h-16">
          <Link to="/" className="flex items-center gap-2 font-bold text-xl no-underline text-primary-foreground">
            <Train className="h-6 w-6" />
            RailBook
          </Link>

          <div className="hidden md:flex items-center gap-1">
            {navLinks.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                className="px-3 py-2 rounded-md text-sm font-medium hover:bg-white/10 transition-colors no-underline text-primary-foreground"
              >
                {link.label}
              </Link>
            ))}
            {isAdmin && (
              <Link
                to="/admin"
                className="px-3 py-2 rounded-md text-sm font-medium hover:bg-white/10 transition-colors no-underline text-primary-foreground flex items-center gap-1"
              >
                <Shield className="h-4 w-4" />
                Admin
              </Link>
            )}
          </div>

          <div className="hidden md:flex items-center gap-2">
            {isAuthenticated ? (
              <>
                <span className="text-sm opacity-80">{user?.email}</span>
                <Button variant="secondary" size="sm" onClick={handleLogout}>
                  <LogOut className="h-4 w-4 mr-1" />
                  Logout
                </Button>
              </>
            ) : (
              <>
                <Button variant="ghost" size="sm" onClick={() => navigate('/login')} className="text-primary-foreground hover:bg-white/10">
                  Login
                </Button>
                <Button variant="secondary" size="sm" onClick={() => navigate('/register')}>
                  Register
                </Button>
              </>
            )}
          </div>

          <button className="md:hidden p-2" onClick={() => setMobileOpen(!mobileOpen)}>
            {mobileOpen ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
          </button>
        </div>

        {mobileOpen && (
          <div className="md:hidden pb-4 space-y-1">
            {navLinks.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                className="block px-3 py-2 rounded-md text-sm font-medium hover:bg-white/10 no-underline text-primary-foreground"
                onClick={() => setMobileOpen(false)}
              >
                {link.label}
              </Link>
            ))}
            {isAdmin && (
              <Link
                to="/admin"
                className="block px-3 py-2 rounded-md text-sm font-medium hover:bg-white/10 no-underline text-primary-foreground"
                onClick={() => setMobileOpen(false)}
              >
                Admin Panel
              </Link>
            )}
            <div className="pt-2 border-t border-white/20 space-y-1">
              {isAuthenticated ? (
                <Button variant="secondary" size="sm" className="w-full" onClick={() => { handleLogout(); setMobileOpen(false); }}>
                  Logout
                </Button>
              ) : (
                <>
                  <Button variant="ghost" size="sm" className="w-full text-primary-foreground" onClick={() => { navigate('/login'); setMobileOpen(false); }}>
                    Login
                  </Button>
                  <Button variant="secondary" size="sm" className="w-full" onClick={() => { navigate('/register'); setMobileOpen(false); }}>
                    Register
                  </Button>
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </nav>
  );
}
