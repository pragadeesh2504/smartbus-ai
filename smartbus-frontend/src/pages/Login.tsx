import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Shield, Mail, Lock, RefreshCw, School } from 'lucide-react';

export const Login: React.FC = () => {
  const navigate = useNavigate();
  const { login, loginWithGoogle } = useAuth();
  
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('STUDENT');
  const [collegeCode, setCollegeCode] = useState('');

  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const navigateByRole = () => {
    const savedUser = JSON.parse(localStorage.getItem('user') || '{}');
    if (savedUser.role === 'SUPER_ADMIN') {
      navigate('/super-admin');
    } else if (savedUser.role === 'ADMIN') {
      navigate('/admin');
    } else if (savedUser.role === 'DRIVER') {
      navigate('/driver');
    } else {
      navigate('/student');
    }
  };

  const handleGoogleSignIn = async () => {
    setGoogleLoading(true);
    setError('');
    setSuccess('');

    try {
      let codeToUse = collegeCode;
      if (role === 'STUDENT' && !codeToUse.trim()) {
        const enteredCode = window.prompt('Student Google Sign-In:\nEnter your College Code (e.g. CIT):');
        if (!enteredCode || !enteredCode.trim()) {
          setGoogleLoading(false);
          setError('College Code is required for student Google Sign-In.');
          return;
        }
        codeToUse = enteredCode.trim().toUpperCase();
        setCollegeCode(codeToUse);
      }

      const enteredToken = window.prompt(
        `Google Sign-In (${role}):\nEnter your Google ID Token (or mock-google-token:your-email@domain.com for dev):`
      );
      if (!enteredToken) {
        setGoogleLoading(false);
        return;
      }

      await loginWithGoogle(enteredToken.trim(), role, role === 'STUDENT' ? codeToUse : undefined);
      navigateByRole();
    } catch (e: any) {
      setError(e.response?.data?.message || 'Google authentication failed. Please ensure your Google email matches your registered account and selected role.');
    } finally {
      setGoogleLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccess('');

    try {
      await login(email, password, role, role === 'STUDENT' ? collegeCode : undefined);
      navigateByRole();
    } catch (e: any) {
      setError(e.response?.data?.message || 'Authentication failed. Please check credentials and selected role.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-brandBg flex items-center justify-center p-4">
      {/* Background Accent Gradients */}
      <div className="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none z-0">
        <div className="absolute top-[-20%] left-[-10%] w-[50%] h-[60%] rounded-full bg-brandBlue/5 blur-[120px]"></div>
        <div className="absolute bottom-[-10%] right-[-10%] w-[45%] h-[55%] rounded-full bg-brandTeal/5 blur-[120px]"></div>
      </div>

      <div className="w-full max-w-md bg-white border border-brandBorder p-8 rounded-3xl shadow-xl relative z-10">
        {/* Top accent line */}
        <div className="absolute top-0 left-0 right-0 h-1.5 bg-gradient-to-r from-brandNavy via-brandBlue to-brandTeal rounded-t-3xl"></div>

        <div className="text-center mb-6 mt-2">
          <div className="inline-flex p-3 bg-brandBlue/5 border border-brandBlue/10 rounded-2xl mb-3 text-brandBlue">
            <Shield className="w-7 h-7" />
          </div>
          <h2 className="text-2xl font-bold tracking-tight text-brandNavy">SmartBus AI</h2>
          <p className="text-sm text-brandTextSecondary mt-1">Smart College Bus Tracking & Management</p>
        </div>

        {error && (
          <div className="mb-4 p-3 bg-brandRed/10 border border-brandRed/20 text-brandRed rounded-xl text-xs text-center font-medium">
            {error}
          </div>
        )}

        {success && (
          <div className="mb-4 p-3 bg-brandGreen/10 border border-brandGreen/20 text-brandGreen rounded-xl text-xs text-center font-medium">
            {success}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Role selector on login */}
          <div>
            <label className="block text-xs font-semibold text-brandTextPrimary mb-1">Select Role</label>
            <select 
              value={role} 
              onChange={e => setRole(e.target.value)} 
              className="w-full px-3 py-2.5 border border-brandBorder bg-white rounded-xl text-sm font-medium text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all"
            >
              <option value="STUDENT">Student</option>
              <option value="DRIVER">Driver</option>
              <option value="ADMIN">Admin</option>
            </select>
          </div>

          {role === 'STUDENT' && (
            <div>
              <label className="block text-xs font-semibold text-brandTextPrimary mb-1 flex items-center gap-1">
                <School className="w-3.5 h-3.5 text-brandBlue" />
                College Code
              </label>
              <div className="relative">
                <input 
                  type="text" 
                  value={collegeCode} 
                  onChange={e => setCollegeCode(e.target.value.toUpperCase())} 
                  placeholder="e.g. CIT" 
                  required 
                  className="w-full px-3 py-2.5 border border-brandBorder rounded-xl text-sm font-mono uppercase text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all bg-brandBg/30" 
                />
              </div>
              <p className="text-[10px] text-brandTextSecondary mt-0.5">Enter your institution's college code (e.g. CIT).</p>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-brandTextPrimary mb-1">
              {role === 'STUDENT' ? 'College Email Address' : role === 'DRIVER' ? 'Driver Registered Email' : 'Admin Email Address'}
            </label>
            <div className="relative">
              <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-brandTextSecondary"><Mail className="w-4 h-4"/></span>
              <input 
                type="email" 
                value={email} 
                onChange={e => setEmail(e.target.value)} 
                placeholder={role === 'STUDENT' ? 'student@college.edu' : role === 'DRIVER' ? 'driver@gmail.com' : 'admin@gmail.com'}
                required 
                className="w-full pl-9 pr-3 py-2.5 border border-brandBorder rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all bg-brandBg/30" 
              />
            </div>
          </div>

          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="block text-xs font-semibold text-brandTextPrimary">Password</label>
              <Link 
                to="/forgot-password" 
                className="text-[11px] text-brandBlue hover:text-brandBlue/80 font-medium transition-colors"
              >
                Forgot Password?
              </Link>
            </div>
            <div className="relative">
              <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-brandTextSecondary"><Lock className="w-4 h-4"/></span>
              <input 
                type="password" 
                value={password} 
                onChange={e => setPassword(e.target.value)} 
                required 
                className="w-full pl-9 pr-3 py-2.5 border border-brandBorder rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all bg-brandBg/30" 
              />
            </div>
          </div>

          <button 
            type="submit" 
            disabled={loading} 
            className="w-full h-11 bg-brandBlue hover:bg-brandBlue/90 text-white font-semibold rounded-xl text-sm transition-all shadow-md flex items-center justify-center gap-2"
          >
            {loading ? (
              <RefreshCw className="w-4 h-4 animate-spin" />
            ) : (
              'Authenticate Securely'
            )}
          </button>
        </form>

        <div className="mt-4">
          <div className="relative flex py-2 items-center">
            <div className="flex-grow border-t border-brandBorder"></div>
            <span className="flex-shrink mx-3 text-xs text-brandTextSecondary font-medium">Or continue with</span>
            <div className="flex-grow border-t border-brandBorder"></div>
          </div>

          <button
            type="button"
            onClick={handleGoogleSignIn}
            disabled={googleLoading}
            className="w-full h-11 mt-2 bg-white hover:bg-brandBg border border-brandBorder text-brandTextPrimary font-semibold rounded-xl text-sm transition-all shadow-sm flex items-center justify-center gap-2.5"
          >
            {googleLoading ? (
              <RefreshCw className="w-4 h-4 animate-spin text-brandBlue" />
            ) : (
              <>
                <svg className="w-4 h-4" viewBox="0 0 24 24">
                  <path
                    fill="#4285F4"
                    d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                  />
                  <path
                    fill="#34A853"
                    d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                  />
                  <path
                    fill="#FBBC05"
                    d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                  />
                  <path
                    fill="#EA4335"
                    d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                  />
                </svg>
                <span>Continue with Google</span>
              </>
            )}
          </button>
        </div>

        <div className="mt-5 space-y-2 text-center text-xs">
          {role === 'ADMIN' ? (
            <div className="pt-2 border-t border-brandBorder/50">
              <Link
                to="/register/college"
                className="inline-flex items-center justify-center gap-1.5 text-xs text-brandBlue hover:text-brandBlue/80 font-semibold transition-colors"
              >
                <School className="w-3.5 h-3.5" />
                Register New College & Admin Account
              </Link>
            </div>
          ) : role === 'STUDENT' ? (
            <div className="pt-2 border-t border-brandBorder/50">
              <Link
                to="/register"
                className="inline-flex items-center justify-center gap-1.5 text-xs text-brandBlue hover:text-brandBlue/80 font-semibold transition-colors"
              >
                <School className="w-3.5 h-3.5" />
                Register as Student with College Code
              </Link>
            </div>
          ) : (
            <div className="pt-2 border-t border-brandBorder/50">
              <p className="text-[11px] text-brandTextSecondary">
                Driver accounts are provisioned by your College Transport Administrator.
              </p>
            </div>
          )}
        </div>

        <div className="mt-4 pt-3 border-t border-brandBorder/60 text-center">
          <Link
            to="/super-admin/login"
            className="inline-flex items-center justify-center gap-1.5 text-[11px] text-slate-500 hover:text-indigo-600 font-medium transition-colors"
          >
            <Shield className="w-3 h-3 text-slate-400" />
            <span>Platform Super Admin? Sign in to Platform Portal &rarr;</span>
          </Link>
        </div>
      </div>
    </div>
  );
};