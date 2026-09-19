import React, { createContext, useState, useEffect, useContext } from 'react';
import axios from 'axios';

export interface User {
  email: string;
  role: string;
  name: string;
  collegeId?: string | null;
  collegeName?: string | null;
  collegeCode?: string | null;
}

interface AuthContextType {
  user: User | null;
  accessToken: string | null;
  loading: boolean;
  login: (email: string, password: string, role?: string, collegeCode?: string) => Promise<void>;
  loginWithGoogle: (idToken: string, role?: string, collegeCode?: string) => Promise<void>;
  logout: () => void;
  register: (data: any) => Promise<void>;
  updateUser: (partial: Partial<User>) => void;
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
          const { accessToken, email, role, name, collegeId, collegeName, collegeCode } = res.data;
          setAccessToken(accessToken);
          const newUser: User = { email, role, name, collegeId, collegeName, collegeCode };
          setUser(newUser);
          localStorage.setItem('accessToken', accessToken);
          localStorage.setItem('user', JSON.stringify(newUser));
          if (collegeId) localStorage.setItem('collegeId', collegeId);
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

  const login = async (email: string, password: string, role?: string, collegeCode?: string) => {
    const res = await axios.post('/api/auth/login', {
      email,
      password,
      role,
      collegeCode: collegeCode ? collegeCode.trim().toUpperCase() : undefined
    });
    const { accessToken, refreshToken, role: returnedRole, name, collegeId, collegeName, collegeCode: returnedCode } = res.data;

    setAccessToken(accessToken);
    const newUser: User = { email, role: returnedRole, name, collegeId, collegeName, collegeCode: returnedCode };
    setUser(newUser);
    
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    localStorage.setItem('user', JSON.stringify(newUser));
    if (collegeId) {
      localStorage.setItem('collegeId', collegeId);
    } else {
      localStorage.removeItem('collegeId');
    }
    axios.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
  };

  const loginWithGoogle = async (idToken: string, role?: string, collegeCode?: string) => {
    const res = await axios.post('/api/auth/google', {
      idToken,
      role,
      collegeCode: collegeCode ? collegeCode.trim().toUpperCase() : undefined
    });
    const { accessToken, refreshToken, role: returnedRole, name, email, collegeId, collegeName, collegeCode: returnedCode } = res.data;

    setAccessToken(accessToken);
    const newUser: User = { email, role: returnedRole, name, collegeId, collegeName, collegeCode: returnedCode };
    setUser(newUser);

    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    localStorage.setItem('user', JSON.stringify(newUser));
    if (collegeId) {
      localStorage.setItem('collegeId', collegeId);
    } else {
      localStorage.removeItem('collegeId');
    }
    axios.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
  };

  const logout = () => {
    setAccessToken(null);
    setUser(null);
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    localStorage.removeItem('collegeId');
    delete axios.defaults.headers.common['Authorization'];
  };

  const register = async (data: any) => {
    await axios.post('/api/auth/register', data);
  };

  const updateUser = (partial: Partial<User>) => {
    setUser((prev) => {
      if (!prev) return null;
      const updated = { ...prev, ...partial };
      localStorage.setItem('user', JSON.stringify(updated));
      return updated;
    });
  };

  return (
    <AuthContext.Provider value={{ user, accessToken, loading, login, loginWithGoogle, logout, register, updateUser }}>
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
