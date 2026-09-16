import React, { createContext, useState, useEffect, useContext } from 'react';
import axios from 'axios';

interface User {
  email: string;
  role: string;
  name: string;
}

interface AuthContextType {
  user: User | null;
  accessToken: string | null;
  loading: boolean;
  login: (email: string, password: string, role?: string) => Promise<void>;
  loginWithGoogle: (idToken: string, role?: string) => Promise<void>;
  logout: () => void;
  register: (data: any) => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    const initializeAuth = async () => {
      const savedToken = localStorage.getItem('accessToken');
      const savedUser = localStorage.getItem('user');
      const savedRefresh = localStorage.getItem('refreshToken');

      if (savedToken && savedUser) {
        setAccessToken(savedToken);
        setUser(JSON.parse(savedUser));
        axios.defaults.headers.common['Authorization'] = `Bearer ${savedToken}`;
      } else if (savedRefresh) {
        // Attempt to refresh
        try {
          const res = await axios.post('/api/auth/refresh', { refreshToken: savedRefresh });
          const { accessToken, email, role, name } = res.data;
          setAccessToken(accessToken);
          const newUser = { email, role, name };
          setUser(newUser);
          localStorage.setItem('accessToken', accessToken);
          localStorage.setItem('user', JSON.stringify(newUser));
          axios.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
        } catch (e) {
          localStorage.removeItem('refreshToken');
        }
      }
      setLoading(false);
    };

    initializeAuth();

    const handleUnauthorized = () => {
      setUser(null);
      setAccessToken(null);
    };
    window.addEventListener('smartbus:unauthorized', handleUnauthorized);
    return () => {
      window.removeEventListener('smartbus:unauthorized', handleUnauthorized);
    };
  }, []);

  const login = async (email: string, password: string, role?: string) => {
    const res = await axios.post('/api/auth/login', { email, password, role });
    const { accessToken, refreshToken, role: returnedRole, name } = res.data;

    setAccessToken(accessToken);
    const newUser = { email, role: returnedRole, name };
    setUser(newUser);
    
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    localStorage.setItem('user', JSON.stringify(newUser));
    axios.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
  };

  const loginWithGoogle = async (idToken: string, role?: string) => {
    const res = await axios.post('/api/auth/google', { idToken, role });
    const { accessToken, refreshToken, role: returnedRole, name, email } = res.data;

    setAccessToken(accessToken);
    const newUser = { email, role: returnedRole, name };
    setUser(newUser);

    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    localStorage.setItem('user', JSON.stringify(newUser));
    axios.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
  };

  const logout = () => {
    setAccessToken(null);
    setUser(null);
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    delete axios.defaults.headers.common['Authorization'];
  };

  const register = async (data: any) => {
    await axios.post('/api/auth/register', data);
  };

  return (
    <AuthContext.Provider value={{ user, accessToken, loading, login, loginWithGoogle, logout, register }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
